package org.owoTrackVRSync;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import org.owoTrackVRSync.ui.homeMenu;

public class MainActivity extends AppCompatActivity {

    public static boolean[] sensor_exist;
    public static boolean getSensorExists(int sensor){
        return sensor_exist[sensor];
    }



    private static String missingSensorMessage = "";
    public static String getSensorText(){
        return missingSensorMessage;
    }

    private void fillSensorArray(){
        SensorManager man = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        sensor_exist = new boolean[4];

        sensor_exist[0] = (man.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null);
        sensor_exist[1] = (man.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null);
        sensor_exist[2] = (man.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null);
        sensor_exist[3] = (man.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null);

        missingSensorMessage = "";

        if(!getSensorExists(1)){
            missingSensorMessage += getString(R.string.sensors_missing_rotation_vector) + " ";
        }

        if(!getSensorExists(3)){
            missingSensorMessage += getString(R.string.sensors_missing_linear_accel);
        }

        if((!getSensorExists(1)) && (!getSensorExists(2))){
            missingSensorMessage = getString(R.string.sensors_missing_all);
        }

    }


    TextView statusLbl = null;
    Button connectButton = null;
    EditText ipAddrTxt = null;
    EditText portTxt = null;

    UDPGyroProviderClient client;

    AppStatus activityStatus;

    Intent mainIntent;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fillSensorArray();

        setContentView(R.layout.activity_main);

        NavController contr = Navigation.findNavController(this, R.id.fragment);

        BottomNavigationView nav = findViewById(R.id.nav_view);

        NavigationUI.setupWithNavController(nav, contr);

        if(true) {
            return;
        }

        statusLbl = (TextView)findViewById(R.id.statusText);
        connectButton = (Button)findViewById(R.id.connectButton);
        ipAddrTxt = (EditText)findViewById(R.id.editIP);
        portTxt = (EditText)findViewById(R.id.editPort);



        activityStatus = new AppStatus(this, statusLbl);

        mainIntent = null;

        //LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("info-log"));
        //LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("cya-ded"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        //LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("info-log"));
        //LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("cya-ded"));
        super.onResume();
    }

    private void onConnectionReturned(){
        runOnUiThread(() -> {
            connectButton.setEnabled(true);
        });
    }


}