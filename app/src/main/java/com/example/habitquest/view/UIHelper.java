package com.example.habitquest.view;


import android.widget.ImageView;

import androidx.fragment.app.Fragment;

import com.example.habitquest.R;
import com.example.habitquest.viewmodel.MainViewModel;

public class UIHelper {
    public static void bindBackgroundToZone(Fragment fragment, MainViewModel viewModel) {
        viewModel.getCurrentZone().observe(fragment.getViewLifecycleOwner(), newZone -> {
            if (newZone == null) return;

            ImageView globalBackground = fragment.requireActivity().findViewById(R.id.globalBackground);
            if (globalBackground != null) {
                globalBackground.setImageResource(newZone.getBackgroundResId());
            }
        });
    }
}
