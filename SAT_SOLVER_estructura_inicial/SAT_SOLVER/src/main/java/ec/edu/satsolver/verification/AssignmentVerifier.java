package ec.edu.satsolver.verification;
import ec.edu.satsolver.model.FormulaCNF;
import java.util.*;
/** Comprueba en tiempo lineal en literales una asignación completa propuesta. */
public final class AssignmentVerifier {
    public boolean verify(FormulaCNF f, Map<String,Boolean> assignment) {
        if(!assignment.keySet().containsAll(f.variables())) throw new IllegalArgumentException("Faltan variables en la asignación");
        for(int[] clause:f.clauses()) {
            boolean satisfied=false;
            for(int lit:clause) {
                Boolean val=assignment.get(f.variables().get(Math.abs(lit)-1));
                if(val==null) throw new IllegalArgumentException("Hay una variable sin valor booleano");
                if(lit>0?val:!val) {satisfied=true;break;}
            }
            if(!satisfied) return false;
        }
        return true;
    }
}
