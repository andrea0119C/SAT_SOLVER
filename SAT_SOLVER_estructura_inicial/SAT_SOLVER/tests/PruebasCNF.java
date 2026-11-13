
import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.parser.CNFParser;
import ec.edu.satsolver.solver.TruthTableEnumerator;

import java.util.Arrays;
import java.util.List;

public class PruebasCNF {

    private static int aprobadas = 0;

    private static void comprobar(
        String nombre,
        boolean condicion
    ) {
        if (!condicion) {
            throw new AssertionError(
                "FALLO: " + nombre
            );
        }

        System.out.println("APROBADA: " + nombre);
        aprobadas++;
    }

    private static void invalida(String entrada) {

        boolean rechazada = false;

        try {
            CNFParser.parse(entrada);
        } catch (IllegalArgumentException e) {
            rechazada = true;
        }

        comprobar(
            "Rechaza: " + String.valueOf(entrada),
            rechazada
        );
    }

    public static void main(String[] args) {

        // 1. Validar variables y clausulas.

        FormulaCNF f = CNFParser.parse(
            " (x | !y) & (!x | z) & (y) "
        );

        comprobar(
            "Orden de variables x, y, z",
            f.variables().equals(
                List.of("x", "y", "z")
            )
        );

        comprobar(
            "Cuenta tres variables",
            f.numeroVariables() == 3
        );

        comprobar(
            "Cuenta tres clausulas",
            f.numeroClausulas() == 3
        );

        comprobar(
            "Cuenta cinco literales",
            f.numeroLiterales() == 5
        );

        List<int[]> c = f.clauses();

        comprobar(
            "Clausula 1 conserva negacion",
            Arrays.equals(
                c.get(0),
                new int[]{1, -2}
            )
        );

        comprobar(
            "Clausula 2 usa los IDs correctos",
            Arrays.equals(
                c.get(1),
                new int[]{-1, 3}
            )
        );

        comprobar(
            "Clausula 3 unitaria",
            Arrays.equals(
                c.get(2),
                new int[]{2}
            )
        );

        comprobar(
            "Clausulas legibles",
            f.clausulas().toString().equals(
                "[(x | !y), (!x | z), (y)]"
            )
        );

        // 2. Validar copias defensivas.

        c.get(0)[0] = -3;
        c.clear();

        comprobar(
            "Una modificacion externa no altera la formula",
            Arrays.equals(
                f.clauses().get(0),
                new int[]{1, -2}
            )
            && f.numeroClausulas() == 3
        );

        // 3. Validar variables repetidas.

        FormulaCNF repetidas = CNFParser.parse(
            "(a | a) & (!a | b)"
        );

        comprobar(
            "Una variable repetida no se duplica",
            repetidas.variables().equals(
                List.of("a", "b")
            )
        );

        // 4. Validar nombres y espacios.

        invalida("(x1 | !_y)");

        comprobar(
            "Acepta nombres validos largos",
            CNFParser.parse(
                "(x1 | !y_2)"
            ).numeroVariables() == 2
        );

        comprobar(
            "Acepta espacios y saltos de linea",
            CNFParser.parse(
                "\n ( x ) \t & ( !x | y ) "
            ).numeroClausulas() == 2
        );

        comprobar(
            "Una clausula sola es valida",
            CNFParser.parse(
                "(x)"
            ).numeroLiterales() == 1
        );

        // 5. Validar formulas incorrectas.

        invalida(null);
        invalida("");
        invalida("   ");
        invalida("x & y");
        invalida("(x | )");
        invalida("(x) &");
        invalida("(x) (y)");
        invalida("(x && y)");
        invalida("()");
        invalida("(!)");
        invalida("(x | y");
        invalida("(x) basura");
        invalida("(1x)");

        // 6. Integracion con Persona 3.

        var resultado =
            new TruthTableEnumerator().enumerate(
                CNFParser.parse(
                    "(x | y) & (!x | y)"
                )
            );

        comprobar(
            "Enumerador recibe dos variables",
            resultado.variables().equals(
                List.of("x", "y")
            )
        );

        comprobar(
            "Enumerador recibe dos clausulas",
            resultado.clauses().equals(
                List.of(
                    "(x | y)",
                    "(!x | y)"
                )
            )
        );

        comprobar(
            "Se generan cuatro asignaciones",
            resultado.rows().size() == 4
        );

        comprobar(
            "Dos asignaciones satisfactorias",
            resultado.satCount() == 2
        );

        comprobar(
            "Cada fila evalua ambas clausulas",
            resultado.rows().stream().allMatch(
                r -> r.clausesSatisfied().size() == 2
            )
        );

        System.out.println(
            "RESULTADO: " + aprobadas
            + " pruebas aprobadas."
        );
    }
}
