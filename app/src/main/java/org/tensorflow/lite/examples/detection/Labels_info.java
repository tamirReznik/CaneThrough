package org.tensorflow.lite.examples.detection;

import java.util.HashMap;
import java.util.Map;

public interface Labels_info {
    Map<String, float[]> label_val = new HashMap<String, float[]>() {{
        put("person", new float[]{50f, 320f});
        put("mouse", new float[]{6.5f, 263.621f});
    }};
}
