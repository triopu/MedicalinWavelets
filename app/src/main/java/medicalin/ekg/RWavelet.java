package medicalin.ekg;

import java.util.ArrayList;
import java.util.List;

import medicalin.ekg.DwtSrc.DWT;
import medicalin.ekg.DwtSrc.Wavelet;

public class RWavelet {
    private static DownSampling downSampling;
    private static RemoveConsecutiveR removeConsecutiveR;
    private static SearchBack searchBack;
    private static double[] out;
    private static double[] dt;
    private static List<Double> process;
    private static List<Double> resampleECG;
    private static List<Double> resampleTime;
    private static List<Integer> ann;
    private static List<Double> db4;

    private double rrAvr,hr;
    private double lastRR, lastHR;

    public RWavelet(ArrayList<Double>time, ArrayList<Integer> ecg, double kthr, int krr) throws Exception {
        ann = new ArrayList<Integer>();

        if(ecg.size() < 10) return;

        dt = new double[ecg.size()];
        for (int i =0; i<ecg.size();i++){
            dt[i] = (double)ecg.get(i);
        }

        double[] out = new double[dt.length];
        out = DWT.transform(dt,Wavelet.Daubechies,4,9, DWT.Direction.forward);

        db4 = new ArrayList<Double>();
        for(int i = out.length/2; i<out.length;i++){
            db4.add(out[i]);
        }

        resampleECG = new ArrayList<Double>();
        process = new ArrayList<Double>();
        for(int i = out.length/2; i<out.length;i++){
            if(out[i] < 0) process.add(-out[i]);
            else process.add(out[i]);
            resampleECG.add(out[i]);
        }

        downSampling = new DownSampling(time, 2);
        resampleTime = downSampling.getOutput();

        double thr = kthr*getMaxValue(process);

        List<Integer> ir = new ArrayList<Integer>();
        for(int i =0; i<process.size()-1;i++){
            if(process.get(i) > thr) ir.add(i);
            ann.add(0);
        }

        removeConsecutiveR = new RemoveConsecutiveR(resampleECG,ir,krr);
        ir = new ArrayList<Integer>();
        ir = removeConsecutiveR.getIrs();

        searchBack = new SearchBack(resampleECG,process,ir,thr,krr);
        List<Integer> irsb = new ArrayList<Integer>();
        irsb = searchBack.getIrsb();

        if(irsb != null){
            for(int i= 0; i < irsb.size()-1; i++) {
                ir.add(irsb.get(i));
            }
        }

        for(int i = 0; i < ir.size(); i++){
            ann.set(ir.get(i),1);
        }

        double rrSum = 0.000;
        int rrDiv = 0;
        for(int i = 1;i<ir.size()-1;i++){
            double rrInt = time.get(ir.get(i)) - time.get(ir.get(i-1));
            if(rrInt > 0.400 && rrInt < 2.000){
                rrSum += rrInt;
                rrDiv += 1;
            }
        }

        if(rrDiv > 0){
            rrAvr = rrSum/rrDiv;
            hr = 60.000/rrAvr;
            lastRR = rrAvr;
            lastHR = hr;
        }else{
            rrAvr = lastRR;
            hr = lastHR;
        }
    }

    private double getMaxValue(List<Double> numbers){
        double maxValue = numbers.get(0);
        for(int i=1;i < numbers.size();i++){
            if(numbers.get(i) > maxValue){
                maxValue = numbers.get(i);
            }
        }
        return maxValue;
    }

    public List<Double> getD4(){
        return db4;
    }

    public List<Double> getResampledTime(){
        return resampleTime;
    }

    public List<Double> getResampleECG(){
        return resampleECG;
    }

    public List<Integer> getAnnotation(){
        return ann;
    }

    public double getRrAvr(){
        return rrAvr;
    }

    public double getHr(){
        return hr;
    }

}
