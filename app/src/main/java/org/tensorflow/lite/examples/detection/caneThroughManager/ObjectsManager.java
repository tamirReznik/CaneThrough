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
import android.os.Looper;
import android.util.Log;
import android.util.SizeF;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
//TODO - prevent duplicates of objects in liveobjects
public class ObjectsManager {

    public static final int ObjectManager_SIZE = 5;

    enum Position {
        LEFT,
        CENTER,
        RIGHT,
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

    public static ObjectsManager getInstance() {
        return instance;
    }


    public static void init(Context _context, String cameraId) {
        Log.i("handlerTag", "run: ");
        if (instance == null) {
            instance = new ObjectsManager(_context, cameraId);
        }
    }

    private ObjectsManager(Context context, String cameraId) {

        liveObjects = new ArrayList<>();
        alerted = new boolean[ObjectManager_SIZE];
        handler = new Handler(Looper.myLooper());

        this.context = context.getApplicationContext();

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
     */
    private int distanceCalc(float realHeight, float pixHeight) {
//        return distance in meter's
        float result = (focalLength * realHeight * imageHeight) / (heightSensor * pixHeight) / 1000;
        Log.i(Labels_Keys.CANE_THROUGH_LOG, "distanceCalc: " + result + " focal:" + focalLength + " real height: " + realHeight + " imageH: " + imageHeight + " heightSensor: " + heightSensor + " pixH:" + pixHeight);
        return (int) result;
    }

    private void initAlertRunnable() {
        new Runnable() {
            @Override
            public void run() {

                for (int i = 0; i < liveObjects.size(); i++) {
                    if (!alerted[i]) {
                        Detector.Recognition tmpObj = liveObjects.get(i);
                        Log.i("handlerTag", "run: " + tmpObj.toString());
                        // position[0].name();
                        if (tmpObj.getTitle().equals(Labels_Keys.PERSON)) {
                            textToSpeech.speak(tmpObj.getTitle() + " " + distanceCalc(Labels_info.objectHeight.get(Labels_Keys.PERSON),
                                    tmpObj.getLocation().height()) + "meter " + getPos(tmpObj));
                            Log.i("handlerTag", "run: " + tmpObj.getLocation().centerX() + " pos: " + getPos(tmpObj));
                        }
                        alerted[i] = true;
                    }
                }
                handler.postDelayed(this, 4000);
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
            //  updateLiveObjPos();
            return;
        }

        if (liveObjects.size() < list.size()) {
            liveObjects.addAll(list.subList(0, list.size() - liveObjects.size()));
            // updateLiveObjPos();
        }

        boolean[] keepOld = new boolean[ObjectManager_SIZE];
        boolean[] addNew = new boolean[list.size()];
        Arrays.fill(addNew,Boolean.TRUE);
        for (int i = 0; i < liveObjects.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                Detector.Recognition tempObj = liveObjects.get(i);
                if (tempObj.getTitle().equals(list.get(j).getTitle()) && getPos(tempObj).equals(getPos(list.get(j)))) {
                    keepOld[i] = true;
                    addNew[j] = false;
                }
            }
        }

        for (int i = 0; i < liveObjects.size(); i++) {
            if (!keepOld[i]) {
                for (int j = 0; j < addNew.length; j++) {
                    if (addNew[j]) {
                        liveObjects.set(i, list.get(j));
                        alerted[i] = false;
                    }
                }
            }

        }

    }

    private Position getPos(Detector.Recognition obj) {

        if (obj.getLocation().centerX() <= 512) {
            return Position.LEFT;
        } else if (obj.getLocation().centerX() >= 1048) {
            return Position.RIGHT;
        } else {
            return Position.CENTER;
        }

    }
}
