/**
 * <h1>Object Manager</h1>
 * The Object Manager is a singleton class that manages Arraylist of Detector.Recognition (liveObjects).
 * The Object Manager's purpose is to keep the liveObjects updated so the system could notify the user in real-time objects in his environment.
 */


package org.tensorflow.lite.examples.detection.caneThroughManager;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.ArrayList;
import java.util.List;

public class ObjectsManager {
    private Context context;
    private Handler handler;
    private boolean[] alerted;
    private static ObjectsManager instance;
    private ArrayList<Detector.Recognition> liveObjects;

    public static ObjectsManager getInstance() {
        return instance;
    }

    private ObjectsManager(Context _context) {
        context = _context;
        liveObjects = new ArrayList<>();
        alerted = new boolean[5];
        handler = new Handler();
        alert.run();


    }

    public static void init(Context _context) {
        Log.i("handlerTag", "run: ");
        if (instance == null) {
            instance = new ObjectsManager(_context);
        }
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
                if (liveObjects.get(i).getTitle().equals(list.get(j).getTitle())) {
                    if (liveObjects.get(i).getLocation().centerX() <= 80 && list.get(j).getLocation().centerX() <= 80) {
                        keepOld[i] = true;
                        removeNew[j] = true;
                        continue;
                    }
                    if (liveObjects.get(i).getLocation().centerX() >= 220 && list.get(j).getLocation().centerX() >= 220) {
                        keepOld[i] = true;
                        removeNew[j] = true;
                        continue;
                    }
                    if ((liveObjects.get(i).getLocation().centerX() > 80 && liveObjects.get(i).getLocation().centerX() < 220)
                            && (list.get(j).getLocation().centerX() > 80 && list.get(j).getLocation().centerX() < 220)) {
                        keepOld[i] = true;
                        removeNew[j] = true;

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


    private Runnable alert = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < liveObjects.size(); i++) {
                if (!alerted[i]) {
                    Log.i("handlerTag", "run: " + liveObjects.get(i).toString());
                    alerted[i] = true;
                }
            }
            handler.postDelayed(this, 3000);
        }
    };

}
