//#include "ecgData.h"
#include <SoftwareSerial.h>

SoftwareSerial Bluetooth(2,3);

#define  AD8232       A0
#define  ledPin       4

unsigned  long currentMicros  = 0;
unsigned  long previousMicros = 0;

const     long PERIOD         = 5000;

char outStr[16];
int outECG[4];
int counter = 0;
int i = 0;
void setup() {
  Bluetooth.begin(115200);
  delay(500);
}

void loop() {
  currentMicros = micros ();
  if (currentMicros - previousMicros >= PERIOD){
    previousMicros = currentMicros;
    int next_ecg_pt = analogRead(AD8232);
    //int next_ecg_pt = dataECG1[i];

    if(counter == 4){
      counter = 0;
      sprintf(outStr, "%03d*%03d*%03d*%03d", outECG[0], outECG[1], outECG[2], outECG[3]);
      Bluetooth.write(outStr);
    }else{
      outECG[counter] = next_ecg_pt;
      counter++;
    }
    //i++;
    //if(i > 780) i = 0; 
  }
}
