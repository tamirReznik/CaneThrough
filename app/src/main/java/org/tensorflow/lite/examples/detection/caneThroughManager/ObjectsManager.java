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
import android.util.Log;
import android.util.SizeF;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ObjectsManager {
    enum Position {
        LEFT,
        CENTER,
        RIGHT,
    }

    public static final int MAX_DISTANCE_LEVEL = 6;
    public static final int ObjectManager_SIZE = 6;
    public static final int LANDSCAPE_FRAME_ANGLE_DEGREE = 18;
    public static final int PITCH_FRAME_ANGLE_DEGREE = 4;
    public static final int MILLISECONDS_ALERT_DELAY = 4000;
    public static final int ALERT_DELAY_SECONDS = MILLISECONDS_ALERT_DELAY / 1000;
    public static final int OBJECT_HELD_TIME = ALERT_DELAY_SECONDS * 5;

    public static final String MOTOR_ON_SIGNAL = "1";
    public static final String MOTOR_OFF_SIGNAL = "0";

    public static final String LEFT_CHAR = "L";
    public static final String CENTER_CHAR = "C";
    public static final String RIGHT_CHAR = "R";

    private static AtomicInteger azimuthIndex, pitchIndex;
    private static ObjectsManager instance;
    private final Context context;
    HashMap<String, HashSet<MyAtomicRef>> atomicLiveObjects;
    private Float focalLength;
    private Float heightSensor, widthSensor;
    private static Float imageHeight, imageWidth;
    private TTSManager textToSpeech;
    private Timer timer;
    private long lastAlert;

    private ObjectsManager(Context context, String cameraId) {

        atomicLiveObjects = new HashMap<>();

        azimuthIndex = new AtomicInteger(-1);
        pitchIndex = new AtomicInteger(-1);

        this.context = context.getApplicationContext();

        initVoiceAlerts();

        initCameraParam(cameraId);

        Log.i("CT_log", "ObjectsManager: focal" + focalLength + " height sensor: " + heightSensor + " width sensor: " + widthSensor);

    }

    public static ObjectsManager getInstance() {
        return instance;
    }


    public static void init(Context context, String cameraId) {
        if (instance == null) {
            instance = new ObjectsManager(context, cameraId);
        }
    }

    public Position getPos(Detector.Recognition obj) {

        if (obj.getLocation().centerX() <= imageWidth / 3) {
            return Position.LEFT;
        } else if (obj.getLocation().centerX() >= 2 * imageWidth / 3) {
            return Position.RIGHT;
        } else {
            return Position.CENTER;
        }

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
        Log.i("heightSensor", "initCameraParam: " + heightSensor + " size of focal: " + focalL.length);

    }

    /**
     * realWidth - object height in mm
     * pixWidth  - object height in pixel
     *
     * @param detectedObject - Detector.Recognition object to calculate distance from
     * @return - Estimated distance in meters
     */
    //addObject method filter object that not exist in Labels_info.objectWidth keys
    private double distanceCalcViaWidth(Detector.Recognition detectedObject) {

        float realWidth = Labels_info.objectWidth.get(detectedObject.getTitle());
        float pixWidth = detectedObject.getLocation().width();

        if (Labels_info.PERSON.equals(detectedObject.getTitle()) && pixWidth / detectedObject.getLocation().width() >= 2) {
            realWidth = Labels_info.objectWidth.get(Labels_info.PERSON_FACE);
        }

        float result = (focalLength * realWidth * imageWidth) / (widthSensor * pixWidth) / 1000;
        Log.i(Labels_info.CANE_THROUGH_LOG, "distanceCalc: " + result + " focal:" + focalLength + " real width: " + realWidth + " imageW: " + imageWidth + " widthSensor: " + heightSensor + " pixH:" + pixWidth + " pixW:" + detectedObject.getLocation().width());
        return result;

    }

    /**
     * realHeight - object height in mm
     * pixHeight  - object height in pixel
     *
     * @param detectedObject - Detector.Recognition object to calculate distance from
     * @return - Estimated distance in meters
     */
    //addObject method filter object that not exist in Labels_info.objectHeight keys
    private double distanceCalcViaHeight(Detector.Recognition detectedObject) {


        float realHeight = Labels_info.objectHeight.get(detectedObject.getTitle());
        float pixHeight = detectedObject.getLocation().height();

        if (Labels_info.PERSON.equals(detectedObject.getTitle()) && pixHeight / detectedObject.getLocation().width() > 2) {
            realHeight = Labels_info.objectHeight.get(Labels_info.PERSON_FACE);
        }

        float result = (focalLength * realHeight * imageHeight) / (heightSensor * pixHeight) / 1000;
        Log.i(Labels_info.CANE_THROUGH_LOG, "distanceCalc: " + result + " focal:" + focalLength + " real height: " + realHeight + " imageH: " + imageHeight + " heightSensor: " + heightSensor + " pixH:" + pixHeight + " pixW:" + detectedObject.getLocation().width());
        return result;

    }

    public void alertCalculation(boolean initiated) {
        String currentKey = getCurrentKey();
        if (atomicLiveObjects.get(currentKey) == null || Objects.requireNonNull(atomicLiveObjects.get(currentKey)).isEmpty()) {
            Log.i("ptttTime", "exit: " + atomicLiveObjects.toString());
            return;
        }

        ArrayList<MyDetectedObject> myDetectedObjects = Objects.requireNonNull(atomicLiveObjects.get(currentKey)).stream().map(AtomicReference::get).collect(Collectors.toCollection(ArrayList::new));

        MyDetectedObject myObj;
        StringBuilder alert = new StringBuilder();
        for (int i = 0; i < myDetectedObjects.size(); i++) {

            if (myDetectedObjects.get(i) == null)
                continue;

            myObj = myDetectedObjects.get(i);
            if (initiated || !myObj.isAlerted()) {
                assert myObj != null;
                Detector.Recognition tmpObj = myObj.getLiveObject();
                alert.append(tmpObj.getTitle()).append(" ").append((int) distanceCalcViaWidth(tmpObj)).append("meter ").append(getPos(tmpObj)).append(" ");
                myObj.setAlerted(true);
                myDetectedObjects.set(i, myObj);
            }
        }
        updateVibrateMotors();
        textToSpeech.speak(alert.toString());
        atomicLiveObjects.put(currentKey, new HashSet<>(myDetectedObjects.stream().map(MyAtomicRef::new).collect(Collectors.toList())));
        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastAlert;
        Log.i("ptttTime", "run: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
        lastAlert = currentTime;
    }

    /**
     * update motors to work according to distance and position
     * R/C/L for the Vibration motor index
     * x/y/z for the speed of the vibration
     * 1/0 on and off
     */
    private void updateVibrateMotors() {
        String currentKey = getCurrentKey();
        if (atomicLiveObjects.get(currentKey) == null || Objects.requireNonNull(atomicLiveObjects.get(currentKey)).isEmpty()) {
            Log.i("ptttTime", "exit: " + atomicLiveObjects.toString());
            return;
        }
        int distanceLevel;
        ArrayList<MyDetectedObject> myDetectedObjects = Objects.requireNonNull(atomicLiveObjects.get(currentKey))
                .stream().map(AtomicReference::get)
                .collect(Collectors.toCollection(ArrayList::new));

        MyDetectedObject obj_to_signal = null;
        int distance_min = MAX_DISTANCE_LEVEL;
        for (MyDetectedObject obj : myDetectedObjects) {
            distanceLevel = (int) (2 * distanceCalcViaHeight(obj.getLiveObject()));
            if(distance_min > distanceLevel){
                distance_min = distanceLevel;
                obj_to_signal = obj;
            }
        }

        // send array to motors
        if(ESP32.getInstance() != null && ESP32.getInstance().isConnected()) {
            ESP32.getInstance().sendMessage(generateSignal(obj_to_signal, distance_min));
        }
    }

    private String generateSignal(MyDetectedObject obj, int distance){
        String signal = "";
        if(obj.getPos() == Position.LEFT)
            signal += LEFT_CHAR;
        else if(obj.getPos() == Position.CENTER)
            signal += CENTER_CHAR;
        else if(obj.getPos() == Position.RIGHT)
            signal += RIGHT_CHAR;

        signal += Labels_info.distanceLevels.get(distance);
        signal += MOTOR_ON_SIGNAL;
        return signal;
    }

    private void initAlertRunnable() {

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                alertCalculation(false);
            }
        }, 0, MILLISECONDS_ALERT_DELAY);

    }

    public void addObjects(HashSet<MyDetectedObject> objCollection) {
        String currentKey = getCurrentKey();
        Log.i("ptttAzimuth", "addObjects: " + currentKey);
        HashSet<MyAtomicRef> currentLiveObjects = atomicLiveObjects.get(currentKey);

        if (currentLiveObjects == null)
            currentLiveObjects = new HashSet<>();

        if (objCollection.isEmpty()) {
            Log.i("holdobj", "addObjects: ");
            atomicLiveObjects.put(currentKey, new HashSet<>(currentLiveObjects.stream()
                    .filter(obj -> TimeUnit.SECONDS.convert(System.nanoTime() - obj.get().getTimeStamp(), TimeUnit.NANOSECONDS) < 7)
                    .collect(Collectors.toList())));
            return;
        }

        ArrayList<MyDetectedObject> list = new ArrayList<>(objCollection);
        Log.i("ptttalertequalEB", "addObjects:" + list);

        if (currentLiveObjects.isEmpty()) {
            Log.i("ptttaddObjects", "isEmpty: ");
            currentLiveObjects.addAll(list.stream().map(MyAtomicRef::new)
                    .collect(Collectors.toList()));
            atomicLiveObjects.put(currentKey, currentLiveObjects);
            return;
        }

        if (currentLiveObjects.size() < ObjectManager_SIZE) {

            int limit = Math.min(ObjectManager_SIZE - currentLiveObjects.size(), list.size());

            currentLiveObjects
                    .addAll(list.stream().
                            limit(limit)
                            .map(MyAtomicRef::new)
                            .collect(Collectors.toList()));

            list.removeAll(list.subList(0, limit));
        }

        Log.i("ptttalertequalEA", "addObjects:" + currentLiveObjects);
        ArrayList<MyDetectedObject> aliveObjects = currentLiveObjects.stream()
                .map(AtomicReference::get)
                .collect(Collectors.toCollection(ArrayList::new));

        /*Check what objects need to replace and what new objects to add*/
        Log.i("ptttaddObjects", "addObjects: bef atomiclist" + aliveObjects.toString());
        boolean[] keepOld = new boolean[aliveObjects.size()];
        boolean[] addNew = new boolean[list.size()];
        Arrays.fill(addNew, Boolean.TRUE);
        MyDetectedObject aliveObj, newObj;
        for (int i = 0; i < aliveObjects.size(); i++) {
            aliveObj = aliveObjects.get(i);

            for (int j = 0; j < list.size(); j++) {
                newObj = list.get(j);

                if (aliveObj.equals(newObj)) {
                    newObj.setAlerted(true);
                    aliveObjects.set(i, newObj);
                    keepOld[i] = true;
                    addNew[j] = false;
                }
            }
        }

        /*Add new objects instead irrelevant old objects*/
        for (int i = 0; i < aliveObjects.size(); i++)
            if (!keepOld[i]) {
                int j;
                for (j = 0; j < addNew.length; j++)
                    if (addNew[j]) {
                        aliveObjects.set(i, list.get(j));
                        break;
                    }
                /*if inner loop finish full run -> all new objects added*/
                if (j >= addNew.length)
                    break;
            }

        currentLiveObjects.clear();
        currentLiveObjects.addAll(aliveObjects.stream()
                .filter(obj -> TimeUnit.SECONDS.convert(System.nanoTime() - obj.getTimeStamp(), TimeUnit.NANOSECONDS) < OBJECT_HELD_TIME)
                .map(MyAtomicRef::new).collect(Collectors.toList()));
        atomicLiveObjects.put(currentKey, currentLiveObjects);
    }

    public static void setAzimuthIndex(int azimuthIndex) {
        if (ObjectsManager.azimuthIndex != null)
            ObjectsManager.azimuthIndex.set(azimuthIndex);
    }

    public static void setPitchIndex(int pitchIndex) {
        if (ObjectsManager.pitchIndex != null)
            ObjectsManager.pitchIndex.set(pitchIndex);
        Log.i("setPitchIndex", "setPitchIndex: " + pitchIndex);
    }

    private String getCurrentKey() {
        return Integer.toString(pitchIndex.get()) + azimuthIndex.get();
    }
}


