package org.owoTrack.Mobile.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.owoTrack.Mobile.R;
import org.owoTrack.TrackingService;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectFragment extends GenericBindingFragment {

    final static String CONN_DATA = "CONNECTION_DATA_PREF";

    public static SharedPreferences get_prefs(Context c){
        return c.getSharedPreferences(CONN_DATA, Context.MODE_PRIVATE);
    }

    public SharedPreferences get_prefs(){
        return get_prefs(getContext());
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
        if(curr_view == null) return;

        TextView text = curr_view.findViewById(R.id.statusText);

        if(text != null)
            text.setText(to.split("\n")[0]);
    }

    @Override
    protected void onConnectionStatus(boolean to) {
        if(connect_button != null)
            connect_button.setText(to ? "Disconnect" : "Connect");

        if(magBox != null)
            magBox.setEnabled(!to);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        save_data();
    }

    Button connect_button = null;
    EditText ipAddrTxt = null;
    EditText portTxt = null;
    CheckBox magBox = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        curr_view = inflater.inflate(R.layout.fragment_connect, container, false);

        connect_button = curr_view.findViewById(R.id.connectButton);
        ipAddrTxt = curr_view.findViewById(R.id.editIP);
        portTxt = curr_view.findViewById(R.id.editPort);
        magBox = curr_view.findViewById(R.id.magnetCheckbox);

        SharedPreferences prefs = get_prefs();

        ipAddrTxt.setText(prefs.getString("ip_address", "192.168.24.150"));
        portTxt.setText(String.valueOf(prefs.getInt("port", 6969)));
        magBox.setChecked(prefs.getBoolean("magnetometer", true));

        connect_button.setOnClickListener(v -> onConnect());

        onConnectionStatus(TrackingService.isInstanceCreated());

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

        int val = 6969;
        try{
            val = Integer.parseInt(filtered_port);
        }catch(NumberFormatException ignored){}

        return val;
    }

    private boolean get_mag(){
        return magBox.isChecked();
    }

    private void onConnect(){
        if((service_v != null) && (service_v.is_running())){
            onSetStatus("Killing service...");
            Intent intent = new Intent("kill-ze-service");
            getContext().sendBroadcast(intent);
            return;
        }


        onConnectionStatus(true);

        Intent mainIntent = new Intent(getContext(), TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", get_ip_address());
        mainIntent.putExtra("port_no", get_port());
        mainIntent.putExtra("magnetometer", get_mag());

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
        editor.putBoolean("magnetometer", get_mag());

        editor.apply();
    }

}