package ec.edu.satsolver.api;
import java.util.*;
import java.util.regex.*;
/** Codificación/lectura del subconjunto JSON requerido por esta API educativa. */
public final class JsonUtil {
    private JsonUtil() {}
    public static String escape(String s) {return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");}
    public static String quote(String s) {return "\""+escape(s)+"\"";}
    public static String assignment(Map<String,Boolean> map) {
        StringJoiner j = new StringJoiner(",","{","}");
        for(var e:map.entrySet()) j.add(quote(e.getKey())+":"+e.getValue());
        return j.toString();
    }
    public static String formula(String body) {
        Matcher m = Pattern.compile("\"formula\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
        if(!m.find()) throw new IllegalArgumentException("Falta el campo 'formula' de tipo texto");
        return unescape(m.group(1));
    }
    public static Map<String,Boolean> assignmentFrom(String body) {
        Matcher m=Pattern.compile("\"assignment\"\\s*:\\s*\\{([^{}]*)\\}").matcher(body);
        if(!m.find()) throw new IllegalArgumentException("Falta 'assignment' como objeto JSON");
        String raw=m.group(1).trim(); Map<String,Boolean> out=new LinkedHashMap<>();
        if (raw.endsWith(",")) {
            throw new IllegalArgumentException(
                "Asignación JSON inválida: coma final"
            );
        }
        if(raw.isEmpty()) return out;
        Pattern pair=Pattern.compile("\\s*\"([A-Za-z][A-Za-z0-9_]*)\"\\s*:\\s*(true|false)\\s*(?:,|$)");
        int at=0;
        while(at<raw.length()) {
            Matcher p=pair.matcher(raw); p.region(at,raw.length());
            if(!p.lookingAt()) throw new IllegalArgumentException("Asignación JSON inválida; use valores booleanos true/false");
            if(out.putIfAbsent(p.group(1),Boolean.parseBoolean(p.group(2)))!=null) throw new IllegalArgumentException("Variable repetida");
            at=p.end();
        }
        return out;
    }
    private static String unescape(String s) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();i++) {char c=s.charAt(i);if(c!='\\') {out.append(c);continue;}
            if(++i>=s.length()) throw new IllegalArgumentException("Escape JSON incompleto");
            char n=s.charAt(i);
            switch(n) {case 'n'->out.append('\n');case 'r'->out.append('\r');case 't'->out.append('\t');case '"'->out.append('"');case '\\'->out.append('\\');default->throw new IllegalArgumentException("Escape JSON no soportado");}
        }
        return out.toString();
    }
}
