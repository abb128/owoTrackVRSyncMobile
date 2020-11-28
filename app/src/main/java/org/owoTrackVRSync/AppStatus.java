package org.owoTrackVRSync;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class AppStatus {
    private Activity main_activity;
    private TextView statusLbl;

    private Service main_service;

    private String statusi;

    AppStatus(Activity act, TextView tx){
        main_activity = act;
        statusLbl = tx;
        statusi = "Debug log will appear here";
        main_service = null;
        update("Initialized");
    }

    AppStatus(Service act){
        main_service = act;
        update("Service Initialized");
    }

    AppStatus(){
        main_activity = null;
        statusLbl = null;
    }

    public void update(String to){
        if(main_service != null){
            Intent intent = new Intent("info-log");
            intent.putExtra("message", to);
            LocalBroadcastManager.getInstance(main_service).sendBroadcast(intent);
            return;
        }
        if((main_activity == null) || (statusLbl == null)){
            return;
        }
        statusi = to + "\n" + statusi;
        main_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusLbl.setText(statusi);
            }
        });

    }
}
