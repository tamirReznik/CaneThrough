package org.tensorflow.lite.examples.detection.caneThroughManager;

import java.util.HashMap;
import java.util.Map;

public interface Labels_info {

    String CANE_THROUGH_LOG = "CT_log";
    String PERSON = "person";
    String PERSON_FACE = "person ";
    String MOUSE = "mouse";
//    String CHAIR = "chair";
//    String TABLE = "dining table";
//    String BED = "bed";
//    String BENCH = "bench";
//    String CAR = "car";


    Map<String, float[]> label_val = new HashMap<String, float[]>() {{
        put(PERSON, new float[]{50f, 320f});
        put(MOUSE, new float[]{6.5f, 263.621f});
    }};

//must contain exactly same keys of objectWith
    Map<String, Float> objectHeight = new HashMap<String, Float>() {{
        put("person", 1750f);
        put("person ", 200f);
        put("bed", 1750f);
        put("dining table", 650f);
        put("car", 500f);
        put("chair", 900f);
        put("bench", 550f);
        put("mouse", 100f);
    }};
    Map<String, Float> objectWidth = new HashMap<String, Float>() {{
        put("person", 380f);
        put("person ", 180f);
        put("bed", 1650f);
        put("dining table", 1050f);
        put("car", 4500f);
        put("chair", 400f);
        put("bench", 1400f);
        put("mouse", 100f);
    }};

    Map<Integer, Character> distanceLevels = new HashMap<Integer, Character>() {{
        put(0, 'x');
        put(1, 'x');
        put(2, 'y');
        put(3, 'y');
        put(4, 'z');
        put(5, 'z');
    }};

}


