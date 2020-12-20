/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import org.tensorflow.lite.examples.detection.caneThroughManager.Labels_info;
import org.tensorflow.lite.examples.detection.caneThroughManager.ObjectsManager;
import org.tensorflow.lite.examples.detection.caneThroughManager.Utils;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.Distance_CallBack;
import org.tensorflow.lite.examples.detection.caneThroughManager.Labels_Keys;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Detector detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    public static float my_distance;

    public static Map<String, Float> my_distances = new HashMap<>();

    static Distance_CallBack distanceCallBack;

    TextToSpeech textToSpeech;

    float personLastLocation = 0;
    float mouseLastLocation = 0;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);


        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int lang = textToSpeech.setLanguage(Locale.ENGLISH);

                }
            }
        });
    }


    @Override
    protected void processImage() {


        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();
        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void run() {

                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        if (ObjectsManager.getInstance() != null)
                            ObjectsManager.getInstance().addObjects(results.subList(0, 5).stream().filter(res -> res.getConfidence() > 0.55).collect(Collectors.toList()));

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Detector.Recognition> mappedRecognitions = new ArrayList<Detector.Recognition>();

                        for (int i = 0; i < results.size(); i++)
                            Log.i("TAGtest", "run: " + results.get(i).getLocation().left + " right: " + results.get(i).getLocation().right + "\n");


                        for (final Detector.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);
                                if (result.getTitle().equals(Labels_Keys.MOUSE)) {


                                    float p = result.getLocation().right - result.getLocation().left;
                                    float d = (float) ((6.5 * 263.621) / p);
                                    float mouseDistance = getDistance(result);
                                    distanceCallBack.mouse(mouseDistance);
                                    my_distances.put("mouse", mouseDistance);
                                    my_distance = getDistance(result);
//                                    soundAlert(result, mouseDistance);

                                    if (Math.abs(mouseLastLocation - mouseDistance) > 100) {
                                        Utils.soundReport(result, mouseDistance);
                                    }
                                    Log.d("pttt", "bottom: " + result.getLocation().bottom + ", top: " + result.getLocation().top + ", right: " + result.getLocation().right + ", left: " + result.getLocation().left);


                                } else if (result.getTitle().equals(Labels_Keys.PERSON)) {
                                    Log.d("pttt", "bottom: " + result.getLocation().bottom + ", top: " + result.getLocation().top + ", right: " + result.getLocation().right + ", left: " + result.getLocation().left);
                                    float personDistance = getDistance(result);
                                    distanceCallBack.person(personDistance);
                                    //my_distances.put("person",personDistance);
                                    //soundAlert(result, personDistance);
                                    if (Math.abs(personLastLocation - personDistance) > 100)
                                        Utils.soundReport(result, personDistance);
                                }

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    public float getDistance(Detector.Recognition result) {
        float pixels = result.getLocation().right - result.getLocation().left;
        String label = result.getTitle();
        return calculateDistance(label, pixels);
    }

    private float calculateDistance(String label, float pixels) {
        return (Labels_info.label_val.get(label)[0] * Labels_info.label_val.get(label)[1]) / (2 * pixels);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(
                () -> {
                    try {
                        detector.setUseNNAPI(isChecked);
                    } catch (UnsupportedOperationException e) {
                        LOGGER.e(e, "Failed to set \"Use NNAPI\".");
                        runOnUiThread(
                                () -> {
                                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    }
                });
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(
                () -> {
                    try {
                        detector.setNumThreads(numThreads);
                    } catch (IllegalArgumentException e) {
                        LOGGER.e(e, "Failed to set multithreads.");
                        runOnUiThread(
                                () -> {
                                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    }
                });
    }

    void soundAlert(Detector.Recognition result, float d) {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.setVolume((15.0f / d) * (1.0f - result.getLocation().centerX() / 300.0f), (15.0f / d) * (result.getLocation().centerX() / 300.0f));
            mediaPlayer.start();
        }
    }

    void soundReport(Detector.Recognition result, float d) {
        personLastLocation = d;
        mouseLastLocation = d;
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

        if (!textToSpeech.isSpeaking()) {
            int speech = textToSpeech.speak("" + result.getTitle() + " " + dis + " " + finalMySide, TextToSpeech.QUEUE_FLUSH, null);
        }

        //  h.postDelayed(this, 5000);
        //  }
        //};

        // h.postDelayed(r, 5000);
    }

    public static void setDistanceCallBack(Distance_CallBack callBack) {
        distanceCallBack = callBack;
    }
}
