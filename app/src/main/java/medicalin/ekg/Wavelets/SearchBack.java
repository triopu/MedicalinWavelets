package medicalin.ekg.Wavelets;

import java.util.ArrayList;
import java.util.List;

public class SearchBack {
    private RemoveConsecutiveR removeConsecutiveR;
    List<Integer> irsb = new ArrayList<Integer>();
    public SearchBack(List<Integer> ecg, List<Double> d1, List<Integer> ir, double thr, int krr){
        int lengthR = ir.size();
        if(lengthR < 2){
            System.out.println("Minimum number of R is 2");
        }else{
            int i = 1;
            int k = 0;
            List<Integer> irsb = new ArrayList<Integer>();
            while (true){
                if(i>lengthR - 1) break;
                if(ir.get(i) - ir.get(i-1) > 100){
                    double thrs = thr*0.5;
                    List<Integer> irt = new ArrayList<Integer>();
                    for(int l =0; l<d1.size()-1;l++){
                        if(d1.get(i) > thr) irt.add(i);
                    }
                    if(irt.size() < 1){
                        System.out.println("Prolonged RR");
                        System.out.println(ir.get(i-1)+"//"+ir.get(i));
                        i =i+1;
                    }else{
                        removeConsecutiveR = new RemoveConsecutiveR(ecg,ir,krr);
                        irt = new ArrayList<Integer>();
                        irt = removeConsecutiveR.getIrs();
                        irsb = new ArrayList<Integer>();
                        for(int u = 0; u<irt.size()-1; u++){
                            irsb.add(ir.get(i-1)+irt.get(u));
                        }
                    }
                }else{
                    i = i+1;
                    System.out.println("Normal RR");
                }

            }
        }
    }

    public List<Integer> getIrsb(){
        if(irsb.isEmpty()) {
            return null;
        }else{
            return irsb;
        }
    }
}
