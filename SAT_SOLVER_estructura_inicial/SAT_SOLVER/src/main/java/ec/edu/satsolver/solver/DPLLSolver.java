
package ec.edu.satsolver.solver;

import ec.edu.satsolver.model.FormulaCNF;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resuelve formulas SAT en CNF utilizando el algoritmo DPLL.
 * Aplica propagacion unitaria y busqueda recursiva (backtracking).
 */
public final class DPLLSolver {

    // Metodo principal: determina si una formula es SAT o UNSAT.
    public Optional<Map<String, Boolean>> solve(FormulaCNF formula) {

        if (formula == null) {
            throw new IllegalArgumentException("La formula no puede ser nula");
        }

        // 0 = sin asignar, 1 = verdadero, -1 = falso.
        int[] asignacion = new int[formula.variables().size() + 1];

        // Ejecutamos DPLL para buscar una solucion.
        int[] solucion = dpll(formula.clauses(), asignacion);

        if (solucion == null) {
            return Optional.empty(); // UNSAT: no existe solucion.
        }

        // Convertimos los resultados a un mapa de variables.
        Map<String, Boolean> resultado = new LinkedHashMap<>();

        for (int i = 1; i < solucion.length; i++) {
            // Las variables libres se completan con true.
            resultado.put(formula.variables().get(i - 1),
                          solucion[i] != -1);
        }

        return Optional.of(resultado); // SAT.
    }

    // Algoritmo DPLL con propagacion unitaria y backtracking.
    private int[] dpll(List<int[]> clausulas, int[] asignacion) {

        // PASO 1: PROPAGACION UNITARIA.
        // Deducimos los valores obligatorios de las variables.
        while (true) {

            boolean huboCambios = false;
            boolean todasSatisfechas = true;

            for (int[] clausula : clausulas) {

                boolean satisfecha = false;
                int pendientes = 0;
                int ultimoPendiente = 0;

                // Revisamos cada literal de la clausula.
                for (int literal : clausula) {

                    int valor = asignacion[Math.abs(literal)];

                    // Comprobamos si el literal es verdadero.
                    if ((literal > 0 && valor == 1) ||
                        (literal < 0 && valor == -1)) {

                        satisfecha = true;
                        break;
                    }

                    if (valor == 0) {
                        pendientes++;
                        ultimoPendiente = literal;
                    }
                }

                // Si la clausula ya se cumple, continuamos.
                if (satisfecha) {
                    continue;
                }

                todasSatisfechas = false;

                // Ningun literal puede satisfacer la clausula.
                if (pendientes == 0) {
                    return null; // Contradiccion.
                }

                // Solo queda un literal: valor obligatorio.
                if (pendientes == 1) {

                    int variable = Math.abs(ultimoPendiente);

                    asignacion[variable] =
                        ultimoPendiente > 0 ? 1 : -1;

                    huboCambios = true;
                }
            }

            // Todas las clausulas estan satisfechas.
            if (todasSatisfechas) {
                return asignacion;
            }

            // No hay mas valores obligatorios.
            if (!huboCambios) {
                break;
            }
        }

        // PASO 2: ELEGIR UNA VARIABLE LIBRE.
        int variableLibre = 0;

        for (int i = 1; i < asignacion.length; i++) {

            if (asignacion[i] == 0) {
                variableLibre = i;
                break;
            }
        }

        if (variableLibre == 0) {
            return null;
        }

        // PASO 3: PROBAR VERDADERO.
        // Clonamos para no modificar otras ramas.
        int[] intentoVerdadero = asignacion.clone();
        intentoVerdadero[variableLibre] = 1;

        int[] solucion = dpll(clausulas, intentoVerdadero);

        if (solucion != null) {
            return solucion;
        }

        // PASO 4: BACKTRACKING.
        // Si verdadero falla, probamos falso.
        int[] intentoFalso = asignacion.clone();
        intentoFalso[variableLibre] = -1;

        return dpll(clausulas, intentoFalso);
    }
}
