package ec.edu.satsolver.model;
import java.util.List;


public record Clausula(List<Literal> literales) {
    public Clausula {
        if (literales == null || literales.isEmpty())
            throw new IllegalArgumentException("Una cláusula debe contener al menos un literal");
        literales = List.copyOf(literales);
    }
    @Override public String toString() {
        return "(" + String.join(" | ", literales.stream().map(Literal::toString).toList()) + ")";
    }
}
