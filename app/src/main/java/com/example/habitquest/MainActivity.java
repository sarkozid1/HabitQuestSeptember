package com.example.habitquest;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.view.HabitFragment;
import com.example.habitquest.view.MapFragment;
import com.example.habitquest.view.ProfileFragment;
import com.example.habitquest.view.ShopFragment;
import com.example.habitquest.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    /** Értesítésből indításkor: melyik fül nyíljon meg. */
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final String TAB_HABITS = "habits";

    private static final int NOTIF_REQ = 101;
    private MainViewModel viewModel;

    /** Fragment betöltése a fő containerbe. */
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mainFragmentContainer, fragment)
                .commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ViewModel inicializálása
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // StorageHelper inicializálása
        if (!viewModel.isStorageInitialized()) {
            viewModel.initStorageHelper(this);
        }

        // Kasztválasztás csak első indításkor
        UserCharacter user = viewModel.getCharacter().getValue();
        if (user != null &&
                (user.getCharacterClass() == null ||
                        user.getCharacterClass().equals("none"))) {
            showClassSelectionDialog();
        }

        // Alapértelmezett képernyő: Szokások
        if (savedInstanceState == null) {
            loadFragment(new HabitFragment());
        }

        // Alsó navigáció kezelése
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_habits) {
                loadFragment(new HabitFragment());
                return true;
            } else if (id == R.id.nav_map) {
                loadFragment(new MapFragment());
                return true;
            } else if (id == R.id.nav_shop) {
                loadFragment(new ShopFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });

        // 👉 Értesítési engedélykérés (a vízivás- és szokás-emlékeztetőket a ViewModel ütemezi)
        requestNotificationPermission();

        handleOpenTabIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenTabIntent(intent);
    }

    /** Értesítésre koppintva a Szokások fül nyílik meg. */
    private void handleOpenTabIntent(Intent intent) {
        if (intent == null || !TAB_HABITS.equals(intent.getStringExtra(EXTRA_OPEN_TAB))) return;
        intent.removeExtra(EXTRA_OPEN_TAB); // forgatáskor ne ugorjon vissza
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav.getSelectedItemId() != R.id.nav_habits) {
            bottomNav.setSelectedItemId(R.id.nav_habits); // a listener betölti a fragmentet
        }
    }

    /** Android 13+ engedélykérés */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_REQ
                );
            }
        }
    }

    // Kasztválasztó dialógus az első indításkor
    private void showClassSelectionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_class_selection, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.classWarrior).setOnClickListener(v -> {
            viewModel.setCharacterClass("warrior");
            Toast.makeText(this, "Kaszt kiválasztva: Harcos ⚔️", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.classMage).setOnClickListener(v -> {
            viewModel.setCharacterClass("mage");
            Toast.makeText(this, "Kaszt kiválasztva: Mágus 🔮", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.classRogue).setOnClickListener(v -> {
            viewModel.setCharacterClass("rogue");
            Toast.makeText(this, "Kaszt kiválasztva: Tolvaj 🗡️", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }
}
