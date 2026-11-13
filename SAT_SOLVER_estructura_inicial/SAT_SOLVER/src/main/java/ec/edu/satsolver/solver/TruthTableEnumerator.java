package ec.edu.satsolver.solver;

import ec.edu.satsolver.model.FormulaCNF;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Genera todas las combinaciones de valores para una formula CNF. */
public final class TruthTableEnumerator {

    // Para no generar tablas demasiado grandes en el navegador.
    private static final int MAX_VARIABLES = 12;

    // Una fila: valores de las variables, resultados de las clausulas y resultado final.
    public record Row(List<Boolean> values,
                      List<Boolean> clausesSatisfied,
                      boolean satisfies) { }


    public record Result(List<String> variables,
                         List<String> clauses,
                         List<Row> rows,
                         int satCount,
                         int unsatCount) { }

    public Result enumerate(FormulaCNF formula) {
        if (formula == null) {
            throw new IllegalArgumentException("La formula no puede ser nula");
        }

        List<String> variables = formula.variables();
        List<int[]> clausulas = formula.clauses();
        int n = variables.size();

        if (n > MAX_VARIABLES) {
            throw new IllegalArgumentException(
                "La tabla admite hasta " + MAX_VARIABLES + " variables (4096 filas)");
        }

        // Textos de las clausulas para las columnas C1, C2, etc.
        List<String> textosClausulas = new ArrayList<>();
        for (int[] clausula : clausulas) {
            StringJoiner texto = new StringJoiner(" | ", "(", ")");
            for (int literal : clausula) {
                String nombre = variables.get(Math.abs(literal) - 1);
                texto.add(literal < 0 ? "!" + nombre : nombre);
            }
            textosClausulas.add(texto.toString());
        }

        List<Row> filas = new ArrayList<>();
        int satisfactorias = 0;
        int total = 1 << n; // 2 elevado al numero de variables.

        for (int numero = 0; numero < total; numero++) {
            List<Boolean> valores = new ArrayList<>();

            // Cada bit representa F o V para una variable.
            for (int i = 0; i < n; i++) {
                boolean valor = ((numero >> (n - 1 - i)) & 1) == 1;
                valores.add(valor);
            }

            List<Boolean> resultadosClausulas = new ArrayList<>();
            boolean cumpleFormula = true;

            // Evaluar TODAS las clausulas, incluso si alguna falla.
            for (int[] clausula : clausulas) {
                boolean cumpleClausula = false;

                for (int literal : clausula) {
                    boolean valor = valores.get(Math.abs(literal) - 1);
                    if (literal < 0) valor = !valor;
                    if (valor) {
                        cumpleClausula = true;
                        break;
                    }
                }

                resultadosClausulas.add(cumpleClausula);
                if (!cumpleClausula) cumpleFormula = false;
            }

            filas.add(new Row(List.copyOf(valores),
                              List.copyOf(resultadosClausulas),
                              cumpleFormula));
            if (cumpleFormula) satisfactorias++;
        }

        return new Result(List.copyOf(variables),
                          List.copyOf(textosClausulas),
                          List.copyOf(filas),
                          satisfactorias,
                          total - satisfactorias);
    }
}
