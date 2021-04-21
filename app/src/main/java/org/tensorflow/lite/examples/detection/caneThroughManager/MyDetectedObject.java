package org.tensorflow.lite.examples.detection.caneThroughManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.Objects;

public class MyDetectedObject {


    private Detector.Recognition liveObject;
    private boolean alerted;
    private ObjectsManager.Position pos;



    private long timeStamp;

    public MyDetectedObject(Detector.Recognition liveObject, boolean alerted, ObjectsManager.Position pos) {
        this.liveObject = liveObject;
        this.alerted = alerted;
        this.pos = pos;
        timeStamp = System.nanoTime();
    }

    public ObjectsManager.Position getPos() {
        return pos;
    }

    public void setPos(ObjectsManager.Position pos) {
        this.pos = pos;
    }

    public boolean isAlerted() {
        return alerted;
    }

    public void setAlerted(boolean alerted) {
        this.alerted = alerted;
    }

    public Detector.Recognition getLiveObject() {
        return liveObject;
    }

    public void setLiveObject(Detector.Recognition liveObject) {
        this.liveObject = liveObject;
    }

    @NonNull
    @Override
    public String toString() {
        return liveObject.toString() + " alerted: " + isAlerted() + " position: " + getPos();
    }

    @Override
    public int hashCode() {
        return Objects.hash(liveObject.getTitle().hashCode(), pos.name().hashCode());
    }

    @Override
    public boolean equals(@Nullable Object obj) {

        assert obj != null;
        if (getClass() != obj.getClass())
            return false;

        return this.hashCode() == obj.hashCode();
    }

    public long getTimeStamp() {
        return timeStamp;
    }
}
