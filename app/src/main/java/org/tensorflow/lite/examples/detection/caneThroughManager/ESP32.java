package org.tensorflow.lite.examples.detection.caneThroughManager;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.widget.Toast;

import me.aflak.arduino.Arduino;
import me.aflak.arduino.ArduinoListener;


public class ESP32 {

    private static ESP32 esp32_instance = null;

    private Arduino arduino;
    private Context context;
    private boolean connected;

    private ESP32(Context context) {
        this.context = context;
        arduino = new Arduino(context);
        arduino.addVendorId(0x1a86);
        arduino.setBaudRate(115200);
        connected = false;
    }
    public static void init(Context context){
        if (esp32_instance == null) {
            esp32_instance = new ESP32(context);
        }
    }
    public static ESP32 getInstance()
    {
        if(esp32_instance != null)
            return esp32_instance;
        return null;
    }

        public void sendMessage(String msg){
            arduino.send(msg.getBytes());
        }

        public void connectToESP32(){
            arduino.setArduinoListener(new ArduinoListener() {
                @Override
                public void onArduinoAttached(UsbDevice device) {
                    Toast.makeText(context,"Attached",Toast.LENGTH_LONG).show();
                    arduino.open(device);
                }

                @Override
                public void onArduinoDetached() {
                    Toast.makeText(context,"detached",Toast.LENGTH_LONG).show();
                }

                @Override
                public void onArduinoMessage(byte[] bytes) {

                }

                @Override
                public void onArduinoOpened() {
                    connected = true;
                    String str = "Welcome ESP32 !";
                    Toast.makeText(context,str,Toast.LENGTH_LONG).show();
                }

                @Override
                public void onUsbPermissionDenied() {
                    arduino.reopen();
                }
            });
        }

        public void disconnectESP32(){
        if(connected) {
            arduino.unsetArduinoListener();
            arduino.close();
        }
        connected =false;
        }

    public boolean isConnected() {
        return connected;
    }
}
