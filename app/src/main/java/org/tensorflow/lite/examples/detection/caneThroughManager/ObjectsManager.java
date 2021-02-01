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
import android.os.SystemClock;
import android.util.Log;
import android.util.SizeF;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private void initAlertRunnable() {


        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
//                Log.i("pttt", "run: TimerTask id: " + Thread.currentThread().getId());

//                MyAtomicRef[] myDetectedObject = new MyAtomicRef[atomicLiveObjects.size()];
                ArrayList<MyAtomicRef> myDetectedObject = new ArrayList<>(atomicLiveObjects);

//                Log.i("pttt", "run: myDetectedObject size: "+myDetectedObject.size()+" atomicLiveObjects.size(): " + atomicLiveObjects.size());

                MyDetectedObject myObj;
//                int i = 0;
                for (int i = 0; i < myDetectedObject.size(); i++) {

                    if (myDetectedObject.get(i) == null) {
                        Log.i("pttt", "run: object null");
                        return;
                    }
                    myObj = myDetectedObject.get(i).get();
                    if (!myObj.isAlerted()) {
                        Detector.Recognition tmpObj = myObj.getLiveObject();
                        Log.i("pttt", "run: alert : " + myObj.toString());
                        // position[0].name();
                        if (tmpObj.getTitle().equals(Labels_Keys.PERSON)) {
                            textToSpeech.speak(tmpObj.getTitle() + " " + distanceCalc(Labels_info.objectHeight.get(Labels_Keys.PERSON),
                                    tmpObj.getLocation().height()) + "meter " + getPos(tmpObj));

                        }
                        myObj.setAlerted(true);

                    }
                    myDetectedObject.set(i,new MyAtomicRef(myObj))  ;
                }

//                for (int i = 0; i < myDetectedObject.length; i++) {
//                    myObj = myDetectedObject[i].get();
//                    if (!myObj.isAlerted()) {
//                        Detector.Recognition tmpObj = myObj.getLiveObject();
//                        // position[0].name();
//                        if (tmpObj.getTitle().equals(Labels_Keys.PERSON)) {
//                            textToSpeech.speak(tmpObj.getTitle() + " " + distanceCalc(Labels_info.objectHeight.get(Labels_Keys.PERSON),
//                                    tmpObj.getLocation().height()) + "meter " + getPos(tmpObj));
//
//                        }
//                        myObj.setAlerted(true);
//
//                    }
//                    myDetectedObject[i] = new MyAtomicRef(myObj);
//                }
                atomicLiveObjects.addAll(myDetectedObject);
            }
        }, 0, 3000);

//      new Runnable() {
//            @Override
//            public void run() {
//                Log.i("pttt", "run: alerts array:" + Arrays.toString(alerted));
//                for (int i = 0; i < liveObjects.size(); i++) {
//                    if (!alerted[i]) {
//                        Detector.Recognition tmpObj = liveObjects.get(i);
//                        Log.i("handlerTag", "run: " + tmpObj.toString());
//                        // position[0].name();
//                        if (tmpObj.getTitle().equals(Labels_Keys.PERSON)) {
//                            textToSpeech.speak(tmpObj.getTitle() + " " + distanceCalc(Labels_info.objectHeight.get(Labels_Keys.PERSON),
//                                    tmpObj.getLocation().height()) + "meter " + getPos(tmpObj));
//                            Log.i("handlerTag", "run: " + tmpObj.getLocation().centerX() + " pos: " + getPos(tmpObj));
//                        }
//                        alerted[i] = true;
//                    }
//                }
//                handler.postDelayed(this, 4000);
//            }
//
//        }.run();


    }

    public void addObjects(List<Detector.Recognition> list) {
//        Log.i("listSize", "addObjects: " + liveObjects.size());
        if (list.isEmpty()) {
            return;
        }

//        ArrayList<Detector.Recognition> liveObjects = atomicLiveObjects.stream().map(obj -> obj.get().getLiveObject()).collect(Collectors.toCollection(ArrayList::new));
        ArrayList<MyDetectedObject> aliveObjects = atomicLiveObjects.stream()
                .map(AtomicReference::get)
                .collect(Collectors.toCollection(ArrayList::new));

//        Log.i("pttt", "addObjects: alive Size" + aliveObjects.size());

        if (aliveObjects.size() == 0) {
            aliveObjects.addAll(list.stream()
                    .map(obj -> new MyDetectedObject(obj, false, getPos(obj)))
                    .collect(Collectors.toList()));
            //  updateLiveObjPos();
//            return;
        }

        if (aliveObjects.size() < list.size()) {
            aliveObjects.addAll(IntStream.range(0, list.size() - aliveObjects.size())
                    .mapToObj(i -> new MyDetectedObject(list.get(i), false, getPos(list.get(i))))
                    .collect(Collectors.toList()));
//            liveObjects.addAll(list.subList(0, list.size() - liveObjects.size()));
            // updateLiveObjPos();
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
//                        alerted[i] = false;
                    }
                }
            }

        }

        Log.i("pttt", "addObjects: size" + atomicLiveObjects.size());
        atomicLiveObjects.clear();
        atomicLiveObjects.addAll(aliveObjects.stream().map(MyAtomicRef::new).collect(Collectors.toList()));

        Log.i("pttt", "addObjects: aft atomiclist" + Arrays.toString(atomicLiveObjects.toArray()));
//        SystemClock.sleep(3000);

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


