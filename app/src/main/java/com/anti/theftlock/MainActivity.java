package com.anti.theftlock;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.anti.theftlock.bluetoothService.BluetoothUtility;
import com.anti.theftlock.bluetoothService.CommunicationService;
import com.anti.theftlock.viewcomponents.ConnectionCard;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private CommunicationService communicationService;
    private ServiceConnection serviceConnection;
    private ConnectionCard connectionCard;
    private final int REQUEST_PERMISSIONS_CODE = 101;
    private final String TAG = "AntiTheft";
    private Button permButton;
    private Button bluetoothBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set Top bar Text Effect
        setToolbarTextEffect();
        getWindow().setNavigationBarColor(getResources().getColor(R.color.mainBackground, getTheme()));

        permButton = findViewById(R.id.reqPerm);
        permButton.setOnClickListener(v -> {
            requestRequiredPermissions();
            permButton.setEnabled(false);
            permButton.setVisibility(INVISIBLE);
        });

        bluetoothBtn = findViewById(R.id.bluetooth);
        bluetoothBtn.setEnabled(false);
        bluetoothBtn.setVisibility(INVISIBLE);
        bluetoothBtn.setOnClickListener(v -> {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                startActivityForResult(enableBtIntent, 11);
            } catch (SecurityException e) {
                Log.e(TAG, "Permissions not found");
            }
        });


        requestRequiredPermissions();

        CommunicationService.mainAlive = true;
        connectionCard = new ConnectionCard(this, findViewById(R.id.connectionCard));

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
    }

    private void registerService() {
        Intent serviceIntent = new Intent(getApplicationContext(), CommunicationService.class);
        if (!communicationServiceRunning()) serviceIntent.setAction(CommunicationService.START);
        else serviceIntent.setAction(CommunicationService.RELINK);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                communicationService = ((CommunicationService.CommunicationBinder) service).getService();
                communicationService.initBluetooth(getApplicationContext(), MainActivity.this);
                connectionCard.init(communicationService);
                if (!communicationService.isBluetoothEnabled()) {
                    Toast.makeText(MainActivity.this, "Bluetooth is Turned off, Turn Bluetooth On to use", Toast.LENGTH_SHORT).show();
                    bluetoothBtn.setEnabled(true);
                    bluetoothBtn.setVisibility(VISIBLE);
                    communicationService.setRunningState(BluetoothUtility.IDLE_STATE);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(CommunicationService.TAG, "Communication Service Disconnected");
            }
        };
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
        startForegroundService(serviceIntent);
    }

    private boolean communicationServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo: activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (CommunicationService.class.getName().equals(serviceInfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[] {
                                Manifest.permission.POST_NOTIFICATIONS,
                                Manifest.permission.FOREGROUND_SERVICE,
                                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                                Manifest.permission.CAMERA
                        },
                        REQUEST_PERMISSIONS_CODE
                );
                Log.d(TAG, "Requesting Permissions");
            } else {
                Log.d(TAG, "Registering Service");
                registerService();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.e(TAG, "Checking Permissions");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            grantResults[0] = PackageManager.PERMISSION_GRANTED;
        }

        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // check all permission and display denied permission
                Log.d(TAG, "Permissions Granted: " + Arrays.toString(grantResults));
                ((TextView)findViewById(R.id.outputText)).setText("IDLE");
                registerService();
            } else {
                Log.e(TAG, "Permissions denied");
                permButton.setVisibility(VISIBLE);
                permButton.setEnabled(true);
                ((TextView)findViewById(R.id.outputText)).setText("Require Permission");
            }
        }
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d("BluetoothState", "Bluetooth is OFF");
                        Toast.makeText(MainActivity.this, "Bluetooth is Turned off, Turn Bluetooth On to use", Toast.LENGTH_SHORT).show();
                        bluetoothBtn.setEnabled(true);
                        bluetoothBtn.setVisibility(VISIBLE);
                        communicationService.setRunningState(BluetoothUtility.IDLE_STATE);
                        connectionCard.setOutputText("Bluetooth Turned Off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d("BluetoothState", "Bluetooth is TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d("BluetoothState", "Bluetooth is ON");
                        bluetoothBtn.setEnabled(false);
                        bluetoothBtn.setVisibility(INVISIBLE);
                        communicationService.setRunningState(BluetoothUtility.READY_STATE);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d("BluetoothState", "Bluetooth is TURNING ON");
                        break;
                    default:
                        Log.d("BluetoothState", "Unknown state");
                        break;
                }
            }
        }
    };

    private void setToolbarTextEffect() {
        TextView toolbarText = findViewById(R.id.toolbarText);
        SpannableString spannableString = new SpannableString("THEFTLOCK");
        spannableString.setSpan(new ForegroundColorSpan(0xFFE68B37),
                0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        toolbarText.setText(spannableString);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        CommunicationService.mainAlive = false;
        connectionCard.releaseMediaPlayer();
        connectionCard.releaseFlash();
        connectionCard.releaseVibrate();
        connectionCard.releaseCaptureAlert();
        unbindService(serviceConnection);
        unregisterReceiver(bluetoothStateReceiver);
        Log.d(TAG, "onDestroy: service Unbinded");
    }

}