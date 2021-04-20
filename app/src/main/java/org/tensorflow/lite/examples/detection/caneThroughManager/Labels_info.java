package org.tensorflow.lite.examples.detection.caneThroughManager;

import org.tensorflow.lite.examples.detection.caneThroughManager.Labels_Keys;

import java.util.HashMap;
import java.util.Map;

public interface Labels_info {
    Map<String, float[]> label_val = new HashMap<String, float[]>() {{
        put(Labels_Keys.PERSON, new float[]{50f, 320f});
        put(Labels_Keys.MOUSE , new float[]{6.5f, 263.621f});
    }};
    Map<String, Float> objectHeight = new HashMap<String, Float>() {{
        put(Labels_Keys.PERSON, 1750f);
        put(Labels_Keys.PERSON_FACE, 200f);
        put(Labels_Keys.BED, 1750f);
        put(Labels_Keys.TABLE, 650f);
        put(Labels_Keys.CAR, 500f);
        put(Labels_Keys.CHAIR, 900f);
        put(Labels_Keys.BENCH, 550f);
        put(Labels_Keys.MOUSE, 100f);
    }};
    Map<String, Float> objectWidth = new HashMap<String, Float>() {{
        put(Labels_Keys.PERSON, 380f);
        put(Labels_Keys.PERSON_FACE, 180f);
        put(Labels_Keys.BED, 1650f);
        put(Labels_Keys.TABLE, 1050f);
        put(Labels_Keys.CAR, 4500f);
        put(Labels_Keys.CHAIR, 400f);
        put(Labels_Keys.BENCH, 1400f);
        put(Labels_Keys.MOUSE, 100f);
    }};
}
