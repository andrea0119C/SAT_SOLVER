
package ec.edu.satsolver.parser;

import ec.edu.satsolver.model.FormulaCNF;
import java.util.*;
import java.util.regex.*;

public final class CNFParser {

    private static final Pattern NAME =
        Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final String input;
    private int pos;

    private final Map<String, Integer> symbols =
        new LinkedHashMap<>();

    private CNFParser(String input) {
        this.input = input == null ? "" : input;
    }

    public static FormulaCNF parse(String input) {
        return new CNFParser(input).parseFormula();
    }

    private FormulaCNF parseFormula() {

        List<int[]> clauses = new ArrayList<>();

        skip();

        if (pos == input.length()) {
            throw error("La fórmula está vacía");
        }

        clauses.add(parseClause());
        skip();

        while (take('&')) {
            clauses.add(parseClause());
            skip();
        }

        if (pos != input.length()) {
            throw error(
                "Se esperaba '&' o el final de la fórmula"
            );
        }

        return new FormulaCNF(
            new ArrayList<>(symbols.keySet()),
            clauses
        );
    }

    private int[] parseClause() {

        skip();

        if (!take('(')) {
            throw error(
                "Cada cláusula debe comenzar con '('"
            );
        }

        List<Integer> lits = new ArrayList<>();

        lits.add(parseLiteral());
        skip();

        while (take('|')) {
            lits.add(parseLiteral());
        }

        if (!take(')')) {
            throw error(
                "Falta ')' o hay un operador incorrecto"
            );
        }

        return lits.stream()
            .mapToInt(Integer::intValue)
            .toArray();
    }

    private int parseLiteral() {

        skip();

        boolean negated = take('!');
        skip();

        Matcher m = NAME.matcher(input);
        m.region(pos, input.length());

        if (!m.lookingAt()) {
            throw error("Se esperaba una variable");
        }

        String name = m.group();
        pos = m.end();

        int index = symbols.computeIfAbsent(
            name,
            k -> symbols.size() + 1
        );

        return negated ? -index : index;
    }

    private boolean take(char c) {

        skip();

        if (pos < input.length()
                && input.charAt(pos) == c) {

            pos++;
            return true;
        }

        return false;
    }

    private void skip() {

        while (pos < input.length()
                && Character.isWhitespace(
                    input.charAt(pos))) {

            pos++;
        }
    }

    private IllegalArgumentException error(
        String message
    ) {
        return new IllegalArgumentException(
            message + " (posición " + (pos + 1) + ")"
        );
    }
}
