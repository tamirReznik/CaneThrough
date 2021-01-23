package org.tensorflow.lite.examples.detection.caneThroughManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DetectedObject {

    private String name;
    private ObjectsManager.Position position;

    public DetectedObject(String name, ObjectsManager.Position position) {
        this.name = name;
        this.position = position;
    }

    public DetectedObject() {
    }

    public String getName() {
        return name;
    }

    public ObjectsManager.Position getPosition() {
        return position;
    }

    @Override
    public int hashCode() {
        return (this.toString()).hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return (obj instanceof DetectedObject) && (((DetectedObject) obj).toString()).equals(this.toString());
    }

    @NonNull
    @Override
    public String toString() {
        return name + " meter from " + position;
    }
}
