package ec.edu.satsolver.api;
import com.sun.net.httpserver.*;
import ec.edu.satsolver.parser.CNFParser;
import ec.edu.satsolver.solver.DPLLSolver;
import ec.edu.satsolver.verification.AssignmentVerifier;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Adaptador HTTP: no contiene lógica SAT. */
public final class SatHttpServer {
    private SatHttpServer() {}
    public static void start(int port) throws IOException {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/api/solve", ex -> handle(ex, false));
        server.createContext("/api/verify", ex -> handle(ex, true));
        server.createContext("/", SatHttpServer::staticFile);
        server.start();
        System.out.println("SAT SOLVER disponible en http://localhost:"+port);
    }
    private static void handle(HttpExchange ex,boolean verify) throws IOException {
        if(!ex.getRequestMethod().equals("POST")) {respond(ex,405,"{\"error\":\"Use POST\"}","application/json");return;}
        try {
            byte[] body=ex.getRequestBody().readNBytes(100_001);
            if(body.length>100_000) throw new IllegalArgumentException("Entrada demasiado grande");
            String json=new String(body,StandardCharsets.UTF_8);
            String formula=JsonUtil.formula(json);
            var f=CNFParser.parse(formula);
            if(f.variables().size()>60 || f.clauses().size()>5000) throw new IllegalArgumentException("Límite de esta versión: 60 variables y 5000 cláusulas");
            long start=System.nanoTime();
            String answer;
            if(verify) {
                boolean valid=new AssignmentVerifier().verify(f,JsonUtil.assignmentFrom(json));
                answer="{\"valid\":"+valid+",\"elapsedNanos\":"+(System.nanoTime()-start)+"}";
            } else {
                Optional<Map<String,Boolean>> solved=new DPLLSolver().solve(f);
                long elapsed=System.nanoTime()-start;
                answer="{\"satisfiable\":"+solved.isPresent()+",\"assignment\":"+solved.map(JsonUtil::assignment).orElse("null")+",\"elapsedNanos\":"+elapsed+"}";
            }
            respond(ex,200,answer,"application/json");
        }catch(IllegalArgumentException e){respond(ex,400,"{\"error\":"+JsonUtil.quote(e.getMessage())+"}","application/json");}
        catch(Exception e){respond(ex,500,"{\"error\":\"Error interno del servidor\"}","application/json");}
    }
    private static void staticFile(HttpExchange ex) throws IOException {
        if(!ex.getRequestMethod().equals("GET")){respond(ex,405,"Method not allowed","text/plain");return;}
        String raw=ex.getRequestURI().getPath();
        if(raw.equals("/"))raw="/index.html";
        Path root=Paths.get("web").toAbsolutePath().normalize();
        Path file=root.resolve(raw.substring(1)).normalize();
        if(!file.startsWith(root)||!Files.isRegularFile(file)){respond(ex,404,"No encontrado","text/plain");return;}
        
        String type;

        if (raw.endsWith(".html")) {
            type = "text/html";

        } else if (raw.endsWith(".css")) {
            type = "text/css";

        } else if (raw.endsWith(".js")) {
            type = "text/javascript";

        } else if (raw.endsWith(".svg")) {
            type = "image/svg+xml";

        } else {
            type = "application/octet-stream";
        }

        byte[] bytes=Files.readAllBytes(file);ex.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");ex.sendResponseHeaders(200,bytes.length);try(OutputStream os=ex.getResponseBody()){os.write(bytes);}
    }
    private static void respond(HttpExchange ex,int code,String body,String type)throws IOException{
        byte[] b=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");ex.sendResponseHeaders(code,b.length);try(OutputStream out=ex.getResponseBody()){out.write(b);}
    }
}
