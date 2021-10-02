package org.owoTrack.Wear;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import org.owoTrack.Wear.databinding.ActivityMainwearBinding;

public class MainWear extends Activity {

    private TextView mTextView;
    private ActivityMainwearBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainwearBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mTextView = binding.text;

    }
}