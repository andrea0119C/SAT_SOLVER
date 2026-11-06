package ec.edu.satsolver.model;
import java.util.*;
/** Fórmula CNF: conjunción de cláusulas; entero positivo = variable, negativo = negación. */
public final class FormulaCNF {
    private final List<String> variables;
    private final List<int[]> clauses;
    public FormulaCNF(List<String> variables, List<int[]> clauses) {
        this.variables = List.copyOf(variables);
        List<int[]> copied = new ArrayList<>();
        for (int[] c : clauses) copied.add(c.clone());
        this.clauses = Collections.unmodifiableList(copied);
    }
    public List<String> variables() { return variables; }
    public List<int[]> clauses() { List<int[]> result = new ArrayList<>(); for(int[] c:clauses) result.add(c.clone()); return result; }
}
