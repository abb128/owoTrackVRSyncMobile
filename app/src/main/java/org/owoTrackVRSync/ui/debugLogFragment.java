package org.owoTrackVRSync.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.owoTrackVRSync.AppStatus;
import org.owoTrackVRSync.R;
import org.owoTrackVRSync.TrackingService;

import static android.content.Context.BIND_AUTO_CREATE;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link debugLogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class debugLogFragment extends GenericBindingFragment {
    public debugLogFragment() {
    }

    public static debugLogFragment newInstance() {
        debugLogFragment fragment = new debugLogFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    AppStatus stat;

    @Override
    protected void onSetStatus(String to) {
        if(stat != null) stat.update(to);
    }

    @Override
    protected void onConnectionStatus(boolean to) {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        curr_view = inflater.inflate(R.layout.fragment_debug_log, container, false);
        stat = new AppStatus(getActivity(), curr_view.findViewById(R.id.debugText));
        return curr_view;
    }
}