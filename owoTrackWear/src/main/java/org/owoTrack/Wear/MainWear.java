package org.owoTrack.Wear;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.owoTrack.AutoDiscoverer;
import org.owoTrack.TrackingService;
import org.owoTrack.Wear.databinding.ActivityMainwearBinding;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainWear extends Activity {

    private ActivityMainwearBinding binding;

    private TextView debugText;
    private boolean dead_no_sensors = false;

    private Lock connecting_lock = new ReentrantLock();

    boolean game_rotation_exists;
    boolean norm_rotation_exists;

    boolean connecting;
    boolean connected;


    private void onSetStatus(String to){;
        if(to.contains("Service not start")) return;

        System.out.println("Status " + to);
        String[] lines = to.split("\n");
        debugText.setText(lines[lines.length-1]);
    }
    private void onConnectionStatus(boolean to){
        setConnectedStatus(connecting, to);
    };

    private void updateSensorStatus(){
        SensorManager man = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        game_rotation_exists = (man.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null);
        norm_rotation_exists = (man.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null);

        binding.gameRotationRadio.setEnabled(game_rotation_exists);
        binding.magRotationRadio.setEnabled(norm_rotation_exists);

        dead_no_sensors = (!game_rotation_exists) && (!norm_rotation_exists);
        if(dead_no_sensors){
            binding.yesSensorsLayout.setVisibility(View.GONE);
            binding.noSensorsLayout.setVisibility(View.VISIBLE);
        }else{
            binding.yesSensorsLayout.setVisibility(View.VISIBLE);
            binding.noSensorsLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainwearBinding.inflate(getLayoutInflater());

        updateSensorStatus();

        binding.connectButton.setOnTouchListener(this::onConnectTouch);
        debugText = binding.debugText;

        setContentView(binding.getRoot());

        setConnectedStatus(false, false);

        doBinding(true);
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("info-log"));
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("cya-ded"));
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("pls-let-me-die"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doBinding(false);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
    }

    private void setConnectedStatus(boolean connecting, boolean connected){
        this.connecting = connecting;
        this.connected = connected;

        binding.spinner.setVisibility((connecting && !connected) ? View.VISIBLE : View.GONE);
        binding.connectButton.setVisibility((!connecting) ? View.VISIBLE : View.GONE);
        binding.connectButton.setEnabled(!connecting);

        binding.connectButton.setText(connected ? R.string.disconnect : R.string.connect);
    }

    private boolean getMagUsage(){
        boolean use_mag;

        // select the first available sensor that's either checked or is available
        boolean mag_enabled = binding.magRotationRadio.isChecked();
        boolean mag_disabled = binding.gameRotationRadio.isChecked();
        if (!mag_enabled && !mag_disabled) {
            if (norm_rotation_exists) {
                binding.magRotationRadio.setChecked(true);
                use_mag = true;
            } else {
                binding.gameRotationRadio.setChecked(true);
                use_mag = false;
            }
        } else {
            use_mag = mag_enabled;
        }

        return use_mag;
    }

    private void doBinding(boolean is_bound){
        if(is_bound){
            Intent intent = new Intent(this, TrackingService.class);
            this.bindService(intent, trackingConnection, BIND_AUTO_CREATE);
        }else{
            this.unbindService(trackingConnection);
        }
    }


    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case "info-log":
                    onConnectionStatus(true);
                    String data = intent.getStringExtra("message");
                    onSetStatus(data);
                    return;
                case "cya-ded":
                    onConnectionStatus(false);
                    return;
                case "pls-let-me-die":
                    doBinding(false);
                    doBinding(true);
                    return;

            }
        }
    };

    TrackingService service_v = null;
    private ServiceConnection trackingConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TrackingService.TrackingBinder binderBridge = (TrackingService.TrackingBinder) service;
            service_v = binderBridge.getService();

            onConnectionStatus(service_v.is_running());

            onSetStatus(service_v.getLog());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service_v = null;

            onConnectionStatus(false);
        }
    };


    private void connect(String ip, int port, boolean mag){
        System.out.println("Connect calleded");
        if((service_v != null) && (service_v.is_running())){
            onSetStatus("Killing service...");
            Intent intent = new Intent("kill-ze-service");
            this.sendBroadcast(intent);
            return;
        }


        onConnectionStatus(true);

        Intent mainIntent = new Intent(this, TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", ip);
        mainIntent.putExtra("port_no", port);
        mainIntent.putExtra("magnetometer", mag);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            this.startForegroundService(mainIntent);
        }else{
            this.startService(mainIntent);
        }
    }

    private void runConnectionProcedure(){
        if(!connecting_lock.tryLock()) return;

        boolean server_found = false;
        try {
            boolean use_mag = getMagUsage();

            debugText.setText(R.string.searching);

            AutoDiscoverer.DiscoveryResult result = null;
            for(int i=0; i<5; i++) {
                result = AutoDiscoverer.attempt_discover(1000);
                if(result.found) break;
            }

            if (result == null || !result.found) {
                debugText.setText(R.string.not_found);
                return;
            }

            server_found = true;
            debugText.setText("Connect to " + result.server_address.toString() + ":" + String.valueOf(result.port));

            this.connect(result.server_address.toString(), result.port, use_mag);
        }finally{
            connecting_lock.unlock();
            boolean finalServer_found = server_found;
            this.runOnUiThread(() -> {
                setConnectedStatus(false, finalServer_found);
            });
        }
    }

    private long pressedDownTime = 0;
    private boolean onConnectTouch(View v, MotionEvent event){
        System.out.println("Touch " + event.toString());
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressedDownTime = System.currentTimeMillis();
                return true;
            case MotionEvent.ACTION_UP: {
                long diff = System.currentTimeMillis() - pressedDownTime;
                if (diff > 1000) {
                    onConnectHold(v);
                }else{
                    onConnectClick(v);
                }
                return true;
            }
        }
        return false;
    }

    private void onConnectClick(View view) {
        if(dead_no_sensors) return;


        if((service_v != null) && (service_v.is_running())){
            onSetStatus("Killing service...");
            Intent intent = new Intent("kill-ze-service");
            this.sendBroadcast(intent);

            setConnectedStatus(false, false);
            return;
        }

        setConnectedStatus(true, false);

        Thread thread = new Thread(this::runConnectionProcedure);
        thread.start();
    }


    private void onConnectHold(View view) {
        if(dead_no_sensors) return;
        debugText.setText("Debug connect to 192.168.32.50");

        Thread thread = new Thread(() -> {
            this.connect("192.168.32.50", 6969, true);
        });
        thread.start();
    }
}