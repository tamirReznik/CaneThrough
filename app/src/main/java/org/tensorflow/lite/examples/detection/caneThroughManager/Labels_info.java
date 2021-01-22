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
        put(Labels_Keys.PERSON, 180f);
    }};
}
