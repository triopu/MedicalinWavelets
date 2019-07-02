package medicalin.ekg.Wavelets;

import java.util.ArrayList;
import java.util.List;

public class DownSamplingTime {
    private ArrayList<Double> output;
    public DownSamplingTime(List<Double> input, int n){
        int arrayLength = input.size();
        output = new ArrayList<Double>();
        int k = 0;
        output.add(input.get(k));
        while(true){
            k = k+n;
            if(k > arrayLength - 1) break;
            output.add(input.get(k));
        }
    }

    public ArrayList<Double> getOutput(){
        return output;
    }
}
