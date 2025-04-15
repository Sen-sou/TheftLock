package com.anti.theftlock.bluetoothService;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Camera;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anti.theftlock.R;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CommunicationService extends Service {

    // Notification constants
    private static final String CHANNEL_NAME = "Status Notification";
    private static final String CHANNEL_ID = "AntiTheft.notification";
    private static final int NOTIFICATION_ID = 1101;
    private static final int NOTIFICATION_ALERT_ID = 1102;

    // Notification Resources
    private NotificationManager notificationManager;
    private Notification statusNotification;


    // Service constants
    public static final String START = "COMMUNICATION_SERVICE_START";
    public static final String STOP = "COMMUNICATION_SERVICE_STOP";
    public static final String RELINK = "COMMUNICATION_SERVICE_RELINK";
    public static final String TAG = "CommunicationService";
    public static boolean mainAlive = true;
    private ExecutorService serviceThread;
    private BluetoothUtility bluetoothUtility;


    public class CommunicationBinder extends Binder {
        public CommunicationService getService() {
            return CommunicationService.this;
        }
    }

    private final IBinder binder = new CommunicationBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void initBluetooth(Context context, Activity activity) {
        bluetoothUtility = BluetoothUtility.getInstance(context, activity);
        bluetoothUtility.init();
    }

    public void initBluetooth() {
        bluetoothUtility.init();
    }

    public void startAsServer() {
        serviceThread.submit(bluetoothUtility::startAsServer);
        Log.d(TAG, "startAsServer: ");
    }

    public void startAsClient() {
        serviceThread.submit(bluetoothUtility::startAsClient);
    }

    public void stopConnection() {
        bluetoothUtility.stopConnection();
    }

    public void setRunningState(String state) {
        bluetoothUtility.setRunningState(state);
    }

    public Set<BluetoothDevice> getPairedDevices() {
        return bluetoothUtility.getPairedDevices();
    }

    public void setServerDevice(String deviceName) {
        bluetoothUtility.setServerDevice(deviceName);
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        bluetoothUtility.setMediaPlayer(mediaPlayer);
    }

    public void setEnableFlashing(boolean bool) {
        bluetoothUtility.setFlashingEnabled(bool);
    }
    public void stopFlasing() {
        bluetoothUtility.stopFlashing();
    }

    public boolean isBluetoothEnabled() {
        return bluetoothUtility.isBluetoothEnabled();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceThread = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Command: " + intent.getAction());

        switch (Objects.requireNonNull(intent.getAction())) {
            case START:
                startService();
                break;
            case STOP:
                if (!mainAlive)
                    stopService();
                break;
            case RELINK:
                bluetoothUtility.stopConnection();
                break;
        }

//        return super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    private void startService() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Intent stopIntent = new Intent(this, CommunicationService.class);
        stopIntent.setAction(STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                111,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        statusNotification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("TheftLock Service")
                .setContentTitle("Press to Stop Service")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentIntent(stopPendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, statusNotification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        Log.d(TAG, "onStartCommand");
    }

    private void stopService() {
        Log.d(TAG, "onStopCommand");
        bluetoothUtility.stopConnection();
        shutDownThread(serviceThread);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "Service Destroyed");
    }

    private void shutDownThread(@NonNull ExecutorService executorService) {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Log.e(TAG, "shutDownThread: " + e.getMessage());
        }
    }


}
