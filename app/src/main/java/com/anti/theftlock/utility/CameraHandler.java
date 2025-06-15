package com.anti.theftlock.utility;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraHandler {

    private static final String TAG = "CameraHandler";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private ImageCapture imageCapture;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private CameraType currentCameraType = CameraType.BACK;

    // For periodic capture
    private final Handler periodicHandler = new Handler(Looper.getMainLooper());
    private Runnable periodicRunnable;
    private boolean isPeriodicCaptureRunning = false;

    private final ExecutorService cameraExecutor;
    private OnPhotoCaptureListener listener;

    public enum CameraType {
        FRONT, BACK
    }

    public interface OnPhotoCaptureListener {
        void onPhotoCaptured(File photoFile);
        void onError(String errorMessage);
    }

    public CameraHandler(Context context, LifecycleOwner lifecycleOwner) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera(currentCameraType);
    }

    public void setOnPhotoCaptureListener(OnPhotoCaptureListener listener) {
        this.listener = listener;
    }

    public void startCamera(CameraType cameraType) {
        this.currentCameraType = cameraType;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraType == CameraType.FRONT ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture);

            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera.", e);
                if (listener != null) {
                    listener.onError("Failed to start camera: " + e.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void switchCamera() {
        if (cameraProvider != null) {
            CameraType newType = (currentCameraType == CameraType.BACK) ? CameraType.FRONT : CameraType.BACK;
            startCamera(newType);
        }
    }


    public void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized. Cannot take photo.");
            if (listener != null) listener.onError("Camera not ready.");
            return;
        }

        File photoFile = new File(getOutputDirectory(), new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Log.d(TAG, "Photo saved successfully: " + photoFile.getAbsolutePath());
                if (listener != null) {
                    // Post to main thread to be safe for UI updates
                    new Handler(Looper.getMainLooper()).post(() -> listener.onPhotoCaptured(photoFile));
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onError("Photo capture failed: " + exception.getMessage()));
                }
            }
        });
    }

    public void startPeriodicCapture(long intervalMillis) {
        if (isPeriodicCaptureRunning) {
            Log.w(TAG, "Periodic capture is already running.");
            return;
        }
        isPeriodicCaptureRunning = true;
        periodicRunnable = new Runnable() {
            @Override
            public void run() {
                takePhoto();
                if (isPeriodicCaptureRunning) {
                    periodicHandler.postDelayed(this, intervalMillis);
                }
            }
        };
        periodicHandler.post(periodicRunnable);
        Log.i(TAG, "Started periodic capture every " + intervalMillis + "ms.");
    }

    public void stopPeriodicCapture() {
        if (!isPeriodicCaptureRunning) return;
        isPeriodicCaptureRunning = false;
        periodicHandler.removeCallbacks(periodicRunnable);
        Log.i(TAG, "Stopped periodic capture.");
    }

    public void shutdown() {
        stopPeriodicCapture();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraExecutor.shutdown();
    }

    private File getOutputDirectory() {
        // Use app-specific directory which requires no storage permissions on API 19+
        File mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (mediaDir != null && !mediaDir.exists()) {
            mediaDir.mkdirs();
        }
        return (mediaDir != null) ? mediaDir : context.getFilesDir();
    }
}
