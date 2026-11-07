
import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.parser.CNFParser;

public class PruebasCNF {
    private static int aprobadas = 0;

    private static void probarValida(
            String entrada, int clausulas, int literales) {

        FormulaCNF formula = CNFParser.parse(entrada);

        if (formula.clauses().size() != clausulas ||
            formula.clauses().get(0).length != literales) {
            throw new AssertionError(
                "Estructura incorrecta: " + entrada);
        }

        System.out.println("APROBADA: formula valida " + entrada);
        aprobadas++;
    }

    private static void probarInvalida(String entrada) {
        try {
            CNFParser.parse(entrada);
            throw new AssertionError(
                "Debio rechazarse: " + entrada);
        } catch (IllegalArgumentException ex) {
            System.out.println(
                "APROBADA: entrada rechazada " +
                entrada + " - " + ex.getMessage());
            aprobadas++;
        }
    }

    public static void main(String[] args) {

        // Prueba 3: Clausula unitaria
        probarValida("(x)", 1, 1);

        // Prueba 5: Falta un literal
        probarInvalida("(x | )");

        // Prueba 6: Faltan parentesis
        probarInvalida("x & y");

        System.out.println(
            "Resultado: " + aprobadas +
            "/3 pruebas aprobadas.");
    }
}
