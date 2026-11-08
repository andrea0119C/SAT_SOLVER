import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;

/**
 * Persona 4 - pruebas complementarias de integracion (11 de noviembre).
 * Ejecutar con SAT SOLVER encendido en http://localhost:8080.
 * No requiere bibliotecas externas.
 */
public final class PruebasRobustez {
    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).build();
    private static int aprobadas = 0;
    private static String base;

    private static HttpResponse<String> enviar(String metodo, String ruta, String cuerpo)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(15));
        if ("POST".equals(metodo)) {
            b.header("Content-Type", "application/json; charset=utf-8")
             .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        } else {
            b.GET();
        }
        return CLIENTE.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void comprobar(String nombre, HttpResponse<String> respuesta,
                                  int estado, String contenido) {
        if (respuesta.statusCode() != estado ||
                !respuesta.body().contains(contenido)) {
            throw new AssertionError(nombre + ": se esperaba HTTP " + estado
                    + " con '" + contenido + "', pero se obtuvo HTTP "
                    + respuesta.statusCode() + ": "
                    + respuesta.body().substring(0, Math.min(200, respuesta.body().length())));
        }
        aprobadas++;
        System.out.println("OK [" + aprobadas + "] " + nombre
                + " (HTTP " + respuesta.statusCode() + ")");
    }

    private static void probar(String nombre, String metodo, String ruta,
                               String cuerpo, int estado, String contenido) throws Exception {
        comprobar(nombre, enviar(metodo, ruta, cuerpo), estado, contenido);
    }

    public static void main(String[] args) throws Exception {
        int puerto = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        base = "http://127.0.0.1:" + puerto;

        probar("Literal negado: asignacion valida", "POST", "/api/verify",
                "{\"formula\":\"(!x1)\",\"assignment\":{\"x1\":false}}",
                200, "\"valid\":true");
        probar("Literal negado: asignacion no valida", "POST", "/api/verify",
                "{\"formula\":\"(!x1)\",\"assignment\":{\"x1\":true}}",
                200, "\"valid\":false");
        probar("Rechaza booleano como texto", "POST", "/api/verify",
                "{\"formula\":\"(x1)\",\"assignment\":{\"x1\":\"true\"}}",
                400, "error");
        // Este caso falla antes de aplicar la correccion pequena en JsonUtil.java.
        probar("Rechaza coma final JSON", "POST", "/api/verify",
                "{\"formula\":\"(x1)\",\"assignment\":{\"x1\":true,}}",
                400, "error");
        probar("Rechaza variable repetida", "POST", "/api/verify",
                "{\"formula\":\"(x1)\",\"assignment\":{\"x1\":true,\"x1\":false}}",
                400, "error");
        probar("Rechaza formula vacia", "POST", "/api/solve",
                "{\"formula\":\"\"}", 400, "error");
        probar("Rechaza asignacion vacia", "POST", "/api/verify",
                "{\"formula\":\"(x1)\",\"assignment\":{}}",
                400, "Faltan variables");

        StringJoiner variables = new StringJoiner(" | ", "(", ")");
        for (int i = 1; i <= 61; i++) variables.add("x" + i);
        probar("Rechaza mas de 60 variables", "POST", "/api/solve",
                "{\"formula\":\"" + variables + "\"}",
                400, "Límite");
        probar("Rechaza solicitudes mayores a 100 KB", "POST", "/api/solve",
                "{\"formula\":\"(x1)\",\"padding\":\"" + " ".repeat(100_050) + "\"}",
                400, "Entrada demasiado grande");
        probar("Panel web disponible", "GET", "/panel.html", null,
                200, "Verificar asignación");
        probar("JavaScript del panel disponible", "GET", "/js/app.js", null,
                200, "enviarSolicitud");
        probar("GET no permitido en verificacion", "GET", "/api/verify", null,
                405, "Use POST");

        HttpResponse<String> sat = enviar("POST", "/api/solve",
                "{\"formula\":\"(x1)\"}");
        if (sat.statusCode() != 200 ||
                !sat.body().contains("\"satisfiable\":true") ||
                !sat.body().contains("\"x1\":true")) {
            throw new AssertionError("El solver debe devolver un certificado para (x1): "
                    + sat.body());
        }
        comprobar("Certificado DPLL aceptado por API verify",
                enviar("POST", "/api/verify",
                        "{\"formula\":\"(x1)\",\"assignment\":{\"x1\":true}}"),
                200, "\"valid\":true");

        System.out.println("\nRESULTADO: " + aprobadas + "/13 pruebas aprobadas.");
    }
}
