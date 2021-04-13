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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

//TODO - prevent duplicates of objects in liveobjects
// fix concurrent data structures
public class ObjectsManager {
    enum Position {
        LEFT,
        CENTER,
        RIGHT,
        UNKNOWN
    }

    public static final int ObjectManager_SIZE = 3;


    private static int azimuthIndex = -1;
    private static ObjectsManager instance;
    private final Handler handler;
    private final Context context;
    //    ArrayList<AtomicReference<MyDetectedObject>> atomicLiveObjects;
    HashSet<MyAtomicRef> atomicLiveObjects;
    //HashMap<Integer,MyAtomicRef>atomicLiveObjects;
    private Float focalLength;
    private Float heightSensor, widthSensor;
    private Float imageHeight, imageWidth;
    private final boolean[] alerted;
    private TTSManager textToSpeech;
    private Timer timer;
    private long lastAlert;
//    private ArrayList<Detector.Recognition> liveObjects;


    private ObjectsManager(Context context, String cameraId) {

        atomicLiveObjects = new HashSet<>();
        alerted = new boolean[ObjectManager_SIZE];
        handler = new Handler(Looper.myLooper());

        azimuthIndex = 0;
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

    private int distanceCalc(Detector.Recognition tmpObj) {
//todo - check ratio between height and width for person

        float realHeight = Labels_info.objectHeight.get(tmpObj.getTitle()), pixHeight = tmpObj.getLocation().height();
        if (Labels_Keys.PERSON.equals(tmpObj.getTitle()) && tmpObj.getLocation().height() / tmpObj.getLocation().width() > 2) {
            realHeight = Labels_info.objectHeight.get(Labels_Keys.PERSON_FACE);
        }

        pixHeight = tmpObj.getLocation().height();

//        return distance in meter's
        float result = (focalLength * realHeight * imageHeight) / (heightSensor * pixHeight) / 1000;
        Log.i(Labels_Keys.CANE_THROUGH_LOG, "distanceCalc: " + result + " focal:" + focalLength + " real height: " + realHeight + " imageH: " + imageHeight + " heightSensor: " + heightSensor + " pixH:" + pixHeight + " pixW:" + tmpObj.getLocation().width());
        return (int) result;

    }

    private void initAlertRunnable() {

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                ArrayList<MyDetectedObject> myDetectedObjects = atomicLiveObjects.stream().map(AtomicReference::get).collect(Collectors.toCollection(ArrayList::new));

                MyDetectedObject myObj;
                StringBuilder alert = new StringBuilder();
                for (int i = 0; i < myDetectedObjects.size(); i++) {

                    if (myDetectedObjects.get(i) == null) {
                        continue;
                    }

                    if (!(myObj = myDetectedObjects.get(i)).isAlerted()) {
                        Detector.Recognition tmpObj = myObj.getLiveObject();
                        if (/*tmpObj.getTitle().equals(Labels_Keys.PERSON)||*/ Labels_info.objectHeight.containsKey(tmpObj.getTitle())) {
                            alert.append(tmpObj.getTitle()).append(" ").append( /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObj)).append("meter ").append(getPos(tmpObj)).append(" ");

//                             playAlert(tmpObj);
                        }
                        myObj.setAlerted(true);
                        myDetectedObjects.set(i, myObj);
                    }
                }
                textToSpeech.speak(alert.toString());
                atomicLiveObjects.addAll(myDetectedObjects.stream().map(MyAtomicRef::new).collect(Collectors.toList()));
                long currentTime = System.nanoTime();
                long elapsedTime = currentTime - lastAlert;
                Log.i("ptttTime", "run: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
                lastAlert = currentTime;
            }
        }, 0, 3010);

    }

    void startAlert(HashMap<String, List<MyDetectedObject>> objectsToAlert) {
        textToSpeech.speak("4");
        List<MyDetectedObject> currentType = new ArrayList<>();
        for (String key : objectsToAlert.keySet()) {
            Log.i("ptttAlert", "startAlert: " + objectsToAlert.keySet());
            currentType.clear();
            currentType = objectsToAlert.get(key);
            assert currentType != null;
            if (currentType.size() == 1) {
                textToSpeech.speak(currentType.iterator().next().getLiveObject().getTitle()
                        + " " + distanceCalc(currentType.iterator().next().getLiveObject())
                        + "meter " + getPos(currentType.iterator().next().getLiveObject()));
            }
            if (currentType.size() == 2) {
                textToSpeech.speak("A " + currentType.get(0).getLiveObject().getTitle()
                        + " At " + currentType.get(0).getPos()
                        + "And " + currentType.get(1).getPos());
            }
            if (currentType.size() == 3) {
                textToSpeech.speak("A " + currentType.get(0).getLiveObject().getTitle()
                        + " At " + currentType.get(0).getPos()
                        + " " + currentType.get(1).getPos()
                        + "And " + currentType.get(2).getPos());
            }
        }

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

    void playAlert(Detector.Recognition tmpObj) {

        textToSpeech.speak(tmpObj.getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObj) + "meter " + getPos(tmpObj));
        Log.i("ptttalert", "playAlert: " + tmpObj + " pos: " + getPos(tmpObj) + " center: " + tmpObj.getLocation().centerX());
    }

    public void addObjects(HashSet<MyDetectedObject> objCollection) {

        Log.i("ptttaddObjectsSet", "addObjects: hashset: " + objCollection.toString());
        if (objCollection.isEmpty()) {
            return;
        }

        ArrayList<MyDetectedObject> list = new ArrayList<>(objCollection);
        Log.i("ptttalertequalEB", "addObjects:" + list);

        if (atomicLiveObjects.isEmpty()) {
            Log.i("ptttaddObjects", "isEmpty: ");
            atomicLiveObjects.addAll(list.stream().map(MyAtomicRef::new)
                    .collect(Collectors.toList()));
                  /* .map(obj -> new MyAtomicRef(new MyDetectedObject(obj, false, getPos(obj))))
                    .collect(Collectors.toList()));*/
            return;
        }


        if (atomicLiveObjects.size() < ObjectManager_SIZE) {

            int limit = Math.min(ObjectManager_SIZE - atomicLiveObjects.size(), list.size());

            atomicLiveObjects
                    .addAll(list.stream().limit(limit).map(MyAtomicRef::new).collect(Collectors.toList()));
                          /*  .map(recognition -> new MyDetectedObject(recognition, false, getPos(recognition)))
                            .collect(Collectors.toList()));
*/
            list.removeAll(list.subList(0, limit));

        }
        Log.i("ptttalertequalEA", "addObjects:" + atomicLiveObjects);
        ArrayList<MyDetectedObject> aliveObjects = atomicLiveObjects.stream()
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

        for (int i = 0; i < aliveObjects.size(); i++) {
            if (!keepOld[i]) {
                int j;
                for (j = 0; j < addNew.length; j++) {
                    if (addNew[j]) {
                        aliveObjects.set(i, list.get(j));
                        break;
                    }
                }
                if (j >= addNew.length)
                    break;
            }

        }


        atomicLiveObjects.clear();
        atomicLiveObjects.addAll(aliveObjects.stream().map(MyAtomicRef::new).collect(Collectors.toList()));

        Log.i("pttt", "addObjects: aft atomiclist" + Arrays.toString(atomicLiveObjects.toArray()));
    }

    public static void setAzimuthIndex(int azimuthIndex) {
        ObjectsManager.azimuthIndex = azimuthIndex;
    }
}


