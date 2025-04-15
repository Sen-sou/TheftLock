package com.anti.theftlock.utility;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;



public class FlashlightController {
    private final CameraManager cameraManager;
    private final String cameraId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFlashing = false;
    private boolean isFlashOn = false;
    private int patInt = 1;
    private int delay = 1000;

    public FlashlightController(Context context) throws CameraAccessException {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        cameraId = cameraManager.getCameraIdList()[0];
    }

    private final Runnable flashRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFlashing) {
                toggleFlash();
                handler.postDelayed(this, delay); // Repeat every 1 second
            }
        }
    };

    public void startFlashing() {
        if (!isFlashing) {
            isFlashing = true;
            handler.post(flashRunnable);
        }
    }

    public void stopFlashing() {
        isFlashing = false;
        setFlash(false);
        handler.removeCallbacks(flashRunnable);
    }

    private void toggleFlash() {
        if (patInt % 3 == 0) {
            delay = 1000;
            patInt = 0;
            setFlash(false);
        } else {
            delay = 50;
            setFlash(!isFlashOn);
        }
        patInt++;

    }
    // 1000 50 50 1000
    private void setFlash(boolean state) {
        try {
            cameraManager.setTorchMode(cameraId, state);
            isFlashOn = state;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}