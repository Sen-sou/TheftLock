package com.anti.theftlock.utility;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class DeviceVibrator {

    private final Vibrator vibrator;
    private final Handler timeoutHandler;
    private final Runnable stopVibrationRunnable;

    public DeviceVibrator(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        this.timeoutHandler = new Handler(Looper.getMainLooper());
        this.stopVibrationRunnable = this::cancel;
    }

    public boolean hasVibrator() {
        return vibrator != null && vibrator.hasVibrator();
    }

    public void vibrate(long milliseconds) {
        if (!hasVibrator()) return;

        cancel();

        VibrationEffect effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE);
        vibrator.vibrate(effect);
    }

    public void vibrate(long[] pattern) {
        if (!hasVibrator()) return;

        cancel();

        int repeat = -1;

        VibrationEffect effect = VibrationEffect.createWaveform(pattern, repeat);
        vibrator.vibrate(effect);
    }

    public void vibrateWithTimeout(long[] pattern, long timeoutMillis) {
        if (!hasVibrator() || timeoutMillis <= 0) {
            return;
        }

        cancel();

        int repeat = 0;

        VibrationEffect effect = VibrationEffect.createWaveform(pattern, repeat);
        vibrator.vibrate(effect);

        timeoutHandler.postDelayed(stopVibrationRunnable, timeoutMillis);
    }

    public void cancel() {
        if (hasVibrator()) {
            vibrator.cancel();
        }

        timeoutHandler.removeCallbacks(stopVibrationRunnable);
    }
}