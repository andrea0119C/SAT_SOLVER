package ec.edu.satsolver.model;

import java.util.*;

/** Fórmula CNF: conjunción de cláusulas. Positivo = variable; negativo = negación. */
public final class FormulaCNF {
    private final List<String> variables;
    private final List<int[]> clauses;

    public FormulaCNF(List<String> variables, List<int[]> clauses) {
        Objects.requireNonNull(variables, "La lista de variables es obligatoria");
        Objects.requireNonNull(clauses, "La lista de cláusulas es obligatoria");
        if (clauses.isEmpty()) throw new IllegalArgumentException("La fórmula debe contener cláusulas");
        this.variables = List.copyOf(variables);
        if (new HashSet<>(variables).size() != variables.size())
            throw new IllegalArgumentException("Hay variables duplicadas en la tabla de símbolos");
        for (String variable : variables) new Literal(variable, false);
        List<int[]> copy = new ArrayList<>();
        for (int[] clause : clauses) {
            if (clause == null || clause.length == 0)
                throw new IllegalArgumentException("Una cláusula no puede estar vacía");
            for (int lit : clause) {
                if (lit == 0 || (long) Math.abs((long) lit) > variables.size())
                    throw new IllegalArgumentException("Literal fuera del rango de variables: " + lit);
            }
            copy.add(clause.clone());
        }
        this.clauses = Collections.unmodifiableList(copy);
    }

    public List<String> variables() { return variables; }
    /** Copias defensivas: el solver no puede alterar la fórmula original. */
    public List<int[]> clauses() {
        List<int[]> result = new ArrayList<>();
        for (int[] clause : clauses) result.add(clause.clone());
        return result;
    }
    /** Vista explicativa en objetos, útil para mostrar las estructuras sin cambiar DPLL. */
    public List<Clausula> clausulas() {
        List<Clausula> result = new ArrayList<>();
        for (int[] clause : clauses) {
            List<Literal> literals = new ArrayList<>();
            for (int signedId : clause)
                literals.add(new Literal(variables.get(Math.abs(signedId) - 1), signedId < 0));
            result.add(new Clausula(literals));
        }
        return List.copyOf(result);
    }
    public int numeroClausulas() { return clauses.size(); }
    public int numeroLiterales() {
        int count = 0;
        for (int[] clause : clauses) count += clause.length;
        return count;
    }
}
