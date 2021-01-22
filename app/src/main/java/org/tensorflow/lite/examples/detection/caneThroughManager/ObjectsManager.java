/*
 * <h1>Object Manager</h1>
 * The Object Manager is a singleton class that manages Arraylist of Detector.Recognition (liveObjects).
 * The Object Manager's purpose is to keep the liveObjects updated so the system could notify the user in real-time objects in his environment.
 */


package org.tensorflow.lite.examples.detection.caneThroughManager;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.Log;
import android.util.SizeF;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.ArrayList;
import java.util.List;

public class ObjectsManager {

    enum Position {
        LEFT,
        CENTER,
        RIGHT
    }

    private Float focalLength;
    private Float heightSensor, widthSensor;
    private Float imageHeight, imageWidth;
    private final Handler handler;
    private final Context context;
    private final boolean[] alerted;
    private static ObjectsManager instance;
    private TTSManager textToSpeech;
    private ArrayList<Detector.Recognition> liveObjects;
    private Position[] position;
    private Float[] distance;

    public static ObjectsManager getInstance() {
        return instance;
    }


    public static void init(Context _context, String cameraId) {
        Log.i("handlerTag", "run: ");
        if (instance == null) {
            instance = new ObjectsManager(_context, cameraId);
        }
    }

    private ObjectsManager(Context _context, String cameraId) {

        liveObjects = new ArrayList<>();
        alerted = new boolean[5];
        distance = new Float[5];
        handler = new Handler();
        position = new Position[5];

        context = _context.getApplicationContext();

        initVoiceAlerts();

        initCameraParam(cameraId);

        Log.i("CT_log", "ObjectsManager: focal" + focalLength + " height sensor: " + heightSensor + " width sensor: " + widthSensor);


    }

    private void initVoiceAlerts() {
        TTSManager.init(context);

        textToSpeech = TTSManager.getInstance();

        initAlertRunnable();
    }

    private void initCameraParam(String cameraId) {
        float[] focalL = new float[0];
        SizeF size = new SizeF(0, 0);
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics character = manager.getCameraCharacteristics(cameraId);
            focalL = character.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            size = character.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        } catch (CameraAccessException e) {
            Log.e("CT_log", e.getMessage(), e);
        }
        assert size != null;
        heightSensor = size.getHeight();

        widthSensor = size.getWidth();

        imageHeight = 1080.0F;
        imageWidth = 1920.0F;


        assert focalL != null;
        focalLength = focalL[focalL.length - 1];

    }
/**
 * @param realHeight - object height in mm
 * @param pixHeight  - object height in pixels
 * */
    private Float distanceCalc(float realHeight, float pixHeight) {
//        return distance in meter's
        return (focalLength * realHeight * imageHeight) / (heightSensor * pixHeight) * 1000;
    }


    private void initAlertRunnable() {
        new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < liveObjects.size(); i++) {
                    if (!alerted[i]) {
                        Log.i("handlerTag", "run: " + liveObjects.get(i).toString());
                        // position[0].name();
                        textToSpeech.speak(liveObjects.get(i).getTitle());
                        alerted[i] = true;
                    }
                }
                handler.postDelayed(this, 3000);
            }
        }.run();
    }


    public synchronized void addObjects(List<Detector.Recognition> list) {
        Log.i("listSize", "addObjects: " + liveObjects.size());
        if (list.isEmpty()) {
            return;
        }

        if (liveObjects == null || liveObjects.size() == 0) {
            liveObjects = new ArrayList<>(list);
            return;
        }

        if (liveObjects.size() < list.size()) {
            liveObjects.addAll(list.subList(0, list.size() - liveObjects.size()));
        }

        boolean[] keepOld = new boolean[5];
        boolean[] removeNew = new boolean[list.size()];
        for (int i = 0; i < liveObjects.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                Detector.Recognition tempObj = liveObjects.get(i);
                if (tempObj.getTitle().equals(list.get(j).getTitle())) {
                    //Object from left
                    if (tempObj.getLocation().centerX() <= 80 && list.get(j).getLocation().centerX() <= 80) {
                        // distance[i] = distanceCalc(Labels_info.label_val.get(liveObjects.get(i).getTitle())[0],tempObj.getLocation().height());
                        keepOld[i] = true;
                        removeNew[j] = true;
                        position[i] = Position.LEFT;
                        continue;
                    }
                    //Object in right
                    if (tempObj.getLocation().centerX() >= 220 && list.get(j).getLocation().centerX() >= 220) {
                        keepOld[i] = true;
                        removeNew[j] = true;
                        position[i] = Position.RIGHT;
                        continue;
                    }
                    //Object from center
                    if ((tempObj.getLocation().centerX() > 80 && tempObj.getLocation().centerX() < 220)
                            && (list.get(j).getLocation().centerX() > 80 && list.get(j).getLocation().centerX() < 220)) {
                        keepOld[i] = true;
                        removeNew[j] = true;
                        position[i] = Position.CENTER;

                    }

                }
            }

        }

        for (int i = 0; i < liveObjects.size(); i++) {
            if (!keepOld[i]) {
                for (int j = 0; j < removeNew.length; j++) {
                    if (!removeNew[j]) {
                        liveObjects.set(i, list.get(j));
                        alerted[i] = false;
                    }
                }
            }

        }

    }

}
