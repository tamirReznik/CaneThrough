package org.tensorflow.lite.examples.detection.caneThroughManager;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class TTSManager {

    private static TTSManager instance;
    private TextToSpeech tts;
    private Context context;

    public static TTSManager getInstance() {
        return instance;
    }

    private TTSManager(Context _context) {
        context = _context;

        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.ENGLISH);
                }
            }
        });
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new TTSManager(context);
        }
    }

    public void speak(CharSequence text, int queueMode) {

        if (tts.speak(text.toString(), queueMode, null) == -1)
            throw new RuntimeException("Text To Speech failed to speak");

    }

    public boolean isSpeaking() {
        return tts.isSpeaking();
    }
}
