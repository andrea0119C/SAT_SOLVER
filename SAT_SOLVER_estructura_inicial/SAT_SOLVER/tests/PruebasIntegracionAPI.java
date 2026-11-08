import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Pruebas de integracion web-Java para Persona 4.
 * Antes de ejecutarlas, iniciar ec.edu.satsolver.Main en otra terminal.
 */
public final class PruebasIntegracionAPI {
    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private static int aprobadas = 0;
    private static String base;

    private static void probar(String titulo, String metodo, String ruta,
                               String json, int estadoEsperado, String textoEsperado)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(10));
        if ("POST".equals(metodo)) {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.GET();
        }
        HttpResponse<String> respuesta = CLIENTE.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() != estadoEsperado ||
                !respuesta.body().contains(textoEsperado)) {
            throw new AssertionError(titulo + ": esperado HTTP " + estadoEsperado +
                    " y texto " + textoEsperado + "; obtenido HTTP " +
                    respuesta.statusCode() + "\n" + respuesta.body());
        }
        aprobadas++;
        System.out.println("OK [" + aprobadas + "] " + titulo + " (HTTP " +
                respuesta.statusCode() + ")");
    }

    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        base = "http://127.0.0.1:" + puerto;
        try {
            probar("Resolver SAT", "POST", "/api/solve",
                    "{\"formula\":\"(x1 | !x2) & (x2)\"}", 200, "\"satisfiable\":true");
            probar("Resolver UNSAT", "POST", "/api/solve",
                    "{\"formula\":\"(x1) & (!x1)\"}", 200, "\"satisfiable\":false");
            probar("Certificado valido", "POST", "/api/verify",
                    "{\"formula\":\"(x1 | x2)\",\"assignment\":{\"x1\":false,\"x2\":true}}",
                    200, "\"valid\":true");
            probar("Certificado incorrecto", "POST", "/api/verify",
                    "{\"formula\":\"(x1 | x2)\",\"assignment\":{\"x1\":false,\"x2\":false}}",
                    200, "\"valid\":false");
            probar("Asignacion incompleta: error 400", "POST", "/api/verify",
                    "{\"formula\":\"(x1 | x2)\",\"assignment\":{\"x1\":true}}",
                    400, "Faltan variables");
            probar("Formula mal formada: error 400", "POST", "/api/solve",
                    "{\"formula\":\"(x1 | )\"}", 400, "error");
            probar("Falta formula: error 400", "POST", "/api/solve",
                    "{\"assignment\":{}}", 400, "error");
            probar("Metodo incorrecto: error 405", "GET", "/api/solve",
                    null, 405, "Use POST");
            probar("Pagina principal: HTTP 200", "GET", "/",
                    null, 200, "SAT SOLVER");
            probar("Archivo inexistente: error 404", "GET", "/no-existe.js",
                    null, 404, "No encontrado");
        } catch (java.net.ConnectException ex) {
            System.err.println("No se pudo conectar con " + base +
                    ". Inicia el servidor Java en otra terminal.");
            throw ex;
        }
        System.out.println("\nRESULTADO: " + aprobadas + "/10 pruebas aprobadas.");
    }
}
