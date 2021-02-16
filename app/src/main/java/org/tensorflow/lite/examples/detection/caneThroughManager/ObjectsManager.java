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
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

//TODO - prevent duplicates of objects in liveobjects
// fix concurrent data structures
public class ObjectsManager {

    public static final int ObjectManager_SIZE = 3;


    enum Position {
        LEFT,
        CENTER,
        RIGHT,
        UNKNOWN
    }

    private Float focalLength;
    private Float heightSensor, widthSensor;
    private Float imageHeight, imageWidth;
    private final Handler handler;
    private final Context context;
    private boolean[] alerted;
    private static ObjectsManager instance;
    private TTSManager textToSpeech;
    private Timer timer;
//    private ArrayList<Detector.Recognition> liveObjects;


    //    ArrayList<AtomicReference<MyDetectedObject>> atomicLiveObjects;
    HashSet<MyAtomicRef> atomicLiveObjects;


    public static ObjectsManager getInstance() {
        return instance;
    }


    public static void init(Context context, String cameraId) {
        Log.i("handlerTag", "run: ");
        if (instance == null) {
            instance = new ObjectsManager(context, cameraId);
        }
    }

    private ObjectsManager(Context context, String cameraId) {

//        atomicLiveObjects = new ArrayList<>();

        atomicLiveObjects = new HashSet<>();
//        liveObjects = new ArrayList<>();
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
                ArrayList<MyAtomicRef> myDetectedObjects = new ArrayList<>(atomicLiveObjects);

                MyDetectedObject myObj;

                for (int i = 0; i < myDetectedObjects.size(); i++) {

                    if (myDetectedObjects.get(i) == null) {
                        return;
                    }
                    myObj = myDetectedObjects.get(i).get();
                    if (!myObj.isAlerted()) {
                        Detector.Recognition tmpObj = myObj.getLiveObject();
                        if (/*tmpObj.getTitle().equals(Labels_Keys.PERSON)||*/ Labels_info.objectHeight.containsKey(tmpObj.getTitle())) {
                            playAlert(tmpObj);
                        }
                        myObj.setAlerted(true);
                    }
                    myDetectedObjects.set(i, new MyAtomicRef(myObj));
                }
                atomicLiveObjects.addAll(myDetectedObjects);
            }
        }, 0, 3000);

    }

    void playAlert(Detector.Recognition tmpObj) {

        textToSpeech.speak(tmpObj.getTitle() + " " + /*distanceCalc(Labels_info.objectHeight.get(tmpObj.getTitle()),
                tmpObj.getLocation().height())*/distanceCalc(tmpObj) + "meter " + getPos(tmpObj));
    }

    public void addObjects(List<Detector.Recognition> list) {
        if (list.isEmpty()) {
            return;
        }
        ArrayList<MyDetectedObject> aliveObjects = atomicLiveObjects.stream()
                .map(AtomicReference::get)
                .collect(Collectors.toCollection(ArrayList::new));

//        if (aliveObjects.size() == 0) {
//            aliveObjects.addAll(list.stream()
//                    .map(obj -> new MyDetectedObject(obj, false, getPos(obj)))
//                    .collect(Collectors.toList()));
//            return;
//        }

        if (aliveObjects.size() < ObjectManager_SIZE) {
            aliveObjects.addAll(list.stream().limit(Math.min(ObjectManager_SIZE - aliveObjects.size(), list.size()))
                    .map(recognition -> new MyDetectedObject(recognition, false, getPos(recognition)))
                    .collect(Collectors.toList()));
        }

        Log.i("pttt", "addObjects: bef atomiclist" + aliveObjects.toString());
        boolean[] keepOld = new boolean[aliveObjects.size()];
        boolean[] addNew = new boolean[list.size()];
        Arrays.fill(addNew, Boolean.TRUE);
        for (int i = 0; i < aliveObjects.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                Detector.Recognition tempObj = aliveObjects.get(i).getLiveObject();
                if (tempObj.getTitle().equals(list.get(j).getTitle())/* && getPos(tempObj).equals(getPos(list.get(j)))*/) {
                    keepOld[i] = true;
                    addNew[j] = false;
                }
            }
        }

        for (int i = 0; i < aliveObjects.size(); i++) {
            if (!keepOld[i]) {
                for (int j = 0; j < addNew.length; j++) {
                    if (addNew[j]) {
                        aliveObjects.set(i, new MyDetectedObject(list.get(j), false, getPos(list.get(j))));
                    }
                }
            }

        }

        Log.i("pttt", "addObjects: size" + aliveObjects.size());
        atomicLiveObjects.clear();
        atomicLiveObjects.addAll(aliveObjects.stream().map(MyAtomicRef::new).collect(Collectors.toList()));

        Log.i("pttt", "addObjects: aft atomiclist" + Arrays.toString(atomicLiveObjects.toArray()));
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


