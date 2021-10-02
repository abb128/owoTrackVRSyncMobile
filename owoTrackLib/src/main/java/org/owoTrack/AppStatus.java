package org.owoTrack;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class AppStatus {
    private Activity main_activity;
    private TextView statusLbl;

    private Service main_service;

    public String statusi = "";

    public AppStatus(Activity act, TextView tx){
        main_activity = act;
        statusLbl = tx;
        main_service = null;
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
        statusi = to + "\n" + statusi;
        if(main_service != null){
            Intent intent = new Intent("info-log");
            intent.putExtra("message", to);
            LocalBroadcastManager.getInstance(main_service).sendBroadcast(intent);
            return;
        }
        if((main_activity == null) || (statusLbl == null)){
            return;
        }
        main_activity.runOnUiThread(() -> {
            statusLbl.setText(statusi);
        });

    }
}
