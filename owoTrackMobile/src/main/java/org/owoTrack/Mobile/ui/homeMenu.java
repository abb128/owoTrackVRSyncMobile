package org.owoTrack.Mobile.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.owoTrack.Mobile.MainActivity;
import org.owoTrack.Mobile.R;
import org.owoTrack.TrackingService;

public class homeMenu extends Fragment {


    public homeMenu() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home_menu, container, false);

        Button autoConnectButton = v.findViewById(R.id.autoconnectButton);
        TextView sensorWarning = v.findViewById(R.id.sensorWarningTextView);
        sensorWarning.setText(MainActivity.getSensorText());

        if(!MainActivity.hasAnySensorsAtAll()){
            TextView sleepWarning = v.findViewById(R.id.sleepWarningText);
            sleepWarning.setText("");
            autoConnectButton.setVisibility(View.GONE);
        } else {
            autoConnectButton.setOnClickListener(p -> autoConnect());
        }

        return v;
    }

    private void autoConnect(){
        if(TrackingService.isInstanceCreated()) return;

        Intent mainIntent = new Intent(getContext(), TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", "255.255.255.255");
        mainIntent.putExtra("port_no", 6969);
        mainIntent.putExtra("magnetometer", false);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            getContext().startForegroundService(mainIntent);
        }else{
            getContext().startService(mainIntent);
        }
        MainActivity.contr.navigate(R.id.connectFragment);
    }
}