package com.example.habitquest.data;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.RewardRules;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.model.ShopItem;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.notifications.ReminderScheduler;
import com.example.habitquest.notifications.WorkManagerReminderScheduler;
import com.example.habitquest.utils.BackupManager;
import com.example.habitquest.utils.StorageHelper;
import com.example.habitquest.widget.HabitWidgets;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Közös adatréteg: az app (ViewModel), a widget és a háttérfeladatok (workerek) mind ezen
 * keresztül olvasnak és írnak, így ugyanaz a jutalom-, streak- és achievement-logika fut.
 *
 * Folyamatonként egyetlen példány ({@link #getInstance}): a widget receivere és a workerek
 * ugyanabban a folyamatban futnak, mint az app, ezért ha a widget teljesít egy szokást, miközben
 * az app nyitva van, a LiveData-k azonnal frissülnek. Ha az app nem futott, induláskor úgyis
 * a mentett állapotból tölt.
 *
 * Minden módosító metódus synchronized (a workerek háttérszálon futnak).
 */
public class HabitRepository {

    private static final String TAG = "HabitRepository";

    /** Értesítés minden adatváltozásról (pl. widget frissítése). */
    public interface ChangeListener {
        void onDataChanged();

        ChangeListener NO_OP = () -> { };
    }

    private static HabitRepository instance;

    /** Az app-szintű példány (első híváskor betölti a mentett adatokat). */
    public static synchronized HabitRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            HabitRepository repo = new HabitRepository(
                    new StorageHelper(app),
                    new WorkManagerReminderScheduler(app),
                    null, // rendszeróra (a "ma" mindig az aktuális időzónában)
                    // minden adatváltozáskor a kihelyezett widgetek is frissülnek
                    () -> HabitWidgets.updateAll(app));
            repo.loadFromStorage();
            instance = repo;
        }
        return instance;
    }

    /** Csak tesztekhez: a singleton eldobása (pl. adatok törlése után). */
    @VisibleForTesting
    public static synchronized void resetInstanceForTests() {
        instance = null;
    }

    @Nullable private final StorageHelper storage; // null = csak memóriában (unit teszt)
    private ReminderScheduler reminderScheduler;
    /**
     * Teszthez beállított óra; null = rendszeróra. Rendszerórát szándékosan NEM tárolunk
     * (Clock.systemDefaultZone() a létrehozáskor rögzítené az időzónát, így utazás /
     * időzóna-váltás után a "ma" hibás lenne, amíg a folyamat él).
     */
    @Nullable private Clock clock;
    private ChangeListener changeListener;

    // állapot (a LiveData-k ugyanezekre az objektumokra mutatnak)
    private UserCharacter character = new UserCharacter();
    private List<Habit> habits = new ArrayList<>();
    private List<Achievement> achievements = new ArrayList<>();
    private boolean waterReminderEnabled = true;
    /** Memóriában élő (teszt) példánynál nincs onboarding. */
    private boolean onboardingDone = true;

    private final MutableLiveData<UserCharacter> characterLive = new MutableLiveData<>(character);
    private final MutableLiveData<List<Habit>> habitsLive = new MutableLiveData<>(habits);
    private final MutableLiveData<List<Achievement>> achievementsLive = new MutableLiveData<>(achievements);
    private final MutableLiveData<Boolean> waterLive = new MutableLiveData<>(true);

    public HabitRepository(@Nullable StorageHelper storage, @Nullable ReminderScheduler scheduler,
                           @Nullable Clock clock, @Nullable ChangeListener listener) {
        this.storage = storage;
        this.reminderScheduler = scheduler != null ? scheduler : ReminderScheduler.NO_OP;
        this.clock = clock;
        this.changeListener = listener != null ? listener : ChangeListener.NO_OP;
    }

    /** Csak memóriában élő példány (ViewModel unit tesztekhez). */
    public static HabitRepository inMemory() {
        return new HabitRepository(null, null, null, null);
    }

    // -------------------------
    // Beállítható függőségek
    // -------------------------
    public synchronized void setClock(@Nullable Clock clock) {
        this.clock = clock;
    }

    public synchronized void setReminderScheduler(@Nullable ReminderScheduler scheduler) {
        this.reminderScheduler = scheduler != null ? scheduler : ReminderScheduler.NO_OP;
    }

    public synchronized void setChangeListener(@Nullable ChangeListener listener) {
        this.changeListener = listener != null ? listener : ChangeListener.NO_OP;
    }

    /** A mai nap: a beállított óra szerint, egyébként az AKTUÁLIS rendszer-időzónában. */
    public synchronized LocalDate today() {
        return clock != null ? LocalDate.now(clock) : LocalDate.now();
    }

    // -------------------------
    // Olvasás
    // -------------------------
    public LiveData<UserCharacter> getCharacter() { return characterLive; }
    public LiveData<List<Habit>> getHabits() { return habitsLive; }
    public LiveData<List<Achievement>> getAchievements() { return achievementsLive; }
    public LiveData<Boolean> getWaterReminderEnabled() { return waterLive; }

    public synchronized UserCharacter getCharacterSnapshot() { return character; }

    /** A szokások másolata (a lista szerkezete szabadon bejárható, az elemek a valódiak). */
    public synchronized List<Habit> getHabitsSnapshot() { return new ArrayList<>(habits); }

    public synchronized List<Achievement> getAchievementsSnapshot() { return new ArrayList<>(achievements); }

    public synchronized boolean isWaterReminderEnabled() { return waterReminderEnabled; }

    @Nullable
    public synchronized Habit findHabit(@Nullable String id) {
        if (id == null) return null;
        for (Habit h : habits) {
            if (id.equals(h.getId())) return h;
        }
        return null;
    }

    // -------------------------
    // Betöltés
    // -------------------------

    /** A mentett állapot (újra)betöltése (migrációval együtt, lásd StorageHelper). */
    public synchronized void loadFromStorage() {
        if (storage == null) return;

        // Meglévő felhasználó (van mentett karaktere / szokása): az onboarding nem jelenik meg.
        // Ezt minden mentés ELŐTT kell eldönteni.
        if (!storage.isOnboardingDone() && storage.hasSavedUserData()) {
            storage.setOnboardingDone(true);
        }
        onboardingDone = storage.isOnboardingDone();

        UserCharacter loadedCharacter = storage.loadCharacter();
        character = loadedCharacter != null ? loadedCharacter : new UserCharacter();

        // Üres lista esetén NEM töltünk vissza alapértelmezett szokásokat: új felhasználó az
        // onboardingban választ, aki pedig mindet törölte, annál ne jöjjenek vissza.
        List<Habit> loadedHabits = storage.loadHabits();
        habits = loadedHabits != null ? loadedHabits : new ArrayList<>();

        List<Achievement> loadedAchievements = storage.loadAchievements();
        if (loadedAchievements != null && !loadedAchievements.isEmpty()) {
            achievements = new ArrayList<>(loadedAchievements);
        } else {
            achievements = defaultAchievements();
            storage.saveAchievements(achievements);
        }

        waterReminderEnabled = storage.isWaterReminderEnabled();
        publishAll();
        Log.d(TAG, "Betöltve: " + habits.size() + " szokás, szint " + character.getLevel());
    }

    /** Induláskor: emlékeztetők újraütemezése (időzóna-/óraváltás, Google-visszaállítás után is). */
    public synchronized void rescheduleAllReminders() {
        reminderScheduler.rescheduleAll(new ArrayList<>(habits));
        reminderScheduler.setWaterReminderEnabled(waterReminderEnabled);
    }

    // -------------------------
    // Szokások
    // -------------------------

    /**
     * Szokás teljesítése: streak, jutalom (XP, érme, statok), achievementek, mentés,
     * emlékeztető újraütemezése, widget frissítése.
     * @return true, ha volt ilyen szokás
     */
    public synchronized boolean completeHabit(@Nullable String habitId) {
        Habit habit = findHabit(habitId);
        if (habit == null) return false;

        habit.complete(today());

        int prevLevel = character.getLevel();
        RewardRules.apply(character, habit);
        if (character.getLevel() > prevLevel) {
            Log.d(TAG, "Szintlépés! Új szint: " + character.getLevel());
        }

        boolean achievementsChanged = false;
        if (character.getExperience() >= 10) achievementsChanged |= unlockInternal("first_xp");
        if (character.getLevel() >= 2) achievementsChanged |= unlockInternal("level_up");

        saveCharacter();
        saveHabits();
        if (achievementsChanged) saveAchievements();
        publishAll();

        // ha ma már teljesítve van, a mai emlékeztető kimarad → a következő esedékes napra ugrik
        reminderScheduler.schedule(habit);
        notifyChanged();
        return true;
    }

    public synchronized void addHabit(@NonNull Habit habit) {
        // új szokás: a felvétel napja a repository órája szerint (ha még nem teljesítették)
        if (habit.getCompletionCount() == 0) habit.setCreatedDate(today());
        habits.add(habit);
        saveHabits();
        publish(habitsLive, habits);
        reminderScheduler.schedule(habit);
        notifyChanged();
    }

    /** Szokás módosítása (név, leírás, XP, ütemezés, emlékeztető – null = nincs). */
    public synchronized void updateHabit(String habitId, String name, String description,
                                         int experienceReward, Schedule schedule,
                                         @Nullable Integer reminderMinutes) {
        Habit habit = findHabit(habitId);
        if (habit == null) return;

        habit.setName(name);
        habit.setDescription(description);
        habit.setExperienceReward(experienceReward);
        habit.setSchedule(schedule);
        habit.setReminderMinutes(reminderMinutes);

        saveHabits();
        publish(habitsLive, habits);
        reminderScheduler.schedule(habit); // frissíti vagy törli az emlékeztetőt
        notifyChanged();
    }

    public synchronized void deleteHabit(String habitId) {
        Habit habit = findHabit(habitId);
        if (habit == null) return;

        habits.remove(habit);
        reminderScheduler.cancel(habitId);
        saveHabits();
        publish(habitsLive, habits);
        notifyChanged();
    }

    /** Alapértelmezett szokáslista (tesztekhez / fejlesztéshez; új felhasználó az onboardingban választ). */
    public synchronized void resetToDefaultHabits() {
        habits = defaultHabits();
        for (Habit h : habits) h.setCreatedDate(today());
        saveHabits();
        publish(habitsLive, habits);
        notifyChanged();
    }

    private static List<Habit> defaultHabits() {
        List<Habit> list = new ArrayList<>();
        list.add(new Habit("Víz számláló", "Legalább 30 percenként igyál meg egy pohár vizet!", 10));
        list.add(new Habit("10 perc séta", "Sétálj egyet a szabadban", 15));
        list.add(new Habit("Tanulás 30 perc", "Tanulj fókuszáltan legalább 30 percet", 25));
        list.add(new Habit("Teszt", "sok XP", 1125));
        return list;
    }

    // -------------------------
    // Onboarding
    // -------------------------

    /** Első indítás: még nem futott le a bevezető (és nem meglévő felhasználó). */
    public synchronized boolean needsOnboarding() {
        return !onboardingDone;
    }

    /** Meglévő felhasználó, akinek még nincs kasztja. */
    public synchronized boolean needsClassSelection() {
        String c = character.getCharacterClass();
        return c == null || c.isEmpty() || "none".equals(c);
    }

    /**
     * Az onboarding lezárása: kaszt beállítása, a kiválasztott kezdő szokások felvétele,
     * és a "lefutott" jelző mentése.
     */
    public synchronized void completeOnboarding(String characterClass, @Nullable List<Habit> starterHabits) {
        if (characterClass != null) applyCharacterClass(characterClass);
        if (starterHabits != null) {
            for (Habit h : starterHabits) {
                h.setCreatedDate(today());
                habits.add(h);
                reminderScheduler.schedule(h);
            }
        }
        onboardingDone = true;
        if (storage != null) storage.setOnboardingDone(true);
        saveCharacter();
        saveHabits();
        publishAll();
        notifyChanged();
    }

    // -------------------------
    // Karakter
    // -------------------------

    /** Kaszt választása: a karakter 1. szintről, feltöltött statokkal indul. */
    public synchronized void setCharacterClass(String characterClass) {
        applyCharacterClass(characterClass);
        saveCharacter();
        publish(characterLive, character);
        notifyChanged();
    }

    private void applyCharacterClass(String characterClass) {
        character.setCharacterClass(characterClass);
        character.setLevel(1);
        character.setExperience(0);
        character.setHealth(character.getHealthMax());
        character.setEnergy(70);
        character.setMood(70);
        character.setUpdatedAt(System.currentTimeMillis());
        // a kép a szint visszaállítása után legyen újraszámolva
        character.refreshCharacterImage();
    }

    /** Tárgy vásárlása: levonja az érmét és alkalmazza a jutalmat. */
    public synchronized boolean buyItem(@Nullable ShopItem item) {
        if (item == null || character.getCoins() < item.getPrice()) return false;
        character.spendCoins(item.getPrice());
        character.applyReward(item.getHpBonus(), item.getEnergyBonus(), item.getMoodBonus(), 0, 0);
        saveCharacter();
        publish(characterLive, character);
        notifyChanged();
        return true;
    }

    public synchronized void persistCharacter() {
        saveCharacter();
        publish(characterLive, character);
        notifyChanged();
    }

    // -------------------------
    // Achievementek
    // -------------------------
    public synchronized void unlockAchievement(String id) {
        if (unlockInternal(id)) {
            saveAchievements();
            publish(achievementsLive, achievements);
        }
    }

    private boolean unlockInternal(String id) {
        for (Achievement a : achievements) {
            if (a.getId().equals(id)) {
                if (a.isUnlocked()) return false;
                a.setUnlocked(true);
                Log.d(TAG, "Achievement feloldva: " + a.getTitle());
                return true;
            }
        }
        return false;
    }

    public synchronized void addAchievement(@NonNull Achievement achievement) {
        achievements.add(achievement);
        saveAchievements();
        publish(achievementsLive, achievements);
    }

    private static List<Achievement> defaultAchievements() {
        return new ArrayList<>(Arrays.asList(
                new Achievement("first_xp", "Első lépés", "Szerezz 10 XP-t.", false),
                new Achievement("level_up", "Szintlépés", "Érd el a 2. szintet.", false)));
    }

    // -------------------------
    // Beállítások
    // -------------------------
    public synchronized void setWaterReminderEnabled(boolean enabled) {
        waterReminderEnabled = enabled;
        if (storage != null) storage.setWaterReminderEnabled(enabled);
        publish(waterLive, enabled);
        reminderScheduler.setWaterReminderEnabled(enabled);
    }

    // -------------------------
    // Export / import
    // -------------------------
    public synchronized String buildExportJson() {
        return BackupManager.toJson(character, habits, achievements, waterReminderEnabled);
    }

    /** A (már validált és jóváhagyott) mentés betöltése: felülírja a jelenlegi adatokat. */
    public synchronized void applyImport(@Nullable BackupManager.BackupData data) {
        if (data == null || data.getCharacter() == null || data.getHabits() == null) return;

        character = data.getCharacter();
        habits = new ArrayList<>(data.getHabits());
        achievements = new ArrayList<>(data.getAchievements());

        saveCharacter();
        saveHabits();
        saveAchievements();

        Boolean water = data.getWaterReminderEnabled();
        if (water != null) {
            waterReminderEnabled = water;
            if (storage != null) storage.setWaterReminderEnabled(water);
            reminderScheduler.setWaterReminderEnabled(water);
        }
        publishAll();

        // a régi szokások emlékeztetői törlődnek, az importáltak beütemeződnek
        reminderScheduler.rescheduleAll(new ArrayList<>(habits));
        notifyChanged();
    }

    // -------------------------
    // Belső segédek
    // -------------------------
    private void saveCharacter() { if (storage != null) storage.saveCharacter(character); }
    private void saveHabits() { if (storage != null) storage.saveHabits(habits); }
    private void saveAchievements() { if (storage != null) storage.saveAchievements(achievements); }

    private void publishAll() {
        publish(characterLive, character);
        publish(habitsLive, habits);
        publish(achievementsLive, achievements);
        publish(waterLive, waterReminderEnabled);
    }

    /** Fő szálon azonnal, háttérszálról (worker) postValue-val. */
    private static <T> void publish(MutableLiveData<T> live, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            live.setValue(value);
        } else {
            live.postValue(value);
        }
    }

    private void notifyChanged() {
        try {
            changeListener.onDataChanged();
        } catch (RuntimeException e) {
            Log.w(TAG, "ChangeListener hiba", e); // a widget hibája ne akassza meg az appot
        }
    }
}
