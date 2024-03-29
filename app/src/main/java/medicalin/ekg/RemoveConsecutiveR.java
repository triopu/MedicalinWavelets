package medicalin.ekg;

import java.util.ArrayList;
import java.util.List;

public class RemoveConsecutiveR {
    List<Integer> irs;
    public RemoveConsecutiveR(List<Double> ecg, List<Integer> ir, int krr){
        int irLength = ir.size();
        int iter = irLength;
        irs = new ArrayList<Integer>();
        if (irLength < 2){
            irs = ir;
        }else{
            int i = 1;
            int j = 0;
            boolean removeCR = true;
            while(removeCR){
                j = j+1;
                if(i>irLength - 1 || j > iter){
                    removeCR = false;
                    break;
                }
                if(ir.get(i) - ir.get(i-1) < krr){
                    irLength = irLength-1;
                    if(ecg.get(ir.get(i))>ecg.get(ir.get(i-1))){
                        ir.remove(i-1);
                    }else{
                        ir.remove(i);
                    }
                }else{
                    i = i+1;
                }
            }
            irs = ir;
        }
    }

    public List<Integer> getIrs(){
        return irs;
    }
}
