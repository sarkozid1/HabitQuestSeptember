package com.example.habitquest;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.OnBackPressedCallback;
import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.view.LevelUpOverlay;
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
    private LevelUpOverlay levelUpOverlay;

    /** A fülek (az alsó navigáció sorrendjében) és a fragmentjeik címkéi. */
    private static final int[] TAB_ITEMS = {R.id.nav_habits, R.id.nav_map, R.id.nav_shop, R.id.nav_profile};
    private static final String[] TAB_TAGS = {"tab_habits", "tab_map", "tab_shop", "tab_profile"};
    private static final String STATE_TAB = "current_tab";
    private int currentTab = 0;

    private static Fragment createTab(int index) {
        switch (index) {
            case 1: return new MapFragment();
            case 2: return new ShopFragment();
            case 3: return new ProfileFragment();
            default: return new HabitFragment();
        }
    }

    /**
     * Fülváltás: mind a négy fül csak egyszer jön létre, utána show/hide váltja őket
     * (megmarad a görgetés és az állapot). Rövid csúszás + áttűnés a fülek sorrendje szerint.
     */
    private void switchTab(int index, boolean animate) {
        FragmentManager fm = getSupportFragmentManager();
        // a megnyitott alképernyő (pl. Statisztika) bezárul – azonnal, hogy a lenti
        // állapotlekérdezések már a visszaállított fülekre vonatkozzanak
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        if (animate && index != currentTab) {
            boolean forward = index > currentTab;
            tx.setCustomAnimations(
                    forward ? R.anim.tab_in_from_right : R.anim.tab_in_from_left,
                    forward ? R.anim.tab_out_to_left : R.anim.tab_out_to_right);
        }
        for (int i = 0; i < TAB_TAGS.length; i++) {
            Fragment f = fm.findFragmentByTag(TAB_TAGS[i]);
            if (i == index) {
                if (f == null) tx.add(R.id.mainFragmentContainer, createTab(i), TAB_TAGS[i]);
                else tx.show(f);
            } else if (f != null && !f.isHidden()) {
                tx.hide(f);
            }
        }
        tx.commit();
        currentTab = index;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
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

        // ✨ Szintlépés-ünneplés: amíg nincs nyugtázva, látszik (forgatás / újraindítás után is),
        // a "Tovább" után nem jön vissza. A widgetről történt szintlépést is itt ünnepeljük.
        levelUpOverlay = new LevelUpOverlay(findViewById(R.id.levelUpOverlay),
                () -> viewModel.acknowledgeCelebration());
        OnBackPressedCallback dismissCelebration = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                viewModel.acknowledgeCelebration();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, dismissCelebration);
        viewModel.getPendingCelebration().observe(this, celebration -> {
            if (celebration != null) {
                UserCharacter c = viewModel.getCharacter().getValue();
                levelUpOverlay.show(celebration, c != null ? c.getCharacterImageResId() : R.drawable.lv1);
            } else {
                levelUpOverlay.hide();
            }
            dismissCelebration.setEnabled(celebration != null);
        });

        // Alapértelmezett képernyő: Szokások (forgatás után a FragmentManager visszaállítja
        // a füleket a rejtett/látható állapotukkal együtt)
        if (savedInstanceState == null) {
            switchTab(0, false);
        } else {
            currentTab = savedInstanceState.getInt(STATE_TAB, 0);
        }

        // Alsó navigáció kezelése
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            for (int i = 0; i < TAB_ITEMS.length; i++) {
                if (TAB_ITEMS[i] == item.getItemId()) {
                    switchTab(i, true);
                    return true;
                }
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
