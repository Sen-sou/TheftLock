package com.anti.theftlock.bluetoothService;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.anti.theftlock.R;
import com.anti.theftlock.utility.FlashlightController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothUtility {

    // Singleton Instance
    private static volatile BluetoothUtility INSTANCE = null;


    // Utility Resources

    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private final Activity mainActivity;
    private volatile BluetoothDevice serverDevice;
    private volatile BluetoothServerSocket bluetoothServerSocket;
    private volatile BluetoothSocket bluetoothSocket;
    private final String TAG = "Bluetooth Utility";
    private final int PERMISSION_REQUEST_CODE = 1001;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public static final String SERVER_STATE = "SERVER";
    public static final String CLIENT_STATE = "CLIENT";
    public static final String READY_STATE = "READY";
    public static final String IDLE_STATE = "IDLE";
    private String runningState;
    private boolean isConnectionActive;
    private Set<BluetoothDevice> pairedDevices;
    private InputStream inputStream;
    private  OutputStream outputStream;
    private MediaPlayer mediaPlayer;
    private FlashlightController flashlightController;
    private boolean flashingEnabled = false;

    private BluetoothUtility(Context context, Activity activity) {
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.context = context;
        this.mainActivity = activity;
        try {
            this.flashlightController = new FlashlightController(context);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static synchronized BluetoothUtility getInstance(Context context, Activity activity) {
        if (INSTANCE == null) {
            INSTANCE = new BluetoothUtility(context, activity);
        }
        return INSTANCE;
    }

    public void init() {
        // provided that all the permissions are granted and bluetooth is on
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not Enabled");
            return;
        }
        discoverDevices();
        runningState = IDLE_STATE;

    }

    public boolean setServerDevice(String deviceName) {
        try {
            if (pairedDevices == null) return false;
            discoverDevices();
            serverDevice = null;
            for (BluetoothDevice bd : pairedDevices) {
                if (bd.getName().equals(deviceName)) {
                    serverDevice = bd;
                    Log.d(TAG, "Server Device: " + serverDevice.getName() + " : " + serverDevice.getAddress());
                    runningState = READY_STATE;
                    return true;
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission Missing");
        }
        Log.d(TAG, "setConnectedDevice: Did not find Server Device");
        return false;
    }

    public void startAsServer() {
       try {
           if (!runningState.equals(READY_STATE)) return;
           bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(TAG, SPP_UUID);
           Log.d(TAG, "startAsServer: Finding Clients");
           mainActivity.runOnUiThread(() -> Toast.makeText(context, "Starting Server, Finding Client!", Toast.LENGTH_SHORT).show());
           bluetoothSocket = bluetoothServerSocket.accept(60000);
           setOutputOnMain("Getting pinged from " + bluetoothSocket.getRemoteDevice().getName());
           mainActivity.runOnUiThread(() -> Toast.makeText(context, "Client Found: Connected to " + bluetoothSocket.getRemoteDevice().getName(), Toast.LENGTH_SHORT).show());
           Log.d(TAG, "startAsServer: Found Clients");
           runningState = SERVER_STATE;
           isConnectionActive = true;

           communicate(bluetoothSocket);

       } catch (IOException e) {
           Log.e(TAG, "IOException" + e.getMessage());
           isConnectionActive = false;
           mainActivity.runOnUiThread(() -> Toast.makeText(context, "Server Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
       } catch (SecurityException e) {
           isConnectionActive = false;
           Log.e(TAG, "Permission Missing");
       } finally {
           runningState = READY_STATE;
       }
    }

    public void startAsClient() {
        try {
            if (!runningState.equals(READY_STATE)) return;
            setServerDevice(serverDevice.getName());
            BluetoothDevice server = bluetoothAdapter.getRemoteDevice(serverDevice.getAddress());

            Log.d(TAG, "startAsClient: Connecting to Server");
            mainActivity.runOnUiThread(() -> Toast.makeText(context, "Connecting to Server!", Toast.LENGTH_SHORT).show());
            bluetoothSocket = server.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            Log.d(TAG, "startAsClient: Connected to Server");
            runningState = CLIENT_STATE;

            mainActivity.runOnUiThread(() -> Toast.makeText(context, "Connected to " + serverDevice.getName() + "!", Toast.LENGTH_SHORT).show());
            setOutputOnMain("Pinging to " + serverDevice.getName());

            isConnectionActive = true;

            communicate(bluetoothSocket);


        }catch (IOException e) {
            Log.e(TAG, "IOException" + e.getMessage());
            isConnectionActive = false;
            mainActivity.runOnUiThread(() -> Toast.makeText(context, "Server Error: Can't Find Server!", Toast.LENGTH_LONG).show());
        } catch (SecurityException e) {
            isConnectionActive = false;
            Log.e(TAG, "Permission Missing");
        } finally {
            runningState = READY_STATE;
        }
    }

    private void communicate(BluetoothSocket socket) {
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytes;
            String receivedMessage = "pong";
            long startTime;
            long timeout = 3000;

            while (isConnectionActive) {
                startTime = System.currentTimeMillis();

                if (socket.isConnected()) {
                    if ("pong".equals(receivedMessage) && runningState.equals(CLIENT_STATE)) {
                        outputStream.write("ping".getBytes());
                    } else if ("ping".equals(receivedMessage) && runningState.equals(SERVER_STATE)) {
                        outputStream.write("pong".getBytes());
                    }
                }

                // Check for data
                while (inputStream.available() == 0) {
                    if (System.currentTimeMillis() - startTime > timeout) {
                        throw new IOException("Read timeout: No data received within " + timeout + "ms."); // TODO: reconnect implementation and break handling
                    }
                    Thread.sleep(100);
                }

                // Data is available, read it
                bytes = inputStream.read(buffer);
                receivedMessage = new String(buffer, 0, bytes);
                Log.d(TAG, "Received: " + receivedMessage);
                Thread.sleep(100);

            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "communicate: " + e.getMessage());
            stopConnection();
            isConnectionActive = false;

            triggerAlarm();
        }
    }

    public void stopConnection() {
        try {
            setOutputOnMain("Disconnected");
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
            if (bluetoothServerSocket != null) bluetoothServerSocket.close();
            runningState = READY_STATE;
        } catch (IOException e) {
            Log.e(TAG, "stopConnection: " + e.getMessage());
        }

    }

    public Set<BluetoothDevice> getPairedDevices() {
        return pairedDevices;
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }

    public void setFlashingEnabled(boolean bool) {
        flashingEnabled = bool;
        if(!bool) {
            flashlightController.stopFlashing();
        }
    }

    public void stopFlashing() {
        flashlightController.stopFlashing();
    }

    public void setRunningState(String state) {
        runningState = state;
    }


    private void discoverDevices() {
        // Get paired devices
        Log.d(TAG, "discoverDevices: ");
        try {
            pairedDevices = bluetoothAdapter.getBondedDevices();
            if (!pairedDevices.isEmpty()) {
                for (BluetoothDevice device : pairedDevices) {
                    Log.d(TAG, "Paired device: " + device.getName() + " - " + device.getAddress());
                }
                setOutputOnMain(READY_STATE);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission Missing");
        }
    }

    private void setOutputOnMain(String message) {
        mainActivity.runOnUiThread(() -> {
            TextView output = mainActivity.findViewById(R.id.outputText);
            output.setText(message);
        });
    }

    private void triggerAlarm() {
        mainActivity.runOnUiThread(() -> {
            ((Button) mainActivity.findViewById(R.id.stopAlarm)).setEnabled(true);
            mediaPlayer.start();
            Toast.makeText(context, "Connection Lost! Alarm Triggered!", Toast.LENGTH_LONG).show();
            if (flashingEnabled) {
                flashlightController.startFlashing();
            }
        });
    }


    public boolean isBluetoothEnabled() {
        return bluetoothAdapter.isEnabled();
    }

}
