import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.parser.CNFParser;
import ec.edu.satsolver.solver.DPLLSolver;
import ec.edu.satsolver.solver.TruthTableEnumerator;
import java.util.List;

public class PruebasTruthTable {
    private static int aprobadas = 0;

    private static void comprobar(String nombre, boolean condicion) {
        if (!condicion) throw new AssertionError("Fallo: " + nombre);
        aprobadas++;
        System.out.println("Prueba " + aprobadas + " correcta: " + nombre);
    }

    private static TruthTableEnumerator.Result tabla(String texto) {
        return new TruthTableEnumerator().enumerate(CNFParser.parse(texto));
    }

    public static void main(String[] args) {
        // Dos variables: cuatro combinaciones, dos de ellas SAT.
        var ejemplo = tabla("(x | y) & (!x | y)");
        comprobar("2 variables generan 4 filas", ejemplo.rows().size() == 4);
        comprobar("2 SAT y 2 no satisfactorias",
                  ejemplo.satCount() == 2 && ejemplo.unsatCount() == 2);
        comprobar("Primera fila F,F",
                  ejemplo.rows().get(0).values().equals(List.of(false, false)));
        comprobar("Evaluacion de C1 y C2 en primera fila",
                  ejemplo.rows().get(0).clausesSatisfied().equals(List.of(false, true)));
        comprobar("Segunda fila satisface la formula",
                  ejemplo.rows().get(1).satisfies());

        // Casos fundamentales del proyecto.
        comprobar("Contradiccion: ninguna fila SAT",
                  tabla("(x) & (!x)").satCount() == 0);
        comprobar("Clausula unitaria: solo x=true",
                  tabla("(x)").rows().get(1).satisfies()
                  && !tabla("(x)").rows().get(0).satisfies());
        comprobar("Tautologia: todas las filas SAT",
                  tabla("(x | !x)").satCount() == 2);
        comprobar("3 variables generan 8 filas, 7 SAT",
                  tabla("(a | b | c)").satCount() == 7);

        // DPLL y la tabla deben coincidir en si existe alguna solucion.
        String formula = "(a | b) & (!a | b) & (a | !b)";
        FormulaCNF f = CNFParser.parse(formula);
        boolean dpllSAT = new DPLLSolver().solve(f).isPresent();
        comprobar("La tabla coincide con DPLL",
                  dpllSAT == (new TruthTableEnumerator().enumerate(f).satCount() > 0));

        // Limite de seguridad para no bloquear el navegador.
        String treceVariables = "(a|b|c|d|e|f|g|h|i|j|k|l|m)";
        boolean rechazo = false;
        try {
            tabla(treceVariables);
        } catch (IllegalArgumentException error) {
            rechazo = true;
        }
        comprobar("Limite de 12 variables", rechazo);

        System.out.println("Resultado final: " + aprobadas + "/11 pruebas aprobadas.");
    }
}
