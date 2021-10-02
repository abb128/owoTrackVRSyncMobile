package org.owoTrack.Mobile.ui;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.owoTrack.Mobile.MainActivity;
import org.owoTrack.Mobile.R;

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

        TextView sensorWarning = v.findViewById(R.id.sensorWarningTextView);
        sensorWarning.setText(MainActivity.getSensorText());

        return v;
    }
}