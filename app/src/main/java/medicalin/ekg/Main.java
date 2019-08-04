package medicalin.ekg;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, NameDialog.NameDialogListener, VarDialog.VarDialogListener{
    private final static String TAG = Main.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String deviceAddress;
    private String deviceName;

    FileWriter fw2;
    String printFormat;

    GraphView graphView;
    private LineGraphSeries<DataPoint> ecgGraph;
    private double graph2LastXValue = 5d;

    private boolean autoScrollX = true;
    private int xView = 1000;
    private double minX,maxX,minY,maxY;

    private boolean connected = false;
    private boolean record = false;

    private double time = 0.000;

    private String fileName = "EKG";

    private BluetoothLeService bluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> gattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private BluetoothGattCharacteristic notifyCharacteristic;

    private static RWavelet rWavelet;

    //Part of Signal Processing
    private SampleData sampleData;
    private int[] sampleECGData = SampleData.generateData(4);

    //Make a data processing container
    private ArrayList<Integer> processedECGData = new ArrayList<Integer>();
    private ArrayList<Double> processedECGTime = new ArrayList<Double>();

    int second;
    private long startTime = 0;
    private double theTime;

    //It's a goAsync part
    BroadcastReceiver.PendingResult result;
    boolean asyncTask = false;

    //Boolean to Process the Data.
    boolean process = false;
    boolean unprocess = true;

    //Initialize TextView
    private TextView HR;
    private TextView RR;
    private TextView Percentage;

    private final int REQUEST_CODE_PICK_DIR = 1;
    private final int REQUEST_CODE_PICK_FILE = 2;
    String newFile  = "";

    private ArrayList<Integer> dataInput = new ArrayList<Integer>();
    private ArrayList<Double> timeInput = new ArrayList<Double>();

    private String varString = "";
    private Double kthr = 0.3;
    private Integer krr = 36;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetoothLeService = ((BluetoothLeService.LocalBinder)service).getService();
            if(!bluetoothLeService.initialize()){
                Log.e(TAG,"Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            bluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };

    private class BackgroundOpen extends AsyncTask<Object,int[],ArrayList<Integer>>{

        @Override
        protected ArrayList<Integer> doInBackground(Object... objects) {
            final String newFile = (String) objects[0];
            if (newFile.indexOf('/')==0){
                File file = new File (newFile);
                loadFile(file);
                ArrayList<Integer> ecg = new ArrayList<Integer>();
                ArrayList<Double> time = new ArrayList<Double>();
                int dataLength = dataInput.size();
                recordFile("record");
                for(int i = 0; i < dataInput.size();i++){
                    final int finalI = i;
                    final double percent = ((double)finalI/dataLength)*100;
                    Log.d("Debugging: ",String.format("%.2f",percent)+"|"+String.valueOf(finalI)+"/"+String.valueOf(dataLength));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            graphIt(String.valueOf(dataInput.get(finalI)));
                            Percentage.setText(" Process: "+ String.format("%.2f",percent)+"%");
                        }
                    });
                    ecg.add(dataInput.get(i));
                    time.add(timeInput.get(i));
                    if(ecg.size() >= 1000 || i == dataLength - 1){
                        new SignalProcessing().execute(ecg, time);
                        ecg = new ArrayList<Integer>();
                        time = new ArrayList<Double>();
                        //delayProcess(4);
                    }
                }
                recordFile("stop");
            }
            return null;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SignalProcessing extends AsyncTask<Object,int[],ArrayList<Integer>>{

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @SafeVarargs
        @Override
        protected final ArrayList<Integer> doInBackground(Object... integers) {

            //Parse the input of AsyncTask, ECG data in 0 and Time in 1
            ArrayList<Integer> data = (ArrayList<Integer>) integers[0];
            ArrayList<Double> time = (ArrayList<Double>) integers[1];

            //If data size is less than 10,cancel AsynTask by return the data
            if(data.size() < 10) return data;

            double hr = 0.00;
            double rr = 0.00;

            if(data.size() > 10) {
                //Part of Wavelets
                rWavelet = new RWavelet(time, data, kthr, krr);
                List<Double> d4 = rWavelet.getD4();
                List<Integer> resampleECG = rWavelet.getResampleECG();
                List<Double> resampleTime = rWavelet.getResampledTime();
                List<Integer> annRPeak = rWavelet.getAnnotation();
                int datalength = 0;
                if(d4.size() > resampleECG.size()){
                    datalength = resampleECG.size() - 1;
                }else{
                    datalength = d4.size()-1;
                }

                if (record) {
                    for (int i = 0; i < datalength; i++) {
                        try {
                            printFormat = String.format("%.5f\t%d\t%.5f\t%d", resampleTime.get(i), resampleECG.get(i), d4.get(i), annRPeak.get(i));
                            printFormat = printFormat.replace(',','.');
                            fw2.append(printFormat).append("\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                hr = rWavelet.getHr();
                rr = rWavelet.getRrAvr();

            }

            final double finalHr = hr;
            final double finalRr = rr;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if(finalHr != 0) {
                        RR.setText(" RR: " + String.format("%.3f", finalRr));
                        HR.setText(" HR: " + String.format("%.0f", finalHr));
                        if (finalHr > 100 || finalHr < 60) {
                            HR.setTextColor(Color.RED);
                        } else {
                            HR.setTextColor(Color.BLACK);
                        }
                    }
                }
            });

            if(asyncTask) {
                result.finish();
            }
            return data;
        }

        @Override
        protected void onProgressUpdate(int[]... values) {
            //super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(ArrayList integers) {
            //super.onPostExecute(integers);
        }
    }

    // Handles various events fired by the Service.
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                //ACTION_GATT_CONNECTED: connected to a GATT server.
                connected = true;
                Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_LONG).show();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                //ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
                connected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
                connectGattServices(bluetoothLeService.getSupportedServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read or notification operations.
                String incomeData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d("BluetoothData:", incomeData);
                String[] items = incomeData.split("\\*");
                for (String item : items) {
                    if (item.length() == 3) {
                        graphIt(item);
                        if(process) {
                            //Calculate the time, process data every 5 second
                            second = getTime(startTime);
                            if (second < 5) {
                                //Get the time of ECG data
                                time     = time+0.005;
                                Log.d("Time", String.valueOf(time));

                                //Collect data
                                processedECGData.add(Integer.valueOf(item));
                                processedECGTime.add(time);
                            } else {
                                startTime = System.currentTimeMillis();
                                if(processedECGData.size() > 0) {
                                    result = goAsync();
                                    asyncTask = true;
                                    new SignalProcessing().execute(processedECGData, processedECGTime);
                                    Log.d("TheData Input", String.valueOf(processedECGData.size()));
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    private void graphIt(String item) {
        if (graph2LastXValue >= xView) {
            graph2LastXValue = 0;
            ecgGraph.resetData(new DataPoint[]{new DataPoint(graph2LastXValue, Double.parseDouble(item))});
        } else {
            graph2LastXValue += 1d;
        }
        ecgGraph.appendData(new DataPoint(graph2LastXValue, Double.parseDouble(item)), autoScrollX, 1000);
    }

    private void connectGattServices(List<BluetoothGattService> gattServices) {
        if(gattServices == null) return;
        String uuid = null;
        gattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for(BluetoothGattService gattService: gattServices){
            List<BluetoothGattCharacteristic> thegattCharacteristics = gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            for(BluetoothGattCharacteristic gattCharacteristic : thegattCharacteristics){
                charas.add(gattCharacteristic);
                uuid = gattCharacteristic.getUuid().toString();

                if(uuid.equals("0000ffe1-0000-1000-8000-00805f9b34fb")){
                    notifyCharacteristic = gattCharacteristic;
                    bluetoothLeService.setCharacteristicNotification(notifyCharacteristic,true);
                }
            }
            gattCharacteristics.add(charas);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter(){
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private int getTime(long startTime){
        long millis = System.currentTimeMillis() - startTime;
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds     = seconds % 60;
        return seconds;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main_activity);

        DrawerLayout drawerLayout = findViewById(R.id.main_activity);

        graphInit();

        final Intent intent = getIntent();
        deviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent,serviceConnection, BIND_AUTO_CREATE);

        NavigationView navigationView = findViewById(R.id.navi_main);
        navigationView.setNavigationItemSelectedListener(this);

        HR = (TextView)findViewById(R.id.heart_rate);
        HR.setMovementMethod(new ScrollingMovementMethod());
        RR = (TextView)findViewById(R.id.rr_interval);
        RR.setMovementMethod(new ScrollingMovementMethod());
        Percentage = (TextView)findViewById(R.id.percentage);
        Percentage.setMovementMethod(new ScrollingMovementMethod());
        HR.setText(" HR: ");
        RR.setText(" RR: ");
        Percentage.setText(" Process: ");
        HR.setVisibility(View.INVISIBLE);
        RR.setVisibility(View.INVISIBLE);
        Percentage.setVisibility(View.INVISIBLE);

    }

    private void graphInit() {
        graphView = findViewById(R.id.graph);
        ecgGraph = new LineGraphSeries<>();
        graphView.addSeries(ecgGraph);

        ecgGraph.setThickness(1);
        ecgGraph.setColor(Color.YELLOW);

        graphView.getViewport().setScrollable(true);

        graphView.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graphView.getGridLabelRenderer().setVerticalLabelsVisible(false);

        graphView.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        graphView.getViewport().setDrawBorder(false);

        graphView.getGridLabelRenderer().setGridColor(Color.WHITE);

        minX = 0;maxX = 1000;minY = 100;maxY = 500;

        makeBorder(graphView,minX,maxX,minY,maxY);

        graphView.getViewport().setYAxisBoundsManual(true);
        graphView.getViewport().setMinY(minY);
        graphView.getViewport().setMaxY(maxY);

        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setMinX(minX);
        graphView.getViewport().setMaxX(maxX);

        double[] centerX = {200,400,600,800};
        double tickWidthY = 20;
        double tickLengthY = 2;

        makeSecondTickY(centerX, minY, maxY, tickWidthY, tickLengthY);

        double[] centerY = {200,300,400};
        double tickWidthX = 20;
        double tickLengthX = 2;

        makeSecondTickX(centerY, minX, maxX, tickWidthX, tickLengthX);
    }

    private void makeSecondTickY(double[] center, double minY, double maxY, double tickWidth, double tickLength) {
        for(int i = 0; i<center.length;i++){
            double y = minY;
            while(y < maxY) {
                y = y + tickWidth;
                int thickness = 1;
                LineGraphSeries<DataPoint> tick = new LineGraphSeries<>(new DataPoint[]{
                        new DataPoint(center[i] - tickLength, y),
                        new DataPoint(center[i] + tickLength, y)
                });
                graphView.addSeries(tick);
                tick.setThickness(thickness);
                tick.setColor(Color.WHITE);
            }
        }
    }

    private void makeSecondTickX(double[] center, double minX, double maxX, double tickWidth, double tickLength) {
        for(int i = 0; i<center.length;i++){
            double x = minX;
            while(x < maxX) {
                x = x + tickWidth;
                int thickness = 1;
                LineGraphSeries<DataPoint> tick = new LineGraphSeries<>(new DataPoint[]{
                        new DataPoint(x, center[i] - tickLength),
                        new DataPoint(x, center[i] + tickLength)
                });
                graphView.addSeries(tick);
                tick.setThickness(thickness);
                tick.setColor(Color.WHITE);
            }
        }
    }

    private void makeBorder(GraphView graphView, double startX, double endX, double startY, double endY) {
        int thickness = 4;
        LineGraphSeries<DataPoint> series1 = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(startX,startY),
                new DataPoint(endX,startY)
        });
        graphView.addSeries(series1);
        series1.setThickness(thickness);
        series1.setColor(Color.WHITE);

        LineGraphSeries<DataPoint> series2 = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(endX,startY),
                new DataPoint(endX, endY)
        });
        graphView.addSeries(series2);
        series2.setThickness(thickness);
        series2.setColor(Color.WHITE);

        LineGraphSeries<DataPoint> series3 = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(startX, endY),
                new DataPoint(endX, endY)
        });
        graphView.addSeries(series3);
        series3.setThickness(thickness);
        series3.setColor(Color.WHITE);


        LineGraphSeries<DataPoint> series4 = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(startX, startY),
                new DataPoint(startX, endY)
        });
        graphView.addSeries(series4);
        series4.setThickness(3);
        series4.setColor(Color.WHITE);
    }

    @Override
    protected void onResume(){
        super.onResume();
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if(bluetoothLeService != null){
            final boolean result = bluetoothLeService.connect(deviceAddress);
            Log.d(TAG,"Connect request results = "+ result);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        unbindService(serviceConnection);
        bluetoothLeService = null;

        //If crash and recording, try to close the file writer
        if(fw2 != null) {
            try {
                fw2.flush();
                fw2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.bt_connect) {
            bluetoothLeService.connect(deviceAddress);
            Toast.makeText(getApplicationContext(),"Connecting...",Toast.LENGTH_SHORT).show();
        } else if (id == R.id.bt_disconnect) {
            bluetoothLeService.disconnect();
            Toast.makeText(getApplicationContext(),"Disconnected",Toast.LENGTH_SHORT).show();
        } else if (id == R.id.record) {
            if(unprocess){
                Toast.makeText(getApplicationContext(),"Must be processing!",Toast.LENGTH_SHORT).show();
            }else {
                recordFile("record");
            }
        } else if (id == R.id.stoprecord) {
            recordFile("stop");
        } else if (id == R.id.name_edit) {
            openDialog();
        } else if (id == R.id.process){
            if(!process){
                process = true;
                unprocess = false;
                HR.setVisibility(View.VISIBLE);
                RR.setVisibility(View.VISIBLE);
                theTime = System.currentTimeMillis() / 1000.00000;
                Toast.makeText(getApplicationContext(),"Processing data...",Toast.LENGTH_SHORT).show();
            }
            else if (!unprocess){
                process = false;
                unprocess = true;
                HR.setVisibility(View.INVISIBLE);
                RR.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(),"Stop processing data...",Toast.LENGTH_SHORT).show();
            }
        } else if(id == R.id.open){
            browseFile();
        } else if(id == R.id.variableinput){
            varDialog();
        }

        DrawerLayout drawer = findViewById(R.id.main_activity);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void openDialog(){
        NameDialog nameDialog = new NameDialog();
        nameDialog.show(getSupportFragmentManager(),"Name Dialog");
    }

    public void varDialog(){
        VarDialog varDialog = new VarDialog();
        varDialog.show(getSupportFragmentManager(),"Variable Dialog");
    }

    @Override
    public void applyText(String namefile) {
        fileName = namefile;
        Toast.makeText(getApplicationContext(),"Your file name is "+fileName,Toast.LENGTH_SHORT).show();
    }

    @Override
    public void applyVar(String varValue){
        varString = varValue;
        String[] vars = varString.split(";");
        if(vars.length == 2) {
            Toast.makeText(getApplicationContext(), "THR: " + vars[0] + " RR: " + vars[1], Toast.LENGTH_SHORT).show();
            kthr = Double.parseDouble(vars[0]);
            krr  = Integer.parseInt(vars[1]);
        }else{
            Toast.makeText(getApplicationContext(), "Gunakan tanda ; untuk memisahkan!", Toast.LENGTH_SHORT).show();
        }
    }

    public void recordFile(String input){
        if(input.equals("record")) {
            record = true;
            processedECGData = new ArrayList<Integer>();

            //Try make a new file in the Internal
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath());
            File file2 = new File(  dir, "/" + fileName + "_Wavelets_"+
                                    String.valueOf(kthr)+"-"+String.valueOf(krr)+".txt");
            time = 0.000;

            Log.d("File is", String.valueOf(file2) + ".txt");

            try {
                fw2 = new FileWriter(file2, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Toast.makeText(getApplicationContext(), "Recording: " + fileName, Toast.LENGTH_SHORT).show();
        }
        if(input.equals("stop")) {
            record = false;
            startTime = 0;
            if (fw2 != null) {
                try {
                    fw2.flush();
                    fw2.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
            } else {
                //Toast.makeText(getApplicationContext(), "Can't be stopped", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void browseFile(){
        final Activity activityForButton = this;
        Log.d("Activity", "Start Browsing");
        Intent fileExplorerIntent = new Intent(FileBrowserActivity.INTENT_ACTION_SELECT_FILE,null,
                activityForButton,
                FileBrowserActivity.class
        );
        startActivityForResult(
                fileExplorerIntent,
                REQUEST_CODE_PICK_FILE
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == REQUEST_CODE_PICK_FILE){
            if(resultCode == RESULT_OK){
                newFile = data.getStringExtra(FileBrowserActivity.returnFileParameter);
                Toast.makeText(this, "Memproses: "+newFile, Toast.LENGTH_SHORT).show();
                Percentage.setVisibility(View.VISIBLE);
                new BackgroundOpen().execute(newFile);
            }else{
                Toast.makeText(this,"Tidak mendapatkan file", Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    public void loadFile(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader br = new BufferedReader(isr);

        int dataLength=0;
        try {
            while ((br.readLine()) != null) {
                dataLength++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fis.getChannel().position(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String sCurrentLine;
        String[] characters = new String[dataLength];
        int i = 0;
        try {
            while ((sCurrentLine = br.readLine())!=null) {
                String[] arr = sCurrentLine.split("\t");
                timeInput.add(Double.parseDouble(arr[0]));
                dataInput.add(Integer.parseInt(arr[1]));
                i++;
            }
        } catch (IOException e) {e.printStackTrace();}
    }
}
