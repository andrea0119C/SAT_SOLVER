package ec.edu.satsolver.experiment;

import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.solver.DPLLSolver;
import ec.edu.satsolver.solver.TruthTableEnumerator;
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
 * Persona 5. Compara el enumerador de P3, DPLL de P3 y el verificador de P4.
 * No altera los modulos existentes. Java 17+; ejecutar desde raiz SAT_SOLVER.
 * Importante: enumerar TODAS las combinaciones no es equivalente a encontrar UNA solucion.
 * Se mide trabajo computacional, no el tiempo de renderizar HTML/JSON.
 */
public final class BenchmarkSATTabla {
    static { System.setProperty("java.awt.headless", "true"); }

    private static final int[] TAMANOS = {4, 6, 8, 10, 12};
    private static final int INSTANCIAS = 3;
    private static final int CALENTAMIENTO = 5;
    private static final int REPETICIONES = 10;
    private static final long SEMILLA_BASE = 20261114L;
    private static final com.sun.management.ThreadMXBean MEMORIA = configurarMemoria();
    private static volatile long CONSUMIDOR;

    private enum Escenario {
        SAT_BAJA_DENSIDAD(true, new Color(39, 99, 231)),
        SAT_ALTA_DENSIDAD(true, new Color(15, 137, 111)),
        UNSAT_INMEDIATO(false, new Color(230, 93, 29)),
        UNSAT_NUCLEO_COMPLETO(false, new Color(144, 76, 186));
        final boolean sat;
        final Color color;
        Escenario(boolean sat, Color color) { this.sat = sat; this.color = color; }
    }

    private record Caso(FormulaCNF formula, Map<String,Boolean> candidata, int k) { }
    private record Medida(long ns, long bytes) { }
    private record Registro(Escenario escenario, int n, int instancia, long semilla,
                            int clausulas, int literales, int nucleo, int filas,
                            double tablaMs, double dpllMs, double verificadorMs,
                            double tablaBytes, double dpllBytes, double verificadorBytes) { }
    private record Grupo(Escenario escenario, int n, int instancias, int clausulas, int filas,
                         double tablaMs, double q1Tabla, double q3Tabla,
                         double dpllMs, double verificadorMs,
                         double tablaBytes, double dpllBytes, double verificadorBytes) { }
    private record Serie(String nombre, Color color, double[] ms) { }

    private BenchmarkSATTabla() { }

    public static void main(String[] args) {
        try { ejecutar(); }
        catch (Exception e) {
            System.err.println("Error en BenchmarkSATTabla: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void ejecutar() throws IOException {
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path raiz = Paths.get("experiments", "resultados", "tabla_verdad_" + id);
        Files.createDirectories(raiz.resolve("instancias"));
        List<Registro> registros = new ArrayList<>();
        System.out.println("=== SAT SOLVER | ENUMERACION vs DPLL vs VERIFICACION | P5 ===");
        System.out.println("5 tamanos (4..12), 4 escenarios, 3 formulas/escenario, 10 repeticiones.");
        try (BufferedWriter mediciones = nuevo(raiz.resolve("mediciones.csv"));
             BufferedWriter porInstancia = nuevo(raiz.resolve("por_instancia.csv"))) {
            linea(mediciones, "escenario,n,instancia,semilla,clausulas,literales,nucleo,filas,repeticion,"
                    + "enumeracion_ns,dpll_ns,verificacion_ns,enumeracion_bytes,dpll_bytes,verificacion_bytes");
            linea(porInstancia, "escenario,n,instancia,semilla,clausulas,literales,nucleo,filas,"
                    + "mediana_enumeracion_ms,mediana_dpll_ms,mediana_verificacion_ms,"
                    + "mediana_enumeracion_bytes,mediana_dpll_bytes,mediana_verificacion_bytes");
            for (int n : TAMANOS) {
                for (Escenario escenario : Escenario.values()) {
                    for (int i = 1; i <= INSTANCIAS; i++) {
                        long semilla = SEMILLA_BASE + n * 1_000_003L
                                + escenario.ordinal() * 100_007L + i * 997L;
                        Caso caso = generar(escenario, n, semilla);
                        guardarDimacs(caso.formula(), raiz.resolve("instancias").resolve(
                                escenario.name().toLowerCase(Locale.ROOT) + "_n" + n + "_" + i + ".cnf"));
                        Registro r = probar(escenario, n, i, semilla, caso, mediciones);
                        registros.add(r);
                        linea(porInstancia, String.format(Locale.US,
                                "%s,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%s,%s,%s",
                                escenario, n, i, semilla, r.clausulas(), r.literales(), r.nucleo(), r.filas(),
                                r.tablaMs(), r.dpllMs(), r.verificadorMs(),
                                numero(r.tablaBytes()), numero(r.dpllBytes()), numero(r.verificadorBytes())));
                    }
                    System.out.printf("%-23s n=%2d | filas=%5d | 3 formulas completadas%n",
                            escenario, n, 1 << n);
                }
            }
        }
        List<Grupo> grupos = agrupar(registros);
        try (BufferedWriter resumen = nuevo(raiz.resolve("resumen.csv"))) {
            linea(resumen, "escenario,n,instancias,clausulas,filas,mediana_enumeracion_ms,"
                    + "q1_enumeracion_ms,q3_enumeracion_ms,mediana_dpll_ms,mediana_verificacion_ms,"
                    + "mediana_enumeracion_bytes,mediana_dpll_bytes,mediana_verificacion_bytes,metodo_memoria");
            for (Grupo g : grupos) {
                linea(resumen, String.format(Locale.US,
                        "%s,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s",
                        g.escenario(), g.n(), g.instancias(), g.clausulas(), g.filas(),
                        g.tablaMs(), g.q1Tabla(), g.q3Tabla(), g.dpllMs(), g.verificadorMs(),
                        numero(g.tablaBytes()), numero(g.dpllBytes()), numero(g.verificadorBytes()),
                        MEMORIA == null ? "NO_DISPONIBLE" : "BYTES_ASIGNADOS_HILO"));
            }
        }
        graficar(raiz.resolve("grafico_tres_metodos.png"),
                "SAT (baja densidad): enumerar, resolver y verificar", grupos, false);
        graficar(raiz.resolve("grafico_enumeracion_escenarios.png"),
                "Enumeracion exhaustiva por escenario", grupos, true);
        guardarEntorno(raiz.resolve("entorno.txt"));
        System.out.println("RESULTADOS: " + raiz.toAbsolutePath());
        System.out.println("mediciones.csv, por_instancia.csv, resumen.csv, entorno.txt,");
        System.out.println("grafico_tres_metodos.png, grafico_enumeracion_escenarios.png, instancias/");
    }

    private static Registro probar(Escenario escenario, int n, int indice, long semilla,
                                   Caso caso, BufferedWriter mediciones) throws IOException {
        FormulaCNF formula = caso.formula();
        TruthTableEnumerator enumerador = new TruthTableEnumerator();
        DPLLSolver dpll = new DPLLSolver();
        AssignmentVerifier verificador = new AssignmentVerifier();
        // Comprobar correccion FUERA de la region cronometrada.
        TruthTableEnumerator.Result tabla = enumerador.enumerate(formula);
        verificarTabla(formula, tabla, escenario.sat);
        Optional<Map<String,Boolean>> solucion = dpll.solve(formula);
        if (solucion.isPresent() != escenario.sat)
            throw new IllegalStateException("DPLL y caso esperado discrepan: " + escenario);
        if (solucion.isPresent() && !verificador.verify(formula, solucion.get()))
            throw new IllegalStateException("DPLL devolvio una asignacion incorrecta");
        boolean resultadoCandidato = verificador.verify(formula, caso.candidata());
        if (resultadoCandidato != escenario.sat)
            throw new IllegalStateException("Candidato de prueba inconsistente");

        for (int i = 0; i < CALENTAMIENTO; i++) {
            CONSUMIDOR += enumerador.enumerate(formula).satCount();
            CONSUMIDOR += dpll.solve(formula).isPresent() ? 1 : 0;
            CONSUMIDOR += verificador.verify(formula, caso.candidata()) ? 1 : 0;
        }
        List<Long> tEnum = new ArrayList<>(), tDpll = new ArrayList<>(), tVer = new ArrayList<>();
        List<Long> mEnum = new ArrayList<>(), mDpll = new ArrayList<>(), mVer = new ArrayList<>();
        for (int rep = 1; rep <= REPETICIONES; rep++) {
            // Mismas formulas; medir solo el metodo y consumir el resultado despues del cronometro.
            long antes = memoria();
            long inicio = System.nanoTime();
            TruthTableEnumerator.Result rTabla = enumerador.enumerate(formula);
            long nsTabla = System.nanoTime() - inicio;
            long bytesTabla = diferencia(antes, memoria());
            CONSUMIDOR += rTabla.satCount() + rTabla.unsatCount();
            if (rTabla.rows().size() != (1 << n) || (rTabla.satCount() > 0) != escenario.sat)
                throw new IllegalStateException("Conteos inconsistentes en enumeracion");

            antes = memoria();
            inicio = System.nanoTime();
            Optional<Map<String,Boolean>> rDpll = dpll.solve(formula);
            long nsDpll = System.nanoTime() - inicio;
            long bytesDpll = diferencia(antes, memoria());
            CONSUMIDOR += rDpll.isPresent() ? 1 : 0;
            if (rDpll.isPresent() != escenario.sat)
                throw new IllegalStateException("DPLL cambio de resultado");

            antes = memoria();
            inicio = System.nanoTime();
            boolean rVer = verificador.verify(formula, caso.candidata());
            long nsVer = System.nanoTime() - inicio;
            long bytesVer = diferencia(antes, memoria());
            CONSUMIDOR += rVer ? 1 : 0;
            if (rVer != escenario.sat) throw new IllegalStateException("Verificador cambio de resultado");

            tEnum.add(nsTabla); tDpll.add(nsDpll); tVer.add(nsVer);
            if (bytesTabla >= 0) mEnum.add(bytesTabla);
            if (bytesDpll >= 0) mDpll.add(bytesDpll);
            if (bytesVer >= 0) mVer.add(bytesVer);
            linea(mediciones, String.format(Locale.US,
                    "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%s",
                    escenario, n, indice, semilla, formula.numeroClausulas(), formula.numeroLiterales(),
                    caso.k(), 1 << n, rep, nsTabla, nsDpll, nsVer,
                    largo(bytesTabla), largo(bytesDpll), largo(bytesVer)));
        }
        return new Registro(escenario, n, indice, semilla, formula.numeroClausulas(),
                formula.numeroLiterales(), caso.k(), 1 << n,
                medianaLong(tEnum) / 1e6, medianaLong(tDpll) / 1e6, medianaLong(tVer) / 1e6,
                medianaMemoria(mEnum), medianaMemoria(mDpll), medianaMemoria(mVer));
    }

    /** Verificacion independiente de las columnas de la tabla: fuera de mediciones. */
    private static void verificarTabla(FormulaCNF f, TruthTableEnumerator.Result t, boolean esSat) {
        int n = f.variables().size();
        if (t.rows().size() != (1 << n) || t.variables().size() != n
                || t.clauses().size() != f.numeroClausulas())
            throw new IllegalStateException("Dimensiones incorrectas de la tabla");
        List<int[]> clausulas = f.clauses();
        int sat = 0;
        for (TruthTableEnumerator.Row fila : t.rows()) {
            if (fila.values().size() != n || fila.clausesSatisfied().size() != clausulas.size())
                throw new IllegalStateException("Fila con dimensiones incorrectas");
            boolean todas = true;
            for (int ci = 0; ci < clausulas.size(); ci++) {
                boolean cumple = false;
                for (int lit : clausulas.get(ci)) {
                    boolean v = fila.values().get(Math.abs(lit) - 1);
                    if (lit < 0) v = !v;
                    if (v) { cumple = true; break; }
                }
                if (cumple != fila.clausesSatisfied().get(ci))
                    throw new IllegalStateException("Columna de clausula incorrecta");
                todas &= cumple;
            }
            if (todas != fila.satisfies()) throw new IllegalStateException("Resultado de fila incorrecto");
            if (todas) sat++;
        }
        if (sat != t.satCount() || t.unsatCount() != (1 << n) - sat || (sat > 0) != esSat)
            throw new IllegalStateException("Conteo SAT/UNSAT incorrecto");
    }

    private static Caso generar(Escenario escenario, int n, long semilla) {
        if (escenario == Escenario.SAT_BAJA_DENSIDAD) return sat(n, 2 * n, semilla);
        if (escenario == Escenario.SAT_ALTA_DENSIDAD) return sat(n, 5 * n, semilla);
        Caso base = sat(n, 2 * n, semilla);
        List<int[]> cl = base.formula().clauses();
        int k;
        if (escenario == Escenario.UNSAT_INMEDIATO) {
            cl.add(new int[]{1}); cl.add(new int[]{-1});
            k = 1;
        } else {
            k = Math.max(3, Math.min(4, n / 3));
            List<int[]> nucleo = new ArrayList<>();
            Random azar = new Random(semilla ^ 0x5A55AA33L);
            for (int mask = 0; mask < (1 << k); mask++) {
                int[] c = new int[k];
                for (int j = 0; j < k; j++) c[j] = ((mask & (1 << j)) != 0) ? -(j + 1) : j + 1;
                for (int j = k - 1; j > 0; j--) {
                    int s = azar.nextInt(j + 1); int temp = c[s]; c[s] = c[j]; c[j] = temp;
                }
                nucleo.add(c);
            }
            Collections.shuffle(nucleo, azar);
            cl.addAll(nucleo);
        }
        Map<String,Boolean> candidata = new LinkedHashMap<>();
        for (String variable : nombres(n)) candidata.put(variable, false);
        return new Caso(new FormulaCNF(nombres(n), cl), candidata, k);
    }

    /** 3-CNF con asignacion satisfactoria plantada y clausulas diferentes. */
    private static Caso sat(int n, int m, long semilla) {
        Random azar = new Random(semilla);
        boolean[] testigo = new boolean[n + 1];
        for (int i = 1; i <= n; i++) testigo[i] = azar.nextBoolean();
        List<int[]> clausulas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        int intentos = 0;
        while (clausulas.size() < m) {
            if (++intentos > 100_000) throw new IllegalStateException("No se generaron clausulas suficientes");
            int a = clausulas.size() < n ? clausulas.size() + 1 : azar.nextInt(n) + 1;
            int b; do { b = azar.nextInt(n) + 1; } while (b == a);
            int c; do { c = azar.nextInt(n) + 1; } while (c == a || c == b);
            int[] cl = {azar.nextBoolean() ? a : -a,
                    azar.nextBoolean() ? b : -b, azar.nextBoolean() ? c : -c};
            if (!cumple(cl, testigo)) cl[0] = -cl[0];
            int[] orden = cl.clone(); Arrays.sort(orden);
            if (vistas.add(Arrays.toString(orden))) clausulas.add(cl);
        }
        Map<String,Boolean> candidata = new LinkedHashMap<>();
        for (int i = 1; i <= n; i++) candidata.put("x" + i, testigo[i]);
        return new Caso(new FormulaCNF(nombres(n), clausulas), candidata, 0);
    }
    private static boolean cumple(int[] c, boolean[] testigo) {
        for (int lit : c) if (lit > 0 ? testigo[lit] : !testigo[-lit]) return true;
        return false;
    }
    private static List<String> nombres(int n) {
        List<String> v = new ArrayList<>();
        for (int i = 1; i <= n; i++) v.add("x" + i);
        return v;
    }

    private static List<Grupo> agrupar(List<Registro> datos) {
        List<Grupo> out = new ArrayList<>();
        for (int n : TAMANOS) for (Escenario e : Escenario.values()) {
            List<Double> te = new ArrayList<>(), td = new ArrayList<>(), tv = new ArrayList<>();
            List<Double> me = new ArrayList<>(), md = new ArrayList<>(), mv = new ArrayList<>();
            int m = -1, filas = -1;
            for (Registro r : datos) {
                if (r.n() != n || r.escenario() != e) continue;
                m = r.clausulas(); filas = r.filas();
                te.add(r.tablaMs()); td.add(r.dpllMs()); tv.add(r.verificadorMs());
                if (Double.isFinite(r.tablaBytes())) me.add(r.tablaBytes());
                if (Double.isFinite(r.dpllBytes())) md.add(r.dpllBytes());
                if (Double.isFinite(r.verificadorBytes())) mv.add(r.verificadorBytes());
            }
            if (te.size() != INSTANCIAS) throw new IllegalStateException("Faltan instancias");
            out.add(new Grupo(e, n, INSTANCIAS, m, filas,
                    percentil(te, .5), percentil(te, .25), percentil(te, .75),
                    percentil(td, .5), percentil(tv, .5),
                    me.isEmpty() ? Double.NaN : percentil(me, .5),
                    md.isEmpty() ? Double.NaN : percentil(md, .5),
                    mv.isEmpty() ? Double.NaN : percentil(mv, .5)));
        }
        return out;
    }
    private static double medianaLong(List<Long> xs) {
        List<Double> vals = new ArrayList<>(); for (long x : xs) vals.add((double)x);
        return percentil(vals, .5);
    }
    private static double medianaMemoria(List<Long> xs) {
        return xs.isEmpty() ? Double.NaN : medianaLong(xs);
    }
    private static double percentil(List<Double> xs, double p) {
        List<Double> sorted = new ArrayList<>(xs); Collections.sort(sorted);
        double x = p * (sorted.size() - 1); int lo = (int)x, hi = (int)Math.ceil(x);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (x - lo);
    }
    private static com.sun.management.ThreadMXBean configurarMemoria() {
        try {
            java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
            if (t instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                if (bean.getThreadAllocatedBytes(Thread.currentThread().getId()) >= 0) return bean;
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return null;
    }
    private static long memoria() {
        return MEMORIA == null ? -1 : MEMORIA.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
    private static long diferencia(long a, long b) { return a >= 0 && b >= a ? b-a : -1; }
    private static String numero(double v) {
        return Double.isFinite(v) ? String.format(Locale.US,"%.0f",v) : "";
    }
    private static String largo(long n) { return n < 0 ? "" : Long.toString(n); }
    private static BufferedWriter nuevo(Path p) throws IOException {
        return Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static void linea(BufferedWriter w, String s) throws IOException { w.write(s); w.newLine(); }
    private static void guardarDimacs(FormulaCNF f, Path ruta) throws IOException {
        try (BufferedWriter w = nuevo(ruta)) {
            linea(w, "c x1..xn representan variables 1..n");
            linea(w, "p cnf " + f.variables().size() + " " + f.numeroClausulas());
            for (int[] c : f.clauses()) {
                StringBuilder b = new StringBuilder();
                for (int l : c) b.append(l).append(' ');
                linea(w, b.append('0').toString());
            }
        }
    }
    private static void guardarEntorno(Path archivo) throws IOException {
        List<String> lineas = List.of(
                "SAT_SOLVER | Persona 5 | benchmark tabla de verdad",
                "Java: " + System.getProperty("java.runtime.version"),
                "SO: " + System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "CPU logicas: " + Runtime.getRuntime().availableProcessors(),
                "Semilla base: " + SEMILLA_BASE,
                "Tamanos: " + Arrays.toString(TAMANOS),
                "Escenarios: " + Arrays.toString(Escenario.values()),
                "Instancias/configuracion: " + INSTANCIAS,
                "Calentamiento/instancia: " + CALENTAMIENTO,
                "Repeticiones medidas/instancia: " + REPETICIONES,
                "Metodo memoria: " + (MEMORIA == null ? "NO_DISPONIBLE" : "BYTES_ASIGNADOS_HILO"),
                "Memoria son bytes asignados por hilo, NO RAM pico ni memoria retenida.",
                "Cada fila registra resultados de una misma formula para los tres metodos.",
                "Medianas primero sobre repeticiones, luego sobre las instancias independientes.",
                "Se excluyen generacion CNF, verificacion de correctitud, CSV, HTTP, HTML y dibujos.",
                "Enumeracion produce TODAS las filas; DPLL encuentra una solucion o UNSAT; verificador evalua un solo candidato.",
                "En UNSAT verificar un candidato falso NO demuestra insatisfacibilidad.",
                "SAT: verificador utiliza testigo conocido. UNSAT: verifica asignacion todo-falso.",
                "Enumerador del equipo limita a 12 variables; no extrapolar rendimiento mas alla de ese limite.",
                "Cada tabla usa 2^n filas y conserva en memoria resultados de todas las clausulas.",
                "La estructura/densidad de clausulas afecta los tiempos. No se afirma crecimiento monotono en cada medida.",
                "No se sincroniza GC ni carga del sistema; JIT/GC pueden influir en microbenchmark.",
                "Los grafico PNG son resultados de esta ejecucion y deben mantenerse junto a sus CSV."
        );
        Files.write(archivo, lineas, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static final int W = 1160, H = 680, X0 = 120, Y0 = 140, CW = 950, CH = 390;
    private static void graficar(Path archivo, String titulo, List<Grupo> datos, boolean escenarios) throws IOException {
        List<Serie> series = new ArrayList<>();
        if (escenarios) {
            for (Escenario e : Escenario.values()) {
                double[] v = new double[TAMANOS.length];
                for (int i = 0; i < TAMANOS.length; i++) v[i] = grupo(datos,e,TAMANOS[i]).tablaMs();
                series.add(new Serie(e.name(), e.color, v));
            }
        } else {
            Escenario e = Escenario.SAT_BAJA_DENSIDAD;
            double[] tab = new double[TAMANOS.length], dpll = tab.clone(), ver = tab.clone();
            for (int i = 0; i < TAMANOS.length; i++) {
                Grupo g = grupo(datos,e,TAMANOS[i]);
                tab[i] = g.tablaMs(); dpll[i] = g.dpllMs(); ver[i] = g.verificadorMs();
            }
            series.add(new Serie("Enumeracion completa", new Color(144,76,186), tab));
            series.add(new Serie("DPLL", new Color(39,99,231), dpll));
            series.add(new Serie("Verificar 1 candidato", new Color(15,137,111), ver));
        }
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (Serie s : series) for (double x : s.ms()) if (x > 0 && Double.isFinite(x)) {
            min = Math.min(min, x); max = Math.max(max, x);
        }
        if (!Double.isFinite(min) || max <= 0) throw new IllegalStateException("Datos invalidos para grafica");
        double ymin = Math.floor(Math.log10(min)) - .3;
        double ymax = Math.ceil(Math.log10(max)) + .3;
        if (ymax <= ymin) ymax = ymin + 1;
        BufferedImage img = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);g.fillRect(0,0,W,H);
        g.setColor(new Color(25,39,59));g.setFont(new Font("SansSerif",Font.BOLD,24));g.drawString(titulo,X0,52);
        g.setFont(new Font("SansSerif",Font.PLAIN,15));
        g.drawString("Mediana de 3 formulas, 10 repeticiones por formula; eje vertical logaritmico",X0,81);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));
        for (int t = 0; t <= 5; t++) {
            double log = ymin + (ymax-ymin)*t/5.0;
            int y = Y0+CH - t*CH/5;
            g.setColor(new Color(225,231,238));g.drawLine(X0,y,X0+CW,y);
            g.setColor(new Color(56,67,81));
            g.drawString(String.format(Locale.US,"%.5f",Math.pow(10,log)),32,y+4);
        }
        for (int i=0;i<TAMANOS.length;i++) {
            int x = X0 + i*CW/(TAMANOS.length-1);
            g.drawString(String.valueOf(TAMANOS[i]),x-7,Y0+CH+27);
        }
        g.drawString("Tiempo (ms, escala logaritmica)",22,120);
        g.drawString("Numero de variables (n) y combinaciones (2^n)",W/2-150,H-56);
        for (int i=0;i<TAMANOS.length;i++) {
            int x = X0+i*CW/(TAMANOS.length-1);
            g.setColor(new Color(110,120,130));
            g.drawString(String.valueOf(1 << TAMANOS[i])+" filas",x-22,Y0+CH+47);
        }
        for (Serie s : series) {
            g.setColor(s.color());g.setStroke(new BasicStroke(2.8f));
            int px=-1, py=-1;
            for (int i=0;i<TAMANOS.length;i++) {
                int x=X0+i*CW/(TAMANOS.length-1);
                int y=Y0+CH-(int)Math.round((Math.log10(Math.max(s.ms()[i],1e-12))-ymin)/(ymax-ymin)*CH);
                if (px>=0)g.drawLine(px,py,x,y);
                g.fillOval(x-5,y-5,10,10);px=x;py=y;
            }
        }
        int count = series.size();
        for (int i=0;i<count;i++) {
            Serie s=series.get(i);
            int x= escenarios ? X0+i*250 : X0+i*330;
            g.setColor(s.color());g.fillRect(x,H-98,17,14);
            g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(new Color(45,58,72));
            g.drawString(s.nombre(),x+22,H-85);
        }
        g.dispose();
        if (!ImageIO.write(img,"png",archivo.toFile())) throw new IOException("Error generando grafico: " + archivo);
    }
    private static Grupo grupo(List<Grupo> datos, Escenario e, int n) {
        for (Grupo g : datos) if (g.escenario()==e && g.n()==n) return g;
        throw new IllegalStateException("Sin grupo " + e + "/" + n);
    }
}
