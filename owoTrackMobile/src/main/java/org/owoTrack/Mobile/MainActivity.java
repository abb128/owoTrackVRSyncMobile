package org.owoTrack.Mobile;


import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.owoTrack.*;
import org.owoTrack.Mobile.ui.ConnectFragment;

public class MainActivity extends AppCompatActivity {

    public static boolean[] sensor_exist;
    public static boolean getSensorExists(int sensor){
        if((sensor < 0) || (sensor >= 4)) return false;
        return sensor_exist[sensor];
    }

    public static boolean hasAnySensorsAtAll(){
        return getSensorExists(1) || getSensorExists(2);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            sensor_exist[2] = (man.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null);
        }else{
            sensor_exist[2] = false;
        }

        sensor_exist[3] = (man.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null);

        missingSensorMessage = "";

        if(!hasAnySensorsAtAll()){
            missingSensorMessage = getString(R.string.sensors_missing_all);
        }

    }

    private void ensureUUIDSet(){
        SharedPreferences prefs = getSharedPreferences("FakeMAC", Context.MODE_PRIVATE);

        long val = -1;
        if(!prefs.contains("FakeMACValue")){
            SharedPreferences.Editor editor = prefs.edit();
            val = (new java.util.Random()).nextLong();
            editor.putLong("FakeMACValue", val);
            editor.apply();
        }else{
            val = prefs.getLong("FakeMACValue", 1);
        }

        Handshaker.setMac(val);
    }

    public NavController contr;

    private boolean connectGetMag(String ip, int port){
        SharedPreferences prefs = ConnectFragment.get_prefs(this);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("ip_address", ip);
        editor.putInt("port", port);

        editor.apply();


        contr.navigate(R.id.connectFragment);

        return prefs.getBoolean("magnetometer", true);
    }

    private void runDiscovery(){
        if(!hasAnySensorsAtAll()) return;
        if(!AutoDiscoverer.discoveryStillNecessary) return;

        try {
            AutoDiscoverer disc = new AutoDiscoverer(this, this::connectGetMag);
            Thread thrd = new Thread(disc::try_discover);
            thrd.start();
        } catch(OutOfMemoryError ignored){}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ensureUUIDSet();
        fillSensorArray();

        setContentView(R.layout.activity_main);

        contr = Navigation.findNavController(this, R.id.fragment);

        BottomNavigationView nav = findViewById(R.id.nav_view);

        NavigationUI.setupWithNavController(nav, contr);

        runDiscovery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}