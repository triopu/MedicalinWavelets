package medicalin.ekg.Wavelets;

import java.util.ArrayList;
import java.util.List;

public class DownSampling {
    private ArrayList<Integer> output;
    public DownSampling(List<Integer> input, int n){
        int arrayLength = input.size();
        output = new ArrayList<Integer>();
        int k = 0;
        output.add(input.get(k));
        while(true){
            k = k+n;
            if(k > arrayLength - 1) break;
            output.add(input.get(k));
        }
    }

    public ArrayList<Integer> getOutput(){
        return output;
    }
}