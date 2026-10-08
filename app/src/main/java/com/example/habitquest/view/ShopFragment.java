package com.example.habitquest.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.R;
import com.example.habitquest.viewmodel.MainViewModel;

import java.util.ArrayList;

public class ShopFragment extends Fragment {

    private MainViewModel viewModel;
    private ShopAdapter adapter;
    private TextView coinsText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        coinsText = view.findViewById(R.id.shopCoinsText);
        RecyclerView recyclerView = view.findViewById(R.id.shopRecyclerView);

        adapter = new ShopAdapter(new ArrayList<>(), item -> {
            boolean success = viewModel.buyItem(item.getId());
            if (success) {
                Toast.makeText(requireContext(), "Megvásárolva: " + item.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Nincs elég coinod ehhez!", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel.getShopItems().observe(getViewLifecycleOwner(), items -> adapter.setItems(items));

        viewModel.getCoins().observe(getViewLifecycleOwner(), coins -> {
            int value = coins == null ? 0 : coins;
            coinsText.setText("💰 " + value);
            adapter.setCurrentCoins(value);
        });

        UIHelper.bindBackgroundToZone(this, viewModel);
    }
}
