
import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.parser.CNFParser;
import ec.edu.satsolver.solver.DPLLSolver;
import ec.edu.satsolver.verification.AssignmentVerifier;
import java.util.Map;
import java.util.Optional;

public class PruebasDPLL {

    private static int aprobadas = 0;

    // Comprueba si el algoritmo devuelve SAT o UNSAT.
    private static void probar(String entrada, boolean esperado) {

        FormulaCNF formula = CNFParser.parse(entrada);
        DPLLSolver solver = new DPLLSolver();

        Optional<Map<String, Boolean>> resultado =
                solver.solve(formula);

        // Comprobamos que el resultado sea correcto.
        if (resultado.isPresent() != esperado) {
            throw new AssertionError(
                "Resultado incorrecto: " + entrada);
        }

        // Si es SAT, verificamos la asignacion encontrada.
        if (resultado.isPresent()) {
            AssignmentVerifier verificador =
                    new AssignmentVerifier();

            if (!verificador.verify(formula, resultado.get())) {
                throw new AssertionError(
                    "Asignacion incorrecta: " + entrada);
            }
        }

        aprobadas++;
        System.out.println("Prueba " + aprobadas + " correcta: "
                + (esperado ? "SAT" : "UNSAT"));
    }

    public static void main(String[] args) {

        // Pruebas SAT.
        probar("(x)", true);
        probar("(!x)", true);
        probar("(a | b) & (!a | b)", true);
        probar("(x | !x)", true);
        probar("(a | a)", true);
        probar("(x | y) & (!x | y) & (x | !y)", true);
        probar("(p | q | r) & (!p | q) & (!q | r)", true);

        // Pruebas UNSAT.
        probar("(x) & (!x)", false);
        probar("(x | x) & (!x | !x)", false);
        probar("(a | b) & (!a | b) & (a | !b) & (!a | !b)", false);

        System.out.println("Resultado final: "
                + aprobadas + "/10 pruebas aprobadas.");
    }
}

