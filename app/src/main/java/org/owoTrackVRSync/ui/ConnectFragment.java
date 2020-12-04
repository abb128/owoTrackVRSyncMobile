package org.owoTrackVRSync.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.owoTrackVRSync.AppStatus;
import org.owoTrackVRSync.R;
import org.owoTrackVRSync.TrackingService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.BIND_AUTO_CREATE;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectFragment extends GenericBindingFragment {

    final static String CONN_DATA = "CONNECTION_DATA_PREF";

    private SharedPreferences get_prefs(){
        return getContext().getSharedPreferences(CONN_DATA, Context.MODE_PRIVATE);
    }


    public ConnectFragment() {}

    public static ConnectFragment newInstance() {
        ConnectFragment fragment = new ConnectFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    protected void onSetStatus(String to) {
        ((TextView)curr_view.findViewById(R.id.statusText)).setText(to.split("\n")[0]);
    }

    @Override
    protected void onConnectionStatus(boolean to) {
        connect_button.setEnabled(!to);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        save_data();
    }

    Button connect_button = null;
    EditText ipAddrTxt = null;
    EditText portTxt = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        curr_view = inflater.inflate(R.layout.fragment_connect, container, false);

        connect_button = curr_view.findViewById(R.id.connectButton);
        ipAddrTxt = curr_view.findViewById(R.id.editIP);
        portTxt = curr_view.findViewById(R.id.editPort);

        SharedPreferences prefs = get_prefs();

        ipAddrTxt.setText(prefs.getString("ip_address", "192.168.24.150"));
        portTxt.setText(String.valueOf(prefs.getInt("port", 6969)));

        connect_button.setOnClickListener(v -> onConnect());

        return curr_view;
    }

    private String get_ip_address(){
        String filtered_ip = String.valueOf(ipAddrTxt.getText()).replaceAll("[^0-9\\.]", "");
        ipAddrTxt.setText(filtered_ip);

        return filtered_ip;
    }

    private int get_port(){
        String filtered_port = String.valueOf(portTxt.getText()).replaceAll("[^0-9]", "");
        portTxt.setText(filtered_port);

        return Integer.valueOf(filtered_port);
    }


    private void onConnect(){
        if((service_v != null) && (service_v.is_running())){
            onSetStatus("Service is already running!!");
            return;
        }

        onConnectionStatus(false);



        Intent mainIntent = new Intent(getContext(), TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", get_ip_address());
        mainIntent.putExtra("port_no", get_port());

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            getContext().startForegroundService(mainIntent);
        }else{
            getContext().startService(mainIntent);
        }
    }

    public void save_data(){
        SharedPreferences prefs = get_prefs();
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("ip_address", get_ip_address());
        editor.putInt("port", get_port());

        editor.apply();
    }

}