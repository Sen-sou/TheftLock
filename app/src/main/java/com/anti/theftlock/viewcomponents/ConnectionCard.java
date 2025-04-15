package com.anti.theftlock.viewcomponents;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.anti.theftlock.R;
import com.anti.theftlock.bluetoothService.CommunicationService;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;

public class ConnectionCard {

    private final Context context;
    private final View commCard;
    private CommunicationService communicationService;
    private Button clientButton;
    private Button serverButton;
    private Button stopButton;
    private Button refreshButton;
    private Button stopAlarmButton;
    private Spinner pairedMenu;
    private TextView outputText;
    private MaterialSwitch flashSwitch;
    private String TAG = "Connection Card";
    private MediaPlayer mediaPlayer;
    private ArrayList<String> pairedDevices;

    public ConnectionCard(Context context, View card) {
        this.context = context;
        this.commCard = card;
        mediaPlayer = MediaPlayer.create(context, R.raw.alarm);
        mediaPlayer.setLooping(true);
    }

    public void init(CommunicationService communicationService) {
        this.communicationService = communicationService;
        clientButton = commCard.findViewById(R.id.clientButton);
        serverButton = commCard.findViewById(R.id.serverButton);
        stopButton = commCard.findViewById(R.id.stopButton);
        refreshButton = commCard.findViewById(R.id.refreshDevices);
        stopAlarmButton = commCard.findViewById(R.id.stopAlarm);
        pairedMenu = commCard.findViewById(R.id.deviceSelector);
        outputText = commCard.findViewById(R.id.outputText);
        flashSwitch = commCard.findViewById(R.id.flashSwitch);

        pairedDevices = new ArrayList<>();

        clientButton.setOnClickListener(v -> {
            communicationService.startAsClient();
            Log.d(TAG, "onClick: Client");
        });

        serverButton.setOnClickListener(v -> {
            communicationService.startAsServer();
            Log.d(TAG, "onClick: Server");
        });

        stopButton.setOnClickListener(v -> {
            communicationService.stopConnection();
            Log.d(TAG, "onClick: Stop");
        });

        refreshDevices();
        refreshButton.setOnClickListener(v -> {
            refreshDevices();
            refreshSpinner();
        });

        stopAlarmButton.setEnabled(false);
        communicationService.setMediaPlayer(mediaPlayer);
        stopAlarmButton.setOnClickListener(v -> {
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
            stopAlarmButton.setEnabled(false);
            communicationService.stopFlasing();
        });

        refreshSpinner();

        flashSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            communicationService.setEnableFlashing(isChecked);
        });

    }

    private void refreshSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                pairedDevices
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pairedMenu.setAdapter(adapter);

        pairedMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                communicationService.setServerDevice(parent.getItemAtPosition(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void refreshDevices() {
        communicationService.initBluetooth();
        if (communicationService.getPairedDevices() == null) return;
        try {
            pairedDevices.clear();
            for (BluetoothDevice bd: communicationService.getPairedDevices()) {
                pairedDevices.add(bd.getName());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "onClick: " + e.getMessage());
        }
    }

    public void releaseFlash() {
        communicationService.setEnableFlashing(false);
    }

    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void setOutputText(String text) {
        outputText.setText(text);
    }


}
