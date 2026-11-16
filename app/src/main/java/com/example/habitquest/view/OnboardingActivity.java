package com.example.habitquest.view;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.habitquest.MainActivity;
import com.example.habitquest.R;
import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.Habit;
import com.example.habitquest.notifications.NotificationHelper;
import com.example.habitquest.viewmodel.OnboardingViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Első indítás bevezetője: 3 ismertető lap → kasztválasztás → kezdő szokások + értesítési
 * engedély. Csak egyszer jelenik meg (a repository jegyzi); meglévő felhasználóknál nem.
 * Meglévő, kaszt nélküli felhasználónál ({@link #EXTRA_CLASS_ONLY}) csak a kasztválasztó lap.
 *
 * A kiválasztott értékeket az OnboardingViewModel őrzi (forgatás), a mentést a repository végzi.
 */
public class OnboardingActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ONLY = "class_only";

    private static final int PAGE_WELCOME = 0, PAGE_REWARDS = 1, PAGE_MAP = 2, PAGE_CLASS = 3, PAGE_START = 4;

    private OnboardingViewModel viewModel;
    private ViewPager2 pager;
    private Button backButton, nextButton;
    private TextView dots;
    private List<Integer> pages;
    private final List<StarterHabits.Template> starters = StarterHabits.all();

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Toast.makeText(this, R.string.onboarding_notif_denied, Toast.LENGTH_LONG).show();
                RecyclerView.Adapter<?> adapter = pager.getAdapter();
                if (adapter != null) adapter.notifyItemChanged(pages.indexOf(PAGE_START));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);
        viewModel.applyDefaultStarters(0, 1); // víz + séta javasolt

        boolean classOnly = getIntent().getBooleanExtra(EXTRA_CLASS_ONLY, false);
        pages = classOnly
                ? Arrays.asList(PAGE_CLASS)
                : Arrays.asList(PAGE_WELCOME, PAGE_REWARDS, PAGE_MAP, PAGE_CLASS, PAGE_START);

        pager = findViewById(R.id.onboardingPager);
        backButton = findViewById(R.id.onboardingBack);
        nextButton = findViewById(R.id.onboardingNext);
        dots = findViewById(R.id.onboardingDots);

        pager.setAdapter(new PagesAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // kaszt nélkül nem lehet a kasztválasztón túl lapozni
                int classIndex = pages.indexOf(PAGE_CLASS);
                if (position > classIndex && !viewModel.hasClass()) {
                    Toast.makeText(OnboardingActivity.this, R.string.onboarding_class_required,
                            Toast.LENGTH_SHORT).show();
                    pager.setCurrentItem(classIndex, true);
                    return;
                }
                updateControls(position);
            }
        });

        backButton.setOnClickListener(v -> pager.setCurrentItem(pager.getCurrentItem() - 1, true));
        nextButton.setOnClickListener(v -> onNext());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pager.getCurrentItem() > 0) {
                    pager.setCurrentItem(pager.getCurrentItem() - 1, true);
                } else {
                    finish(); // nincs mentés → következő indításkor újra jön
                }
            }
        });
        updateControls(pager.getCurrentItem());
    }

    private void onNext() {
        int position = pager.getCurrentItem();
        if (pages.get(position) == PAGE_CLASS && !viewModel.hasClass()) {
            Toast.makeText(this, R.string.onboarding_class_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (position < pages.size() - 1) {
            pager.setCurrentItem(position + 1, true);
        } else {
            finishOnboarding();
        }
    }

    private void finishOnboarding() {
        HabitRepository repository = HabitRepository.getInstance(this);
        boolean classOnly = pages.size() == 1;
        List<Habit> chosen = new ArrayList<>();
        if (!classOnly) {
            for (int i : viewModel.getSelectedStarters()) chosen.add(starters.get(i).toHabit(this));
        }
        viewModel.finish(repository, chosen);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HABITS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateControls(int position) {
        backButton.setVisibility(position > 0 ? View.VISIBLE : View.INVISIBLE);
        nextButton.setText(position == pages.size() - 1 ? R.string.onboarding_finish : R.string.onboarding_next);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            sb.append(getString(i == position ? R.string.onboarding_dot_active : R.string.onboarding_dot_inactive));
        }
        dots.setText(pages.size() > 1 ? sb.toString() : "");
    }

    // -------------------------
    // Lapok
    // -------------------------
    private final class PagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return pages.get(position);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout;
            if (viewType == PAGE_CLASS) layout = R.layout.page_onboarding_class;
            else if (viewType == PAGE_START) layout = R.layout.page_onboarding_start;
            else layout = R.layout.page_onboarding_info;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            switch (pages.get(position)) {
                case PAGE_WELCOME:
                    bindInfo(v, R.string.onboarding_welcome_emoji, R.string.onboarding_welcome_title,
                            R.string.onboarding_welcome_body);
                    break;
                case PAGE_REWARDS:
                    bindInfo(v, R.string.onboarding_rewards_emoji, R.string.onboarding_rewards_title,
                            R.string.onboarding_rewards_body);
                    break;
                case PAGE_MAP:
                    bindInfo(v, R.string.onboarding_map_emoji, R.string.onboarding_map_title,
                            R.string.onboarding_map_body);
                    break;
                case PAGE_CLASS:
                    bindClassPage(v);
                    break;
                case PAGE_START:
                    bindStartPage(v);
                    break;
                default:
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }

    private void bindInfo(View v, int emoji, int title, int body) {
        ((TextView) v.findViewById(R.id.pageEmoji)).setText(emoji);
        ((TextView) v.findViewById(R.id.pageTitle)).setText(title);
        ((TextView) v.findViewById(R.id.pageBody)).setText(body);
    }

    private void bindClassPage(View v) {
        View warrior = v.findViewById(R.id.classWarrior);
        View mage = v.findViewById(R.id.classMage);
        View rogue = v.findViewById(R.id.classRogue);
        View[] cards = {warrior, mage, rogue};
        String[] ids = {"warrior", "mage", "rogue"};
        int[] names = {R.string.class_warrior, R.string.class_mage, R.string.class_rogue};

        for (int i = 0; i < cards.length; i++) {
            final int index = i;
            cards[i].setSelected(ids[i].equals(viewModel.getSelectedClass()));
            cards[i].setOnClickListener(c -> {
                viewModel.selectClass(ids[index]);
                for (View card : cards) card.setSelected(card == c);
                Toast.makeText(this, getString(R.string.onboarding_class_chosen, getString(names[index])),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void bindStartPage(View v) {
        LinearLayout list = v.findViewById(R.id.starterList);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < starters.size(); i++) {
            final int index = i;
            StarterHabits.Template t = starters.get(i);
            View item = inflater.inflate(R.layout.item_starter_habit, list, false);
            ((TextView) item.findViewById(R.id.starterEmoji)).setText(t.emojiRes);
            ((TextView) item.findViewById(R.id.starterName)).setText(t.nameRes);
            ((TextView) item.findViewById(R.id.starterDesc)).setText(t.descRes);
            bindStarterState(item, viewModel.isStarterSelected(i));
            item.setOnClickListener(c -> {
                viewModel.toggleStarter(index);
                bindStarterState(item, viewModel.isStarterSelected(index));
            });
            list.addView(item);
        }

        Button notif = v.findViewById(R.id.notificationButton);
        View card = v.findViewById(R.id.notificationCard);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            card.setVisibility(View.GONE); // Android 13 alatt nem kell külön engedély
        } else if (NotificationHelper.canPostNotifications(this)) {
            notif.setText(R.string.onboarding_notif_granted);
            notif.setEnabled(false);
        } else {
            notif.setText(R.string.onboarding_notif_button);
            notif.setEnabled(true);
            notif.setOnClickListener(c -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS));
        }
    }

    private void bindStarterState(View item, boolean selected) {
        item.setSelected(selected);
        TextView check = item.findViewById(R.id.starterCheck);
        check.setText(selected ? getString(R.string.widget_check_done) : "");
        check.setBackgroundResource(selected ? R.drawable.widget_check_done : R.drawable.widget_check_open);
    }
}
