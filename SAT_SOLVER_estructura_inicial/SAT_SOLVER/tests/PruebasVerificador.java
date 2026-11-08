import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.parser.CNFParser;
import ec.edu.satsolver.solver.DPLLSolver;
import ec.edu.satsolver.verification.AssignmentVerifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pruebas de la Persona 4: verificacion de certificados SAT.
 * No depende de JUnit ni modifica los modulos de otras personas.
 */
public final class PruebasVerificador {
    private static int aprobadas = 0;
    private static final AssignmentVerifier VERIFICADOR = new AssignmentVerifier();

    private static void probar(String titulo, String texto,
                                Map<String, Boolean> asignacion, boolean esperado) {
        FormulaCNF formula = CNFParser.parse(texto);
        boolean resultado = VERIFICADOR.verify(formula, asignacion);
        if (resultado != esperado) {
            throw new AssertionError(titulo + ": se esperaba " + esperado +
                    ", pero se obtuvo " + resultado);
        }
        aprobadas++;
        System.out.println("OK [" + aprobadas + "] " + titulo);
    }

    private static void probarError(String titulo, String texto,
                                    Map<String, Boolean> asignacion) {
        FormulaCNF formula = CNFParser.parse(texto);
        try {
            VERIFICADOR.verify(formula, asignacion);
            throw new AssertionError(titulo + ": se esperaba IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            aprobadas++;
            System.out.println("OK [" + aprobadas + "] " + titulo + " - " + ex.getMessage());
        }
    }

    private static void probarIntegracionDPLL() {
        FormulaCNF formula = CNFParser.parse("(a | b) & (!a | b)");
        Optional<Map<String, Boolean>> solucion = new DPLLSolver().solve(formula);
        if (solucion.isEmpty() || !VERIFICADOR.verify(formula, solucion.get())) {
            throw new AssertionError("DPLL debe retornar una asignacion que el verificador acepte");
        }
        aprobadas++;
        System.out.println("OK [" + aprobadas + "] integracion DPLL y verificador");
    }

    private static void probarDPLLInsatisfacible() {
        FormulaCNF formula = CNFParser.parse("(a) & (!a)");
        if (new DPLLSolver().solve(formula).isPresent()) {
            throw new AssertionError("La formula contradictoria debe ser UNSAT");
        }
        aprobadas++;
        System.out.println("OK [" + aprobadas + "] DPLL identifica UNSAT");
    }

    public static void main(String[] args) {
        probar("Tres variables: certificado valido",
                "(x1 | x2) & (!x1 | x3)",
                Map.of("x1", true, "x2", false, "x3", true), true);

        probar("Primera clausula falsa",
                "(x1 | x2) & (!x1 | x3)",
                Map.of("x1", false, "x2", false, "x3", true), false);

        probar("Clausula unitaria verdadera", "(x1)",
                Map.of("x1", true), true);

        probar("Literal negado falso", "(!x1)",
                Map.of("x1", true), false);

        probar("Tautologia", "(x1 | !x1)",
                Map.of("x1", false), true);

        probar("Contradiccion: ninguna asignacion la satisface",
                "(x1) & (!x1)", Map.of("x1", true), false);

        probar("Disyuncion satisfecha por segunda variable",
                "(x1 | x2)", Map.of("x1", false, "x2", true), true);

        probarError("Asignacion incompleta", "(x1 | x2)",
                Map.of("x1", true));

        Map<String, Boolean> conNulo = new LinkedHashMap<>();
        conNulo.put("x1", null);
        probarError("Valor no booleano", "(x1)", conNulo);

        probarIntegracionDPLL();
        probarDPLLInsatisfacible();
        System.out.println("\nRESULTADO: " + aprobadas + "/11 pruebas aprobadas.");
    }
}
