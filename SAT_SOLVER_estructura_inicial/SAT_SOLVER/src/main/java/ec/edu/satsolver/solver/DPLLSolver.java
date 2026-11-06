package ec.edu.satsolver.solver;
import ec.edu.satsolver.model.FormulaCNF;
import java.util.*;
/** DPLL exacto: propagación unitaria y ramificación recursiva. */
public final class DPLLSolver {
    public Optional<Map<String, Boolean>> solve(FormulaCNF formula) {
        int[] state = new int[formula.variables().size()+1]; // 0 = no asignado, 1 = true, -1 = false
        int[] solved = dpll(formula.clauses(), state);
        if (solved == null) return Optional.empty();
        Map<String,Boolean> values = new LinkedHashMap<>();
        for(int i=1;i<solved.length;i++) values.put(formula.variables().get(i-1), solved[i]>=0);
        return Optional.of(values);
    }
    private int[] dpll(List<int[]> clauses, int[] a) {
        while (true) {
            boolean changed = false;
            boolean allSatisfied = true;
            for (int[] clause : clauses) {
                boolean sat = false; int unassigned = 0, last = 0;
                for(int lit:clause) {
                    int value=a[Math.abs(lit)];
                    if(value != 0 && ((lit>0 && value==1)||(lit<0 && value==-1))) {sat=true;break;}
                    if(value==0) {unassigned++; last=lit;}
                }
                if(sat) continue;
                allSatisfied=false;
                if(unassigned==0) return null;
                if(unassigned==1) {
                    int idx=Math.abs(last), needed=last>0?1:-1;
                    if(a[idx]!=0 && a[idx]!=needed) return null;
                    if(a[idx]==0) {a[idx]=needed;changed=true;}
                }
            }
            if(allSatisfied) return a;
            if(!changed) break;
        }
        int branch=0;
        for(int i=1;i<a.length;i++) if(a[i]==0) {branch=i;break;}
        if(branch==0) return null;
        for(int val:new int[]{1,-1}) {
            int[] next=a.clone();next[branch]=val;
            int[] result=dpll(clauses,next);
            if(result!=null) return result;
        }
        return null;
    }
}
