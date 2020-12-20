package org.tensorflow.lite.examples.detection.caneThroughManager;

import android.speech.tts.TextToSpeech;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.Locale;

public class Utils {

    public static void soundReport(Detector.Recognition result, float d) {
        String side[] = {"on the left", "ahead", "on the right"};
        String mySide = "";
        if (result.getLocation().left < 100 && result.getLocation().right <= 150)
            mySide = side[0];
        else if (result.getLocation().right > 200 && result.getLocation().left >= 150)
            mySide = side[2];
        else
            mySide = side[1];

        d = d / 100;
        int dd = (int) d;
        String dis = String.format(Locale.getDefault(), "%d meters", dd);
        //final Handler h =new Handler();
        String finalMySide = mySide;
        //Runnable r = new Runnable() {

        //public void run() {

        if (!TTSManager.getInstance().isSpeaking()) {
            TTSManager.getInstance().speak("" + result.getTitle() + " " + dis + " " + finalMySide, TextToSpeech.QUEUE_FLUSH);
        }
    }
}
