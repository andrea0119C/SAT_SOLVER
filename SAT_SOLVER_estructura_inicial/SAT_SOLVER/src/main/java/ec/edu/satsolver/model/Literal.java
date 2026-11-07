package ec.edu.satsolver.model;


/** Literal booleano: variable y signo, sin evaluar su valor de verdad. */
public record Literal(String variable, boolean negado) {
    public Literal {
        if (variable == null || !variable.matches("[A-Za-z][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("Nombre de variable inválido: " + variable);
    }
    @Override public String toString() { return (negado ? "!" : "") + variable; }
}

