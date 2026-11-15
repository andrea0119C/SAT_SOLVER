package ec.edu.satsolver.api;

import com.sun.net.httpserver.HttpExchange;
import ec.edu.satsolver.model.FormulaCNF;
import ec.edu.satsolver.parser.CNFParser;
import ec.edu.satsolver.solver.TruthTableEnumerator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** P4: adapta la enumeracion de P3 a una respuesta HTTP JSON. */
public final class TruthTableEndpoint {
    private static final int MAX_BODY_BYTES = 100_000;
    private static final long MAX_CELLS = 500_000L;

    private TruthTableEndpoint() { }

    public static void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"Use POST\"}");
            return;
        }

        try {
            byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Entrada demasiado grande");
            }

            String request = new String(bytes, StandardCharsets.UTF_8);
            String formulaText = JsonUtil.formula(request);
            FormulaCNF formula = CNFParser.parse(formulaText);

            // El enumerador de P3 admite hasta 12 variables.
            int n = formula.variables().size();
            if (n > 12) {
                throw new IllegalArgumentException(
                    "La tabla admite hasta 12 variables (4096 filas)");
            }
            // Evita generar una respuesta gigantesca en el navegador.
            long cells = (1L << n) * Math.max(1, formula.clauses().size());
            if (cells > MAX_CELLS) {
                throw new IllegalArgumentException(
                    "La formula produce una tabla demasiado grande para mostrar");
            }

            TruthTableEnumerator.Result result =
                new TruthTableEnumerator().enumerate(formula);

            respond(exchange, 200, toJson(result));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400,
                "{\"error\":" + JsonUtil.quote(e.getMessage()) + "}");
        } catch (Exception e) {
            respond(exchange, 500,
                "{\"error\":\"Error interno del servidor\"}");
        }
    }

    /** Convierte los registros de P3 al contrato JSON para la interfaz. */
    private static String toJson(TruthTableEnumerator.Result result) {
        StringBuilder json = new StringBuilder();
        json.append("{\"variables\":");
        appendStrings(json, result.variables());
        json.append(",\"clauses\":");
        appendStrings(json, result.clauses());
        json.append(",\"rows\":[");

        for (int i = 0; i < result.rows().size(); i++) {
            if (i > 0) json.append(',');
            TruthTableEnumerator.Row row = result.rows().get(i);
            json.append("{\"values\":");
            appendBooleans(json, row.values());
            json.append(",\"clausesSatisfied\":");
            appendBooleans(json, row.clausesSatisfied());
            json.append(",\"satisfies\":").append(row.satisfies());
            json.append('}');
        }

        json.append("],\"satCount\":").append(result.satCount());
        json.append(",\"unsatCount\":").append(result.unsatCount());
        json.append(",\"totalRows\":").append(result.rows().size());
        json.append(",\"satisfiable\":").append(result.satCount() > 0);
        json.append('}');
        return json.toString();
    }

    private static void appendStrings(StringBuilder json, List<String> values) {
        json.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) json.append(',');
            json.append(JsonUtil.quote(values.get(i)));
        }
        json.append(']');
    }

    private static void appendBooleans(StringBuilder json, List<Boolean> values) {
        json.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) json.append(',');
            json.append(values.get(i));
        }
        json.append(']');
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}