package org.owoTrackVRSync;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView statusLbl = null;
    Button connectButton = null;
    EditText ipAddrTxt = null;
    EditText portTxt = null;

    UDPGyroProviderClient client;

    AppStatus activityStatus;

    Intent mainIntent;
    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("info-log")) {
                String data = intent.getStringExtra("message");
                activityStatus.update(data);
            }else if(intent.getAction().equals("cya-ded")){
                connectButton.setEnabled(true);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusLbl = (TextView)findViewById(R.id.statusText);
        connectButton = (Button)findViewById(R.id.connectButton);
        ipAddrTxt = (EditText)findViewById(R.id.editIP);
        portTxt = (EditText)findViewById(R.id.editPort);

        connectButton.setOnClickListener(v -> onConnect());


        activityStatus = new AppStatus(this, statusLbl);

        mainIntent = null;

        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("info-log"));
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("cya-ded"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mainIntent != null) {
            stopService(mainIntent);
        }
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
        super.onPause();
    }

    private void onConnectionReturned(){
        runOnUiThread(() -> {
            connectButton.setEnabled(true);
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onConnect(){
        connectButton.setEnabled(false);
        if(mainIntent != null){
            stopService(mainIntent);
        }
        mainIntent = new Intent(this, TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", String.valueOf(ipAddrTxt.getText()));
        mainIntent.putExtra("port_no", Integer.valueOf(portTxt.getText().toString()));
        startService(mainIntent);
    }
}