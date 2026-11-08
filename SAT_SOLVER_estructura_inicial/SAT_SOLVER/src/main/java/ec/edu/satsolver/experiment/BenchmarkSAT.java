package ec.edu.satsolver.experiment;

import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.solver.DPLLSolver;
import ec.edu.satsolver.verification.AssignmentVerifier;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Persona 5: experimentos reproducibles para el SAT Solver del equipo.
 * NO modifica las clases existentes y NO necesita iniciar el servidor HTTP.
 * Ejecutar desde la raiz del proyecto SAT_SOLVER.
 * Requiere JDK 17+ (como el proyecto original).
 */
public final class BenchmarkSAT {

    static {
        // Debe fijarse antes de inicializar java.awt.Color/Font.
        System.setProperty("java.awt.headless", "true");
    }

    private static final int[] TAMANOS = {6, 9, 12, 15, 18};
    private static final int CALENTAMIENTO = 12;
    private static final int REPETICIONES = 30;
    private static final long SEMILLA = 20261108L;

    private static final Color AZUL = new Color(37, 99, 235);
    private static final Color NARANJA = new Color(234, 88, 12);
    private static final int ANCHO = 1000;
    private static final int ALTO = 600;
    private static final int IZQUIERDA = 110;
    private static final int DERECHA = 65;
    private static final int ARRIBA = 95;
    private static final int ABAJO = 110;

    // Si la JVM lo permite, mide bytes ASIGNADOS en el hilo actual.
    // No equivalen a la memoria RAM maxima utilizada (pico de heap).
    private static final com.sun.management.ThreadMXBean BEAN = activarMedicionMemoria();
    private static final String METODO_MEMORIA = BEAN == null
            ? "HEAP_DELTA_ESTIMADO" : "BYTES_ASIGNADOS_HILO";

    private BenchmarkSAT() { }

    private record Medicion(long nanosegundos, long memoriaBytes) { }

    private record Resumen(String tipo, int variables, int clausulas, int literales,
                           String resultado, double dpllMs, double dpllMemoria,
                           Double verificadorMs, Double verificadorMemoria) { }

    public static void main(String[] args) {
        // Permite guardar PNG incluso si Java se ejecuta sin escritorio grafico.
        System.setProperty("java.awt.headless", "true");
        try {
            ejecutarExperimentos();
        } catch (Exception e) {
            System.err.println("ERROR en la experimentacion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void ejecutarExperimentos() throws IOException {
        // Un directorio distinto por ejecucion: nunca se pisan resultados anteriores.
        String identificador = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path directorio = Paths.get("experiment", "resultados", "benchmark_" + identificador);
        Files.createDirectories(directorio);

        List<Resumen> resumenes = new ArrayList<>();
        Path rutaDetalle = directorio.resolve("resultados.csv");
        Path rutaResumen = directorio.resolve("resumen.csv");

        System.out.println("=== EXPERIMENTACION SAT - PERSONA 5 ===");
        System.out.println("Memoria: " + METODO_MEMORIA);
        System.out.println("Calentamiento: " + CALENTAMIENTO
                + " | Repeticiones: " + REPETICIONES);

        try (BufferedWriter detalle = Files.newBufferedWriter(rutaDetalle,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            detalle.write("tipo,variables,clausulas,literales,repeticion,resultado,"
                    + "tiempo_dpll_ns,memoria_dpll_bytes,tiempo_verificador_ns,"
                    + "memoria_verificador_bytes,metodo_memoria");
            detalle.newLine();

            for (int n : TAMANOS) {
                int m = 4 * n;
                // Una instancia fija por tipo y tamano, con semilla reproducible.
                FormulaCNF sat = generarSAT(n, m, SEMILLA + n);
                FormulaCNF unsat = generarUNSAT(n, m, SEMILLA + 10_000 + n);
                resumenes.add(medirConfiguracion("SAT", sat, detalle));
                resumenes.add(medirConfiguracion("UNSAT", unsat, detalle));
            }
        }

        try (BufferedWriter resumen = Files.newBufferedWriter(rutaResumen,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            resumen.write("tipo,variables,clausulas,literales,resultado,"
                    + "mediana_dpll_ms,mediana_memoria_dpll_bytes,"
                    + "mediana_verificador_ms,mediana_memoria_verificador_bytes,metodo_memoria");
            resumen.newLine();
            for (Resumen r : resumenes) {
                resumen.write(String.format(Locale.US,
                        "%s,%d,%d,%d,%s,%.6f,%.0f,%s,%s,%s",
                        r.tipo(), r.variables(), r.clausulas(), r.literales(), r.resultado(),
                        r.dpllMs(), r.dpllMemoria(),
                        numeroOpcional(r.verificadorMs(), 6),
                        numeroOpcional(r.verificadorMemoria(), 0), METODO_MEMORIA));
                resumen.newLine();
            }
        }

        escribirEntorno(directorio.resolve("entorno.txt"));
        graficaCrecimiento(resumenes, directorio.resolve("grafico_tiempos.png"));
        graficaComparacion(resumenes, directorio.resolve("grafico_comparacion.png"));

        System.out.println("\nArchivos creados en: " + directorio.toAbsolutePath());
        System.out.println("  resultados.csv, resumen.csv, entorno.txt");
        System.out.println("  grafico_tiempos.png, grafico_comparacion.png");
    }

    private static Resumen medirConfiguracion(String tipo, FormulaCNF formula,
                                               BufferedWriter detalle) throws IOException {
        DPLLSolver solver = new DPLLSolver();
        AssignmentVerifier verificador = new AssignmentVerifier();
        boolean esSAT = "SAT".equals(tipo);

        // Comprobacion funcional FUERA de la region cronometrada.
        Optional<Map<String, Boolean>> prueba = solver.solve(formula);
        if (prueba.isPresent() != esSAT) {
            throw new IllegalStateException("DPLL dio una respuesta inesperada: "
                    + tipo + " con " + formula.variables().size() + " variables");
        }
        Map<String, Boolean> asignacion = prueba.orElse(null);
        if (esSAT && !verificador.verify(formula, asignacion)) {
            throw new IllegalStateException("La asignacion del solver no satisface la CNF");
        }

        // El calentamiento permite estabilizar la compilacion JIT.
        for (int i = 0; i < CALENTAMIENTO; i++) {
            solver.solve(formula);
            if (esSAT) verificador.verify(formula, asignacion);
        }

        List<Long> tiemposDpll = new ArrayList<>();
        List<Long> memoriaDpll = new ArrayList<>();
        List<Long> tiemposVerificador = new ArrayList<>();
        List<Long> memoriaVerificador = new ArrayList<>();

        for (int repeticion = 1; repeticion <= REPETICIONES; repeticion++) {
            // Se mide solo solve(), sin incluir parser, interfaz ni escritura CSV.
            long memAntes = leerMemoria();
            long comienzo = System.nanoTime();
            Optional<Map<String, Boolean>> resultado = solver.solve(formula);
            long transcurrido = System.nanoTime() - comienzo;
            long memDespues = leerMemoria();
            Medicion dpll = new Medicion(transcurrido, Math.max(0L, memDespues - memAntes));
            if (resultado.isPresent() != esSAT) {
                throw new IllegalStateException("Resultado DPLL inconsistente en la repeticion "
                        + repeticion + " del caso " + tipo);
            }
            tiemposDpll.add(dpll.nanosegundos());
            memoriaDpll.add(dpll.memoriaBytes());

            Medicion verificacion = null;
            if (esSAT) {
                // Se reutiliza una asignacion ya encontrada antes de cronometrar:
                // verificar no incluye el costo de resolver SAT.
                memAntes = leerMemoria();
                comienzo = System.nanoTime();
                boolean valida = verificador.verify(formula, asignacion);
                transcurrido = System.nanoTime() - comienzo;
                memDespues = leerMemoria();
                if (!valida) throw new IllegalStateException("Error en el verificador");
                verificacion = new Medicion(transcurrido,
                        Math.max(0L, memDespues - memAntes));
                tiemposVerificador.add(verificacion.nanosegundos());
                memoriaVerificador.add(verificacion.memoriaBytes());
            }

            detalle.write(String.format(Locale.US,
                    "%s,%d,%d,%d,%d,%s,%d,%d,%s,%s,%s",
                    tipo, formula.variables().size(), formula.numeroClausulas(),
                    formula.numeroLiterales(), repeticion, tipo,
                    dpll.nanosegundos(), dpll.memoriaBytes(),
                    verificacion == null ? "" : String.valueOf(verificacion.nanosegundos()),
                    verificacion == null ? "" : String.valueOf(verificacion.memoriaBytes()),
                    METODO_MEMORIA));
            detalle.newLine();
        }

        double dpllMs = mediana(tiemposDpll) / 1_000_000.0;
        Double verificadorMs = esSAT ? mediana(tiemposVerificador) / 1_000_000.0 : null;
        System.out.printf(Locale.US,
                "%5s | n=%2d | m=%2d | DPLL=%.6f ms | Verificador=%s ms%n",
                tipo, formula.variables().size(), formula.numeroClausulas(), dpllMs,
                esSAT ? numeroOpcional(verificadorMs, 6) : "N/A");

        return new Resumen(tipo, formula.variables().size(),
                formula.numeroClausulas(), formula.numeroLiterales(), tipo,
                dpllMs, mediana(memoriaDpll), verificadorMs,
                esSAT ? mediana(memoriaVerificador) : null);
    }

    /** Genera una 3-CNF SAT: cada clausula respeta una asignacion plantada. */
    private static FormulaCNF generarSAT(int n, int m, long semilla) {
        if (n < 3 || m < n) throw new IllegalArgumentException("Parametros invalidos");
        Random azar = new Random(semilla);
        boolean[] testigo = new boolean[n + 1];
        for (int i = 1; i <= n; i++) testigo[i] = azar.nextBoolean();
        List<int[]> clausulas = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            int a = i < n ? i + 1 : azar.nextInt(n) + 1;
            int b;
            do { b = azar.nextInt(n) + 1; } while (b == a);
            int c;
            do { c = azar.nextInt(n) + 1; } while (c == a || c == b);
            int[] clausula = {
                    azar.nextBoolean() ? a : -a,
                    azar.nextBoolean() ? b : -b,
                    azar.nextBoolean() ? c : -c
            };
            boolean satisfecha = false;
            for (int literal : clausula) {
                boolean valor = testigo[Math.abs(literal)];
                if ((literal > 0 && valor) || (literal < 0 && !valor)) {
                    satisfecha = true;
                    break;
                }
            }
            if (!satisfecha) clausula[0] = -clausula[0];
            clausulas.add(clausula);
        }
        return new FormulaCNF(nombresVariables(n), clausulas);
    }

    /**
     * Genera CNF UNSAT con un nucleo contradictorio de cuatro clausulas:
     * (x1|x2), (x1|!x2), (!x1|x2), (!x1|!x2).
     * Es UNSAT por construccion, pero NO representa un peor caso de DPLL.
     */
    private static FormulaCNF generarUNSAT(int n, int m, long semilla) {
        FormulaCNF base = generarSAT(n, m - 4, semilla);
        List<int[]> clausulas = base.clauses();
        clausulas.add(new int[] {1, 2});
        clausulas.add(new int[] {1, -2});
        clausulas.add(new int[] {-1, 2});
        clausulas.add(new int[] {-1, -2});
        return new FormulaCNF(nombresVariables(n), clausulas);
    }

    private static List<String> nombresVariables(int n) {
        List<String> nombres = new ArrayList<>();
        for (int i = 1; i <= n; i++) nombres.add("x" + i);
        return nombres;
    }

    private static double mediana(List<Long> valores) {
        if (valores.isEmpty()) throw new IllegalArgumentException("Mediciones vacias");
        long[] ordenados = valores.stream().mapToLong(Long::longValue).sorted().toArray();
        int centro = ordenados.length / 2;
        return ordenados.length % 2 == 1
                ? ordenados[centro]
                : ordenados[centro - 1] / 2.0 + ordenados[centro] / 2.0;
    }

    private static String numeroOpcional(Double valor, int decimales) {
        return valor == null ? "" : String.format(Locale.US, "%." + decimales + "f", valor);
    }

    private static com.sun.management.ThreadMXBean activarMedicionMemoria() {
        try {
            java.lang.management.ThreadMXBean gestor = ManagementFactory.getThreadMXBean();
            if (gestor instanceof com.sun.management.ThreadMXBean asignaciones
                    && asignaciones.isThreadAllocatedMemorySupported()) {
                if (!asignaciones.isThreadAllocatedMemoryEnabled()) {
                    asignaciones.setThreadAllocatedMemoryEnabled(true);
                }
                if (asignaciones.getThreadAllocatedBytes(Thread.currentThread().getId()) >= 0) {
                    return asignaciones;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // JVM sin la extension de medicion de asignaciones.
        }
        return null;
    }

    private static long leerMemoria() {
        if (BEAN != null) {
            return BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    private static void escribirEntorno(Path ruta) throws IOException {
        List<String> lineas = Arrays.asList(
                "Proyecto: SAT SOLVER / Persona 5",
                "JVM: " + System.getProperty("java.runtime.version"),
                "Sistema operativo: " + System.getProperty("os.name") + " "
                        + System.getProperty("os.version"),
                "Procesadores logicos: " + Runtime.getRuntime().availableProcessors(),
                "Tamanos de variables: " + Arrays.toString(TAMANOS),
                "Clausulas por instancia: 4 * n",
                "Semilla base: " + SEMILLA,
                "Calentamiento por configuracion: " + CALENTAMIENTO,
                "Repeticiones por configuracion: " + REPETICIONES,
                "Metodo de memoria: " + METODO_MEMORIA,
                "Nota: BYTES_ASIGNADOS_HILO cuenta bytes asignados por el hilo durante cada operacion.",
                "Nota: HEAP_DELTA_ESTIMADO es solo una diferencia aproximada y puede fluctuar con el GC.",
                "Nota: ninguna de estas medidas representa el pico de RAM del proceso.",
                "Las mediciones no incluyen generacion de casos, parser, servidor, red, graficas ni CSV.",
                "UNSAT incluye un nucleo contradictorio pequeno; no representa el peor caso exponencial.",
                "Se usa una instancia fija por tipo y tamano; repetir sobre instancias diversas",
                "es una mejora posible para generalizar los resultados."
        );
        Files.write(ruta, lineas, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static Graphics2D nuevoLienzo(BufferedImage imagen, String titulo, String subtitulo) {
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ANCHO, ALTO);
        g.setColor(new Color(25, 36, 55));
        g.setFont(new Font("SansSerif", Font.BOLD, 23));
        g.drawString(titulo, IZQUIERDA, 42);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString(subtitulo, IZQUIERDA, 67);
        g.setColor(new Color(85, 95, 110));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString("Numero de variables (n)", ANCHO / 2 - 65, ALTO - 36);
        return g;
    }

    private static void graficaCrecimiento(List<Resumen> datos, Path salida) throws IOException {
        List<Resumen> sat = filtrar(datos, "SAT");
        List<Resumen> unsat = filtrar(datos, "UNSAT");
        double max = 0.0;
        for (Resumen r : datos) max = Math.max(max, r.dpllMs());
        max = Math.max(max * 1.15, 0.0001);

        BufferedImage imagen = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nuevoLienzo(imagen, "Tiempo de resolucion DPLL",
                "Instancias SAT y UNSAT - mediana de " + REPETICIONES + " repeticiones");
        int altoUtil = ALTO - ARRIBA - ABAJO;
        int anchoUtil = ANCHO - IZQUIERDA - DERECHA;
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        for (int i = 0; i <= 5; i++) {
            int y = ARRIBA + altoUtil - (i * altoUtil / 5);
            g.setColor(new Color(225, 230, 235));
            g.drawLine(IZQUIERDA, y, ANCHO - DERECHA, y);
            g.setColor(new Color(75, 85, 99));
            g.drawString(String.format(Locale.US, "%.4f", max * i / 5.0), 30, y + 5);
        }
        g.setColor(new Color(75, 85, 99));
        g.drawString("Tiempo (ms)", 15, 86);
        for (int i = 0; i < TAMANOS.length; i++) {
            int x = IZQUIERDA + i * anchoUtil / (TAMANOS.length - 1);
            g.drawString(String.valueOf(TAMANOS[i]), x - 8, ALTO - ABAJO + 22);
        }
        trazarSerie(g, sat, AZUL, max);
        trazarSerie(g, unsat, NARANJA, max);
        leyenda(g, AZUL, "DPLL SAT", ANCHO - 280, 45);
        leyenda(g, NARANJA, "DPLL UNSAT", ANCHO - 160, 45);
        g.dispose();
        ImageIO.write(imagen, "png", salida.toFile());
    }

    private static void trazarSerie(Graphics2D g, List<Resumen> datos,
                                    Color color, double max) {
        int altoUtil = ALTO - ARRIBA - ABAJO;
        int anchoUtil = ANCHO - IZQUIERDA - DERECHA;
        g.setStroke(new BasicStroke(3));
        g.setColor(color);
        int anteriorX = -1, anteriorY = -1;
        for (int i = 0; i < datos.size(); i++) {
            int x = IZQUIERDA + i * anchoUtil / (TAMANOS.length - 1);
            int y = ARRIBA + altoUtil - (int) Math.round(datos.get(i).dpllMs() / max * altoUtil);
            if (anteriorX >= 0) g.drawLine(anteriorX, anteriorY, x, y);
            g.fillOval(x - 5, y - 5, 10, 10);
            anteriorX = x;
            anteriorY = y;
        }
    }

    private static void graficaComparacion(List<Resumen> datos, Path salida) throws IOException {
        List<Resumen> sat = filtrar(datos, "SAT");
        double minimo = Double.POSITIVE_INFINITY;
        double maximo = 0;
        for (Resumen r : sat) {
            minimo = Math.min(minimo, Math.min(r.dpllMs(), r.verificadorMs()));
            maximo = Math.max(maximo, Math.max(r.dpllMs(), r.verificadorMs()));
        }
        // Limites en potencias de diez para comparar tiempos muy distintos.
        double minEscala = Math.pow(10, Math.floor(Math.log10(Math.max(minimo, 1e-9))) - 1);
        double maxEscala = Math.pow(10, Math.ceil(Math.log10(Math.max(maximo, 1e-9))) + 1);
        double logMin = Math.log10(minEscala);
        double logMax = Math.log10(maxEscala);

        BufferedImage imagen = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nuevoLienzo(imagen, "DPLL frente a verificacion (SAT)",
                "Tiempo mediano en milisegundos - eje Y logaritmico");
        int altoUtil = ALTO - ARRIBA - ABAJO;
        int anchoUtil = ANCHO - IZQUIERDA - DERECHA;
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        for (int exp = (int) Math.ceil(logMin); exp <= (int) Math.floor(logMax); exp++) {
            double valor = Math.pow(10, exp);
            int y = ARRIBA + altoUtil - (int) Math.round((exp - logMin) /
                    (logMax - logMin) * altoUtil);
            g.setColor(new Color(225, 230, 235));
            g.drawLine(IZQUIERDA, y, ANCHO - DERECHA, y);
            g.setColor(new Color(75, 85, 99));
            g.drawString(String.format(Locale.US, "%.6g", valor), 35, y + 5);
        }
        g.setColor(new Color(75, 85, 99));
        g.drawString("Tiempo (ms, log)", 12, 86);
        for (int i = 0; i < sat.size(); i++) {
            int centroX = IZQUIERDA + (2 * i + 1) * anchoUtil / (2 * sat.size());
            dibujarBarraLog(g, centroX - 32, sat.get(i).dpllMs(),
                    AZUL, logMin, logMax, altoUtil);
            dibujarBarraLog(g, centroX + 3, sat.get(i).verificadorMs(),
                    NARANJA, logMin, logMax, altoUtil);
            g.setColor(new Color(75, 85, 99));
            g.drawString(String.valueOf(sat.get(i).variables()), centroX - 8,
                    ALTO - ABAJO + 22);
        }
        leyenda(g, AZUL, "DPLL", ANCHO - 235, 45);
        leyenda(g, NARANJA, "Verificador", ANCHO - 145, 45);
        g.dispose();
        ImageIO.write(imagen, "png", salida.toFile());
    }

    private static void dibujarBarraLog(Graphics2D g, int x, double valor, Color color,
                                        double logMin, double logMax, int altoUtil) {
        double logValor = Math.log10(Math.max(valor, Math.pow(10, logMin)));
        double proporcion = (logValor - logMin) / (logMax - logMin);
        int altura = Math.max(1, (int) Math.round(proporcion * altoUtil));
        g.setColor(color);
        g.fillRoundRect(x, ARRIBA + altoUtil - altura, 28, altura, 4, 4);
    }

    private static List<Resumen> filtrar(List<Resumen> datos, String tipo) {
        List<Resumen> resultado = new ArrayList<>();
        for (Resumen r : datos) if (r.tipo().equals(tipo)) resultado.add(r);
        return resultado;
    }

    private static void leyenda(Graphics2D g, Color color, String nombre, int x, int y) {
        g.setColor(color);
        g.fillRect(x, y - 12, 15, 11);
        g.setColor(new Color(45, 55, 75));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString(nombre, x + 21, y - 2);
    }
}
