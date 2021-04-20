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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    public static final int ObjectManager_SIZE = 3;
    public static final int LANDSCAPE_FRAME_ANGLE_DEGREE = 18;
    public static final int PITCH_FRAME_ANGLE_DEGREE = 4;

    private static AtomicInteger azimuthIndex, pitchIndex;
    private static ObjectsManager instance;
    private final Handler handler;
    private final Context context;
    HashMap<String, HashSet<MyAtomicRef>> atomicLiveObjects;
    private Float focalLength;
    private Float heightSensor, widthSensor;
    private Float imageHeight, imageWidth;
    private TTSManager textToSpeech;
    private Timer timer;
    private long lastAlert;

    private ObjectsManager(Context context, String cameraId) {

        atomicLiveObjects = new HashMap<>();

        handler = new Handler(Looper.myLooper());

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

    public static Position getPos(Detector.Recognition obj) {

        if (obj.getLocation().centerX() <= 512) {
            return Position.LEFT;
        } else if (obj.getLocation().centerX() >= 1048) {
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
     * realHeight - object height in mm
     * pixHeight  - object height in pixel
     *
     * @param detectedObject - Detector.Recognition object to calculate distance from
     * @return - Estimated distance in meters
     */
    private double distanceCalc(Detector.Recognition detectedObject) {
//todo - check ratio between height and width for person

        float realHeight = Labels_info.objectHeight.get(detectedObject.getTitle());
        float pixHeight = detectedObject.getLocation().height();

        if (Labels_Keys.PERSON.equals(detectedObject.getTitle()) && pixHeight / detectedObject.getLocation().width() > 2) {
            realHeight = Labels_info.objectHeight.get(Labels_Keys.PERSON_FACE);
        }

        pixHeight = detectedObject.getLocation().height();

//        return distance in meter's
        float result = (focalLength * realHeight * imageHeight) / (heightSensor * pixHeight) / 1000;
        Log.i(Labels_Keys.CANE_THROUGH_LOG, "distanceCalc: " + result + " focal:" + focalLength + " real height: " + realHeight + " imageH: " + imageHeight + " heightSensor: " + heightSensor + " pixH:" + pixHeight + " pixW:" + detectedObject.getLocation().width());
        return result;

    }

    private void alertCalculation() {
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

            if (!(myObj = myDetectedObjects.get(i)).isAlerted()) {
                Detector.Recognition tmpObj = myObj.getLiveObject();
                alert.append(tmpObj.getTitle()).append(" ").append((int) distanceCalc(tmpObj)).append("meter ").append(getPos(tmpObj)).append(" ");
                myObj.setAlerted(true);
                myDetectedObjects.set(i, myObj);
            }
        }
        textToSpeech.speak(alert.toString());
        atomicLiveObjects.put(currentKey, new HashSet<>(myDetectedObjects.stream().map(MyAtomicRef::new).collect(Collectors.toList())));
        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastAlert;
        Log.i("ptttTime", "run: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
        lastAlert = currentTime;
    }

    /**
     * update motors to work according to distance and position
     * motorsRate[0] -> contains Left object distance from camera - update left motor
     * motorsRate[1] -> contains Center object distance from camera - update Center motor
     * motorsRate[2] -> contains Right object distance from camera - update Right motor
     */
    private void updateVibrateMotors() {
        String currentKey = getCurrentKey();
        if (atomicLiveObjects.get(currentKey) == null || Objects.requireNonNull(atomicLiveObjects.get(currentKey)).isEmpty()) {
            Log.i("ptttTime", "exit: " + atomicLiveObjects.toString());
            return;
        }
        int[] motorsRate = {MAX_DISTANCE_LEVEL, MAX_DISTANCE_LEVEL, MAX_DISTANCE_LEVEL};
        int distanceLevel = Integer.MAX_VALUE;
        ArrayList<MyDetectedObject> myDetectedObjects = Objects.requireNonNull(atomicLiveObjects.get(currentKey))
                .stream().map(AtomicReference::get)
                .collect(Collectors.toCollection(ArrayList::new));

        for (MyDetectedObject obj : myDetectedObjects) {
            distanceLevel = (int) (2 * distanceCalc(obj.getLiveObject()));
            if (obj.getPos() == Position.LEFT) {
                motorsRate[0] = Math.min(motorsRate[0], distanceLevel);
            }
            if (obj.getPos() == Position.CENTER) {
                motorsRate[1] = Math.min(motorsRate[0], distanceLevel);
            }
            if (obj.getPos() == Position.RIGHT) {
                motorsRate[2] = Math.min(motorsRate[0], distanceLevel);
            }

        }
        // send array to motors
    }

    public void initiatedAlert() {
        String currentKey = getCurrentKey();
        if (atomicLiveObjects.get(currentKey) == null || Objects.requireNonNull(atomicLiveObjects.get(currentKey)).isEmpty()) {
            Log.i("ptttTime", "exit: " + atomicLiveObjects.toString());
            return;
        }
        ArrayList<MyDetectedObject> myDetectedObjects = Objects.requireNonNull(atomicLiveObjects.get(currentKey)).stream().map(AtomicReference::get).collect(Collectors.toCollection(ArrayList::new));

        MyDetectedObject myObj;
        StringBuilder alert = new StringBuilder();
        for (int i = 0; i < myDetectedObjects.size(); i++) {

            if (myDetectedObjects.get(i) == null) {
                continue;
            }
            myObj = myDetectedObjects.get(i);
            Detector.Recognition tmpObj = myObj.getLiveObject();
            alert
                    .append(tmpObj.getTitle())
                    .append(" ")
                    .append((int) distanceCalc(tmpObj))
                    .append("meter ")
                    .append(getPos(tmpObj))
                    .append(" ");
        }
        textToSpeech.speak(alert.toString());
    }

    private void initAlertRunnable() {

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                alertCalculation();
            }
        }, 0, 3010);

    }

    void playAlert2(List<Detector.Recognition> tmpObjList) {

        if (tmpObjList == null || tmpObjList.isEmpty())
            return;

        switch (tmpObjList.size()) {
            case 1:
                textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0)));
                Log.i("ptttalert", "playAlert: " + tmpObjList + " pos: " + getPos(tmpObjList.get(0)) + " center: " + tmpObjList.get(0).getLocation().centerX());
                break;
            case 2:
                if (tmpObjList.get(0).getTitle().equals(tmpObjList.get(1).getTitle())) {
                    textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0))
                            + " And " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1)));

                } else {
                    textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0))
                            + " And " + tmpObjList.get(1).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1)));
                }
                break;
            case 3:
                if (tmpObjList.get(0).getTitle().equals(tmpObjList.get(1).getTitle())) {
                    if (tmpObjList.get(0).getTitle().equals(tmpObjList.get(2).getTitle())) {
                        textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0))
                                + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1))
                                + " And " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1)));
                    } else {
                        textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0))
                                + " " + tmpObjList.get(1).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1))
                                + " And " + tmpObjList.get(2).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(2)) + "meter " + getPos(tmpObjList.get(2)));
                    }
                } else if (tmpObjList.get(0).getTitle().equals(tmpObjList.get(2).getTitle())) {
                    textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0))
                            + " " + tmpObjList.get(2).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(2)) + "meter " + getPos(tmpObjList.get(2))
                            + " And " + tmpObjList.get(1).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1)));

                } else if (tmpObjList.get(1).getTitle().equals(tmpObjList.get(2).getTitle())) {
                    textToSpeech.speak(tmpObjList.get(1).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1))
                            + " " + tmpObjList.get(2).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(2)) + "meter " + getPos(tmpObjList.get(2))
                            + " And " + tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0)));
                } else {
                    textToSpeech.speak(tmpObjList.get(1).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(1)) + "meter " + getPos(tmpObjList.get(1))
                            + " " + tmpObjList.get(2).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(2)) + "meter " + getPos(tmpObjList.get(2))
                            + " And " + tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0)));
                }


                break;
        }

        textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0)));
        Log.i("ptttalert", "playAlert: " + tmpObjList + " pos: " + getPos(tmpObjList.get(0)) + " center: " + tmpObjList.get(0).getLocation().centerX());

        textToSpeech.speak(tmpObjList.get(0).getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObjList.get(0)) + "meter " + getPos(tmpObjList.get(0)));
        Log.i("ptttalert", "playAlert: " + tmpObjList + " pos: " + getPos(tmpObjList.get(0)) + " center: " + tmpObjList.get(0).getLocation().centerX());
    }


    public void addObjects(HashSet<MyDetectedObject> objCollection) {
        String currentKey = getCurrentKey();
        Log.i("ptttAzimuth", "addObjects: " + currentKey);
        HashSet<MyAtomicRef> currentLiveObjects = atomicLiveObjects.get(currentKey);

        if (currentLiveObjects == null)
            currentLiveObjects = new HashSet<>();

        if (objCollection.isEmpty())
            return;

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

        Log.i("ptttaddObjects", "addObjects: bef atomiclist" + aliveObjects.toString());
        boolean[] keepOld = new boolean[aliveObjects.size()];
        boolean[] addNew = new boolean[list.size()];
        Arrays.fill(addNew, Boolean.TRUE);
        MyDetectedObject aliveObj, newObj;
        for (int i = 0; i < aliveObjects.size(); i++) {
            Log.i("ptttaddObjects", "addObjects: index: " + i);
            aliveObj = aliveObjects.get(i);
            for (int j = 0; j < list.size(); j++) {
                newObj = list.get(j);

                Log.i("ptttaddObjects", "addObjects: obj:" + aliveObj);
                if (aliveObj.equals(newObj)) {
                    Log.i("ptttaddObjects", "addnewObjects: " + aliveObj);
                    newObj.setAlerted(true);
                    aliveObjects.set(i, newObj);
                    keepOld[i] = true;
                    addNew[j] = false;
                }
            }
        }

        for (int i = 0; i < aliveObjects.size(); i++)
            if (!keepOld[i]) {
                int j;
                for (j = 0; j < addNew.length; j++)
                    if (addNew[j]) {
                        aliveObjects.set(i, list.get(j));
                        break;
                    }

                if (j >= addNew.length)
                    break;
            }

        currentLiveObjects.clear();
        currentLiveObjects.addAll(aliveObjects.stream()
                .filter(obj -> TimeUnit.SECONDS.convert(System.nanoTime() - obj.getTimeStamp(), TimeUnit.NANOSECONDS) < 16)
                .map(MyAtomicRef::new).collect(Collectors.toList()));
        atomicLiveObjects.put(currentKey, currentLiveObjects);
        Log.i("ptttMap", "addObjects: aft atomiclist");
        Log.i("ptttMap", "addObjects: aft atomiclist" + atomicLiveObjects.toString());
        Log.i("pttt", "addObjects: aft atomiclist" + Arrays.toString(currentLiveObjects.toArray()));
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


