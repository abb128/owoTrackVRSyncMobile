package org.owoTrack.Mobile.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.owoTrack.Mobile.MainActivity;
import org.owoTrack.Mobile.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorInfoFragment extends Fragment {
    public SensorInfoFragment() {
        // Required empty public constructor
    }


    String sensorName = "err";
    int sensorID = -2;

    View main_view;

    public static SensorInfoFragment newInstance(String sensorName, int sensorID) {
        SensorInfoFragment fragment = new SensorInfoFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        main_view = inflater.inflate(R.layout.fragment_sensor_info, container, false);

        if(MainActivity.getSensorExists(sensorID)){
            ((ImageView) main_view.findViewById(R.id.radio_btn)).setImageResource(R.drawable.not_missing);
            ((TextView) main_view.findViewById(R.id.sensor_name)).setText(sensorName);
        }else{
            ((ImageView) main_view.findViewById(R.id.radio_btn)).setImageResource(R.drawable.error_missing);
            ((TextView) main_view.findViewById(R.id.sensor_name)).setText("Missing: " + sensorName);
        }

        return main_view;
    }

    @Override
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SensorInfoFragment);

        sensorName = String.valueOf(a.getText(R.styleable.SensorInfoFragment_sensorName));
        sensorID = a.getInt(R.styleable.SensorInfoFragment_sensorID, -2);

        a.recycle();
    }
}