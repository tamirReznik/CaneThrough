package org.tensorflow.lite.examples.detection.tracking;

public interface Distance_CallBack {
    void person(final float distance);
    void mouse(final float distance);

}
