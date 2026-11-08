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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Experimentos ampliados del SAT Solver (Persona 5).
 *
 * NO modifica el parser, DPLL, verificador, servidor ni BenchmarkSAT.java.
 * Guarda cada ejecucion en experiments/resultados/ampliado_... (sin sobrescribir).
 * Requiere JDK 17 o superior. Ejecutar desde la raiz del proyecto SAT_SOLVER.
 *
 * Separacion metodologica:
 *  - 5 tamanos x 4 escenarios x 5 instancias diferentes = 100 formulas.
 *  - 20 repeticiones cronometradas por formula, tras calentamiento.
 *  - Mediana por instancia; luego mediana de medianas por configuracion.
 *  - Para UNSAT no se mide el verificador: no existe certificado SAT.
 */
public final class BenchmarkSATAvanzado {
    static { System.setProperty("java.awt.headless", "true"); }

    private static final int[] TAMANOS = {6, 9, 12, 15, 18};
    private static final int INSTANCIAS = 5;
    private static final int CALENTAMIENTO = 10;
    private static final int REPETICIONES = 20;
    private static final long SEMILLA_BASE = 20261108L;
    private static final com.sun.management.ThreadMXBean MEMORIA_BEAN = activarMemoria();
    private static final String METODO_MEMORIA = MEMORIA_BEAN == null
            ? "NO_DISPONIBLE" : "BYTES_ASIGNADOS_HILO";

    private enum Escenario {
        SAT_BAJA_DENSIDAD(true, new Color(37, 99, 235)),
        SAT_ALTA_DENSIDAD(true, new Color(29, 140, 106)),
        UNSAT_INMEDIATO(false, new Color(234, 88, 12)),
        UNSAT_NUCLEO_COMPLETO(false, new Color(133, 65, 180));

        final boolean esSat;
        final Color color;
        Escenario(boolean esSat, Color color) { this.esSat = esSat; this.color = color; }
    }

    private record Caso(FormulaCNF formula, boolean[] testigo, int nucleovariables) { }
    private record Medicion(long tiempoNs, long bytesAsignados) { }
    private record InstanciaResumen(Escenario escenario, int n, int m, int l, int nucleo,
                                   int indice, long semilla, double dpllMs, double dpllBytes,
                                   Double verificarMs, Double verificarBytes) { }
    private record GrupoResumen(Escenario escenario, int n, int instancias,
                                double medianaDpllMs, double q1DpllMs, double q3DpllMs,
                                double medianaDpllBytes, Double medianaVerifMs,
                                Double medianaVerifBytes) { }

    private BenchmarkSATAvanzado() { }

    public static void main(String[] args) {
        try { ejecutar(); }
        catch (Exception e) {
            System.err.println("ERROR en benchmark ampliado: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void ejecutar() throws IOException {
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path directorio = Paths.get("experiments", "resultados", "ampliado_" + id);
        Path instanciasDir = directorio.resolve("instancias");
        Files.createDirectories(instanciasDir);

        List<InstanciaResumen> resultados = new ArrayList<>();
        System.out.println("=== SAT SOLVER | EXPERIMENTACION AMPLIADA | PERSONA 5 ===");
        System.out.printf(Locale.US, "Tamanos: %s | Escenarios: %d | Instancias por escenario: %d%n",
                Arrays.toString(TAMANOS), Escenario.values().length, INSTANCIAS);
        System.out.printf(Locale.US, "Calentamiento: %d | Repeticiones: %d | Memoria: %s%n",
                CALENTAMIENTO, REPETICIONES, METODO_MEMORIA);
        System.out.println("Primer benchmark: sin cambios. Resultados nuevos: " + directorio);

        try (BufferedWriter mediciones = abrirNuevo(directorio.resolve("mediciones.csv"));
             BufferedWriter porInstancia = abrirNuevo(directorio.resolve("por_instancia.csv"))) {
            escribirLinea(mediciones, "escenario,n,instancia,semilla,clausulas,literales,nucleo,"
                    + "repeticion,resultado,dpll_ns,dpll_bytes,verificador_ns,verificador_bytes,metodo_memoria");
            escribirLinea(porInstancia, "escenario,n,instancia,semilla,clausulas,literales,nucleo,"
                    + "mediana_dpll_ms,mediana_dpll_bytes,mediana_verificador_ms,"
                    + "mediana_verificador_bytes,resultado");

            // Mismo orden para cada ejecucion, semillas diferentes y casos independientes.
            for (int n : TAMANOS) {
                for (Escenario escenario : Escenario.values()) {
                    for (int i = 1; i <= INSTANCIAS; i++) {
                        long semilla = SEMILLA_BASE + 1_000_003L * n
                                + 10_007L * escenario.ordinal() + 97L * i;
                        Caso caso = generarCaso(escenario, n, semilla);
                        String nombre = String.format(Locale.US, "%s_n%02d_i%02d.cnf",
                                escenario.name().toLowerCase(Locale.ROOT), n, i);
                        guardarDimacs(caso.formula(), instanciasDir.resolve(nombre));
                        InstanciaResumen r = medir(escenario, n, i, semilla, caso, mediciones);
                        resultados.add(r);
                        escribirLinea(porInstancia, String.format(Locale.US,
                                "%s,%d,%d,%d,%d,%d,%d,%.6f,%s,%s,%s,%s",
                                escenario, n, i, semilla, r.m(), r.l(), r.nucleo(),
                                r.dpllMs(), fmt(r.dpllBytes(), 0), fmt(r.verificarMs(), 6),
                                fmt(r.verificarBytes(), 0), escenario.esSat ? "SAT" : "UNSAT"));
                    }
                    System.out.printf(Locale.US, "%-22s | n=%2d | %d instancias completadas%n",
                            escenario.name(), n, INSTANCIAS);
                }
            }
        }

        List<GrupoResumen> grupos = resumir(resultados);
        try (BufferedWriter resumen = abrirNuevo(directorio.resolve("resumen.csv"))) {
            escribirLinea(resumen, "escenario,n,instancias,mediana_dpll_ms,q1_dpll_ms,q3_dpll_ms,"
                    + "mediana_dpll_bytes,mediana_verificador_ms,mediana_verificador_bytes,metodo_memoria");
            for (GrupoResumen g : grupos) {
                escribirLinea(resumen, String.format(Locale.US,
                        "%s,%d,%d,%.6f,%.6f,%.6f,%s,%s,%s,%s",
                        g.escenario(), g.n(), g.instancias(), g.medianaDpllMs(),
                        g.q1DpllMs(), g.q3DpllMs(), fmt(g.medianaDpllBytes(), 0),
                        fmt(g.medianaVerifMs(), 6), fmt(g.medianaVerifBytes(), 0), METODO_MEMORIA));
            }
        }
        escribirEntorno(directorio.resolve("entorno.txt"));
        guardarGrafica(grupos, directorio.resolve("grafico_dpll_escenarios.png"), false);
        guardarGrafica(grupos, directorio.resolve("grafico_resolver_verificar.png"), true);

        System.out.printf(Locale.US,
                "%nCOMPLETADO: %d formulas distintas y %d mediciones DPLL.%n",
                resultados.size(), resultados.size() * REPETICIONES);
        System.out.println("Directorio: " + directorio.toAbsolutePath());
        System.out.println("Archivos: mediciones.csv, por_instancia.csv, resumen.csv, entorno.txt,");
        System.out.println("          grafico_dpll_escenarios.png, grafico_resolver_verificar.png");
        System.out.println("          instancias/ (archivos DIMACS CNF para reproducibilidad)");
    }

    private static Caso generarCaso(Escenario escenario, int n, long semilla) {
        switch (escenario) {
            case SAT_BAJA_DENSIDAD: return generarSat(n, 2 * n, semilla);
            case SAT_ALTA_DENSIDAD: return generarSat(n, 5 * n, semilla);
            case UNSAT_INMEDIATO: {
                Caso base = generarSat(n, 2 * n, semilla);
                List<int[]> clausulas = base.formula().clauses();
                // x1 y !x1: UNSAT demostrado sin busqueda extensa.
                clausulas.add(new int[]{1});
                clausulas.add(new int[]{-1});
                return new Caso(new FormulaCNF(nombres(n), clausulas), null, 1);
            }
            case UNSAT_NUCLEO_COMPLETO: {
                Caso base = generarSat(n, 2 * n, semilla);
                List<int[]> clausulas = base.formula().clauses();
                int k = Math.max(3, Math.min(6, n / 3));
                // Cada clausula descarta una de las 2^k asignaciones posibles.
                // En conjunto descartan TODAS las asignaciones: UNSAT sin clausulas unitarias.
                List<int[]> cubo = new ArrayList<>();
                Random azar = new Random(semilla ^ 0x5A55AA33L);
                for (int mascara = 0; mascara < (1 << k); mascara++) {
                    int[] prohibicion = new int[k];
                    for (int v = 0; v < k; v++) {
                        prohibicion[v] = (mascara & (1 << v)) != 0 ? -(v + 1) : v + 1;
                    }
                    // Permutar literales cambia el trabajo de evaluacion, no la satisfacibilidad.
                    for (int pos = k - 1; pos > 0; pos--) {
                        int j = azar.nextInt(pos + 1);
                        int t = prohibicion[pos]; prohibicion[pos] = prohibicion[j]; prohibicion[j] = t;
                    }
                    cubo.add(prohibicion);
                }
                Collections.shuffle(cubo, azar);
                clausulas.addAll(cubo);
                return new Caso(new FormulaCNF(nombres(n), clausulas), null, k);
            }
            default: throw new IllegalStateException("Escenario desconocido");
        }
    }

    /** Genera clausulas 3-CNF distintas, todas satisfechas por un testigo conocido. */
    private static Caso generarSat(int n, int m, long semilla) {
        if (n < 3 || m < n) throw new IllegalArgumentException("Parametros SAT invalidos");
        Random azar = new Random(semilla);
        boolean[] testigo = new boolean[n + 1];
        for (int v = 1; v <= n; v++) testigo[v] = azar.nextBoolean();
        List<int[]> clausulas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        int intentos = 0;
        while (clausulas.size() < m) {
            if (++intentos > 100_000) throw new IllegalStateException("No se pudieron generar clausulas distintas");
            int a = clausulas.size() < n ? clausulas.size() + 1 : azar.nextInt(n) + 1;
            int b;
            do { b = azar.nextInt(n) + 1; } while (b == a);
            int c;
            do { c = azar.nextInt(n) + 1; } while (c == a || c == b);
            int[] cl = {azar.nextBoolean() ? a : -a,
                    azar.nextBoolean() ? b : -b,
                    azar.nextBoolean() ? c : -c};
            if (!satisface(cl, testigo)) cl[0] = -cl[0];
            int[] ordenada = cl.clone(); Arrays.sort(ordenada);
            if (vistas.add(Arrays.toString(ordenada))) clausulas.add(cl);
        }
        FormulaCNF formula = new FormulaCNF(nombres(n), clausulas);
        if (!satisfaceTodas(formula, testigo)) {
            throw new IllegalStateException("Error de generacion: testigo SAT invalido");
        }
        return new Caso(formula, testigo, 0);
    }

    private static boolean satisface(int[] clausula, boolean[] valores) {
        for (int l : clausula) if (l > 0 ? valores[l] : !valores[-l]) return true;
        return false;
    }

    private static boolean satisfaceTodas(FormulaCNF formula, boolean[] valores) {
        for (int[] cl : formula.clauses()) if (!satisface(cl, valores)) return false;
        return true;
    }

    private static List<String> nombres(int n) {
        List<String> nombres = new ArrayList<>();
        for (int i = 1; i <= n; i++) nombres.add("x" + i);
        return nombres;
    }

    private static InstanciaResumen medir(Escenario escenario, int n, int indice, long semilla,
                                         Caso caso, BufferedWriter salida) throws IOException {
        FormulaCNF formula = caso.formula();
        DPLLSolver solver = new DPLLSolver();
        AssignmentVerifier verificador = new AssignmentVerifier();
        Optional<Map<String, Boolean>> control = solver.solve(formula);
        if (control.isPresent() != escenario.esSat) {
            throw new IllegalStateException("Resultado inesperado en " + escenario + " n=" + n);
        }
        Map<String, Boolean> solucion = control.orElse(null);
        if (escenario.esSat) {
            if (!verificador.verify(formula, solucion))
                throw new IllegalStateException("DPLL devolvio certificado SAT incorrecto");
            if (!satisfaceTodas(formula, caso.testigo()))
                throw new IllegalStateException("El testigo construido no satisface la CNF");
        }

        for (int i = 0; i < CALENTAMIENTO; i++) {
            solver.solve(formula);
            if (escenario.esSat) verificador.verify(formula, solucion);
        }
        List<Long> tiempos = new ArrayList<>();
        List<Long> memorias = new ArrayList<>();
        List<Long> tiemposVerificador = new ArrayList<>();
        List<Long> memoriaVerificador = new ArrayList<>();
        for (int rep = 1; rep <= REPETICIONES; rep++) {
            long antesMem = leerMemoria();
            long inicio = System.nanoTime();
            Optional<Map<String, Boolean>> obtenido = solver.solve(formula);
            long ns = System.nanoTime() - inicio;
            long despuesMem = leerMemoria();
            if (obtenido.isPresent() != escenario.esSat)
                throw new IllegalStateException("Respuesta no determinista en " + escenario);
            if (obtenido.isPresent() && !verificador.verify(formula, obtenido.get()))
                throw new IllegalStateException("Certificado incorrecto durante repeticion");
            tiempos.add(ns);
            long bytes = diferenciaMemoria(antesMem, despuesMem);
            if (bytes >= 0) memorias.add(bytes);

            Medicion v = null;
            if (escenario.esSat) {
                antesMem = leerMemoria();
                inicio = System.nanoTime();
                boolean valido = verificador.verify(formula, solucion);
                long nsVer = System.nanoTime() - inicio;
                despuesMem = leerMemoria();
                if (!valido) throw new IllegalStateException("Fallo el verificador SAT");
                v = new Medicion(nsVer, diferenciaMemoria(antesMem, despuesMem));
                tiemposVerificador.add(nsVer);
                if (v.bytesAsignados() >= 0) memoriaVerificador.add(v.bytesAsignados());
            }
            escribirLinea(salida, String.format(Locale.US,
                    "%s,%d,%d,%d,%d,%d,%d,%d,%s,%d,%s,%s,%s,%s",
                    escenario, n, indice, semilla, formula.numeroClausulas(),
                    formula.numeroLiterales(), caso.nucleovariables(), rep,
                    escenario.esSat ? "SAT" : "UNSAT", ns, bytes >= 0 ? String.valueOf(bytes) : "",
                    v == null ? "" : String.valueOf(v.tiempoNs()),
                    v == null || v.bytesAsignados() < 0 ? "" : String.valueOf(v.bytesAsignados()),
                    METODO_MEMORIA));
        }
        return new InstanciaResumen(escenario, n, formula.numeroClausulas(),
                formula.numeroLiterales(), caso.nucleovariables(), indice, semilla,
                medianaNumeros(tiempos) / 1_000_000.0,
                memorias.isEmpty() ? Double.NaN : medianaNumeros(memorias),
                escenario.esSat ? medianaNumeros(tiemposVerificador) / 1_000_000.0 : null,
                escenario.esSat && !memoriaVerificador.isEmpty()
                        ? medianaNumeros(memoriaVerificador) : null);
    }

    private static List<GrupoResumen> resumir(List<InstanciaResumen> instancias) {
        List<GrupoResumen> grupos = new ArrayList<>();
        for (int n : TAMANOS) {
            for (Escenario escenario : Escenario.values()) {
                List<Double> ts = new ArrayList<>();
                List<Double> ms = new ArrayList<>();
                List<Double> tv = new ArrayList<>();
                List<Double> mv = new ArrayList<>();
                for (InstanciaResumen r : instancias) {
                    if (r.n() != n || r.escenario() != escenario) continue;
                    ts.add(r.dpllMs());
                    if (Double.isFinite(r.dpllBytes())) ms.add(r.dpllBytes());
                    if (r.verificarMs() != null) tv.add(r.verificarMs());
                    if (r.verificarBytes() != null) mv.add(r.verificarBytes());
                }
                if (ts.size() != INSTANCIAS) throw new IllegalStateException("Faltan instancias");
                grupos.add(new GrupoResumen(escenario, n, ts.size(), percentil(ts, 0.5),
                        percentil(ts, 0.25), percentil(ts, 0.75),
                        ms.isEmpty() ? Double.NaN : percentil(ms, 0.5),
                        tv.isEmpty() ? null : percentil(tv, 0.5),
                        mv.isEmpty() ? null : percentil(mv, 0.5)));
            }
        }
        return grupos;
    }

    private static double medianaNumeros(List<Long> numeros) {
        if (numeros.isEmpty()) throw new IllegalArgumentException("Sin mediciones");
        List<Double> valores = new ArrayList<>();
        for (long n : numeros) valores.add((double) n);
        return percentil(valores, 0.5);
    }

    /** Percentil por interpolacion lineal sobre posiciones 0..N-1. */
    private static double percentil(List<Double> valores, double p) {
        if (valores.isEmpty()) throw new IllegalArgumentException("Sin valores para percentil");
        List<Double> orden = new ArrayList<>(valores);
        Collections.sort(orden);
        double pos = p * (orden.size() - 1);
        int bajo = (int) Math.floor(pos);
        int alto = (int) Math.ceil(pos);
        return orden.get(bajo) + (orden.get(alto) - orden.get(bajo)) * (pos - bajo);
    }

    private static String fmt(Double valor, int digitos) {
        return valor == null || !Double.isFinite(valor) ? ""
                : String.format(Locale.US, "%." + digitos + "f", valor);
    }

    private static com.sun.management.ThreadMXBean activarMemoria() {
        try {
            java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean m
                    && m.isThreadAllocatedMemorySupported()) {
                if (!m.isThreadAllocatedMemoryEnabled()) m.setThreadAllocatedMemoryEnabled(true);
                if (m.getThreadAllocatedBytes(Thread.currentThread().getId()) >= 0) return m;
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return null;
    }

    private static long leerMemoria() {
        return MEMORIA_BEAN == null ? -1 : MEMORIA_BEAN.getThreadAllocatedBytes(
                Thread.currentThread().getId());
    }

    private static long diferenciaMemoria(long antes, long despues) {
        return antes < 0 || despues < 0 || despues < antes ? -1 : despues - antes;
    }

    private static BufferedWriter abrirNuevo(Path ruta) throws IOException {
        return Files.newBufferedWriter(ruta, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void escribirLinea(BufferedWriter writer, String linea) throws IOException {
        writer.write(linea);
        writer.newLine();
    }

    private static void guardarDimacs(FormulaCNF f, Path ruta) throws IOException {
        try (BufferedWriter writer = abrirNuevo(ruta)) {
            escribirLinea(writer, "c SAT_SOLVER experimental case - variable xI maps to integer I");
            escribirLinea(writer, "p cnf " + f.variables().size() + " " + f.numeroClausulas());
            for (int[] clausula : f.clauses()) {
                StringBuilder sb = new StringBuilder();
                for (int lit : clausula) sb.append(lit).append(' ');
                sb.append('0');
                escribirLinea(writer, sb.toString());
            }
        }
    }

    private static void escribirEntorno(Path archivo) throws IOException {
        List<String> texto = Arrays.asList(
                "PROYECTO SAT SOLVER - EXPERIMENTACION AMPLIADA - PERSONA 5",
                "Fecha de ejecucion: " + LocalDateTime.now(),
                "Java runtime: " + System.getProperty("java.runtime.version"),
                "Sistema operativo: " + System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "Procesadores logicos: " + Runtime.getRuntime().availableProcessors(),
                "Semilla base: " + SEMILLA_BASE,
                "Tamanos n: " + Arrays.toString(TAMANOS),
                "Escenarios: " + Arrays.toString(Escenario.values()),
                "Instancias independientes por escenario y tamano: " + INSTANCIAS,
                "Calentamiento por instancia: " + CALENTAMIENTO,
                "Repeticiones medidas por instancia: " + REPETICIONES,
                "Metodo memoria: " + METODO_MEMORIA,
                "Memoria: bytes asignados por hilo; NO pico de RAM ni espacio vivo del algoritmo.",
                "Sin soporte de ThreadMXBean: memoria vacia (no inventar valores).",
                "SAT_BAJA_DENSIDAD: 2*n clausulas 3-CNF con testigo plantado.",
                "SAT_ALTA_DENSIDAD: 5*n clausulas 3-CNF con testigo plantado.",
                "UNSAT_INMEDIATO: SAT base + x1 + !x1; detectabilidad inmediata.",
                "UNSAT_NUCLEO_COMPLETO: SAT base + 2^k clausulas que prohiben todas las asignaciones de k variables.",
                "k = max(3, min(6, n/3)); UNSAT por construccion, sin clausulas unitarias en el nucleo.",
                "ATENCION: densidad no equivale necesariamente a dificultad del problema.",
                "ATENCION: el nucleo completo involucra a lo sumo seis variables; NO es el peor caso general.",
                "Cada formula SAT se valida con testigo conocido y con el verificador del equipo.",
                "Solo se mide solver.solve() / verifier.verify(), sin generacion, parser, HTTP, CSV o dibujos.",
                "Mediana por instancia de repeticiones, luego mediana de medianas sobre cinco instancias.",
                "Cuartiles Q1/Q3: distribucion entre las cinco medianas por instancia.",
                "Rendimiento sujeto a calentamiento JIT, GC, CPU y ejecucion de fondo."
        );
        Files.write(archivo, texto, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static final int W = 1100, H = 650, X0 = 130, Y0 = 115, W_GRAF = 910, H_GRAF = 400;
    private record Serie(String etiqueta, Color color, double[] valores) { }

    private static void guardarGrafica(List<GrupoResumen> grupos, Path archivo, boolean comparacion) throws IOException {
        List<Serie> series = new ArrayList<>();
        if (comparacion) {
            for (Escenario e : new Escenario[]{Escenario.SAT_BAJA_DENSIDAD, Escenario.SAT_ALTA_DENSIDAD}) {
                series.add(new Serie("DPLL " + (e == Escenario.SAT_BAJA_DENSIDAD ? "2n" : "5n"),
                        e.color, valoresDe(grupos, e, false)));
                series.add(new Serie("Verif. " + (e == Escenario.SAT_BAJA_DENSIDAD ? "2n" : "5n"),
                        e == Escenario.SAT_BAJA_DENSIDAD ? new Color(100, 150, 240) : new Color(105, 195, 155),
                        valoresDe(grupos, e, true)));
            }
        } else {
            for (Escenario e : Escenario.values()) {
                series.add(new Serie(e.name(), e.color, valoresDe(grupos, e, false)));
            }
        }
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (Serie serie : series) for (double v : serie.valores()) {
            if (v > 0 && Double.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); }
        }
        if (!Double.isFinite(min) || max <= 0) throw new IllegalStateException("Tiempos invalidos para grafica");
        double minLog = Math.floor(Math.log10(min)) - 0.4;
        double maxLog = Math.ceil(Math.log10(max)) + 0.4;
        if (maxLog <= minLog) maxLog = minLog + 1;

        BufferedImage imagen = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        g.setColor(new Color(29, 40, 56)); g.setFont(new Font("SansSerif", Font.BOLD, 23));
        g.drawString(comparacion ? "Comparacion: encontrar frente a verificar SAT"
                : "DPLL: cuatro escenarios de entrada", X0, 44);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Mediana de 5 instancias, cada una con 20 repeticiones - eje Y logaritmico", X0, 68);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (int t = 0; t <= 5; t++) {
            double log = minLog + (maxLog - minLog) * t / 5.0;
            int y = Y0 + H_GRAF - t * H_GRAF / 5;
            g.setColor(new Color(225, 231, 237)); g.drawLine(X0, y, X0 + W_GRAF, y);
            g.setColor(new Color(65, 75, 85));
            g.drawString(String.format(Locale.US, "%.5f", Math.pow(10, log)), 45, y + 4);
        }
        for (int i = 0; i < TAMANOS.length; i++) {
            int x = X0 + i * W_GRAF / (TAMANOS.length - 1);
            g.setColor(new Color(65, 75, 85)); g.drawString(String.valueOf(TAMANOS[i]), x - 5, Y0 + H_GRAF + 24);
        }
        g.drawString("Tiempo (ms, escala logaritmica)", 14, 96);
        g.drawString("Numero de variables (n)", W / 2 - 80, H - 48);

        for (Serie s : series) {
            g.setColor(s.color()); g.setStroke(new BasicStroke(2.5f));
            int px = -1, py = -1;
            for (int i = 0; i < TAMANOS.length; i++) {
                double v = Math.max(s.valores()[i], 1e-12);
                int x = X0 + i * W_GRAF / (TAMANOS.length - 1);
                int y = Y0 + H_GRAF - (int) Math.round((Math.log10(v) - minLog)
                        / (maxLog - minLog) * H_GRAF);
                if (px >= 0) g.drawLine(px, py, x, y);
                g.fillOval(x - 4, y - 4, 8, 8);
                px = x; py = y;
            }
        }
        int xLegend = 135;
        for (int i = 0; i < series.size(); i++) {
            Serie serie = series.get(i);
            int x = xLegend + i * 233;
            g.setColor(serie.color()); g.fillRect(x, H - 95, 15, 12);
            g.setColor(new Color(50, 60, 75)); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(serie.etiqueta(), x + 20, H - 84);
        }
        g.dispose();
        if (!ImageIO.write(imagen, "png", archivo.toFile()))
            throw new IOException("No se pudo generar PNG: " + archivo);
    }

    private static double[] valoresDe(List<GrupoResumen> grupos, Escenario e, boolean verificar) {
        double[] valores = new double[TAMANOS.length];
        for (int i = 0; i < TAMANOS.length; i++) {
            final int n = TAMANOS[i];
            GrupoResumen r = grupos.stream().filter(g -> g.escenario() == e && g.n() == n)
                    .findFirst().orElseThrow();
            valores[i] = verificar ? r.medianaVerifMs() : r.medianaDpllMs();
        }
        return valores;
    }
}
