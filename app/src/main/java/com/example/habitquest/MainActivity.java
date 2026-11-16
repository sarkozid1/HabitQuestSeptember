package com.example.habitquest;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.view.HabitFragment;
import com.example.habitquest.view.OnboardingActivity;
import com.example.habitquest.view.MapFragment;
import com.example.habitquest.view.ProfileFragment;
import com.example.habitquest.view.ShopFragment;
import com.example.habitquest.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    /** Értesítésből indításkor: melyik fül nyíljon meg. */
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final String TAB_HABITS = "habits";

    private MainViewModel viewModel;

    /** Fragment betöltése a fő containerbe. */
    private void loadFragment(Fragment fragment) {
        // fülváltáskor a megnyitott alképernyő (pl. Statisztika) back stack-je törlődik
        getSupportFragmentManager().popBackStack(null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mainFragmentContainer, fragment)
                .commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Első indítás: bevezető (kasztválasztással); meglévő, kaszt nélküli felhasználónál
        // csak a kasztválasztó lap. A régi kasztválasztó dialógus megszűnt.
        HabitRepository repository = HabitRepository.getInstance(this);
        if (repository.needsOnboarding() || repository.needsClassSelection()) {
            Intent onboarding = new Intent(this, OnboardingActivity.class);
            onboarding.putExtra(OnboardingActivity.EXTRA_CLASS_ONLY, !repository.needsOnboarding());
            startActivity(onboarding);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // ViewModel inicializálása
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // StorageHelper inicializálása
        if (!viewModel.isStorageInitialized()) {
            viewModel.initStorageHelper(this);
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

        // Az értesítési engedélyt az onboarding kéri (új felhasználó), illetve az első
        // emlékeztető bekapcsolásakor kérjük (meglévő felhasználó) – induláskor már nem.

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
}
