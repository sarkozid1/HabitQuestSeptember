# HabitQuest – szabályok és projekt-áttekintés

Szokáskövető Android-app RPG- és Tamagotchi-elemekkel, Play Store-kiadásra készül.
A felhasználó magyarul kommunikál; a válaszok, a kommentek és a UI-szövegek magyarul legyenek.
A fejlesztési fázisokat a [ROADMAP.md](ROADMAP.md) követi.

## Tech stack

| | |
|---|---|
| Nyelv | Java 11 (nem Kotlin) |
| Platform | Android, minSdk 29, targetSdk / compileSdk 35 |
| Architektúra | MVVM: egy közös `MainViewModel` (Activity-szintű, a Fragmentek osztoznak rajta) + `LiveData` |
| UI | Fragmentek + alsó navigáció, Material Components 3 (`Theme.Material3.DayNight.NoActionBar`) |
| Háttérfeladatok | AndroidX WorkManager |
| Tárolás | SharedPreferences (`habitquest_prefs`) + Gson JSON; kulcsok: `character_data`, `habits_data`, `achievements_data`, `water_reminder_enabled` |
| Tesztek | JUnit 4 + Robolectric (`app/src/test`), AndroidX Test / Espresso (`app/src/androidTest`) |

## Mappastruktúra és felelősségek

```
app/src/main/java/com/example/habitquest/
├── MainActivity.java     # navigáció, onboarding-átirányítás, értesítésből nyitott fül
├── data/                 # HabitRepository: KÖZÖS adatréteg (folyamatonként egy példány) – app, widget,
│                         #   workerek mind ezen át olvasnak/írnak; jutalom, streak, achievement, mentés
├── model/                # tiszta Java domain + logika (Android-függés nélkül, kivéve R.drawable)
│                         #   Habit (teljesítés, streak, esedékesség, emlékeztető, napi előzmény),
│                         #   Schedule, HabitStats (statisztika), RewardRules (jutalom),
│                         #   UserCharacter (XP, szint, statok), WorldZone, ShopItem, Achievement
├── viewmodel/            # MainViewModel: a repository tükrözése + származtatott állapot (mai lista,
│                         #   zóna, statok); StatsSnapshot, OnboardingViewModel, HabitListItem
├── view/                 # Fragmentek, OnboardingActivity, adapterek, egyedi View-k (MapPathView,
│                         #   HeatmapView), HabitFormBinder. CSAK megjelenítés és input továbbítása
├── notifications/        # WaterWorker, HabitReminderWorker, NotificationHelper,
│                         #   ReminderScheduler (interfész; tesztben NO_OP / fake) + WorkManager implementáció
├── widget/               # kezdőképernyő-widgetek: providerek, HabitWidgets (RemoteViews), WidgetState
│                         #   (tesztelhető állapot), WidgetMidnightWorker, HabitListWidgetService (API<31)
└── utils/                # StorageHelper (mentés, betöltés, MIGRÁCIÓ), BackupManager (JSON export/import)
```

- A dátumfüggő logika a modellben van (`Habit.complete(LocalDate)`, `isDueOn`, `nextReminderAt`,
  `HabitStats`); a repository `Clock`-ot kaphat (`setClock`, tesztben fix óra). Rendszeróránál
  NE tárolj `Clock.systemDefaultZone()`-t: az rögzíti az időzónát (utazás után hibás „ma”).
- Minden adatírás a `HabitRepository`-n át megy; a ViewModel nem tart saját másolatot az adatról.
  A repository minden változás után frissíti a widgeteket (`ChangeListener`).
- Külső szolgáltatást (WorkManager stb.) a ViewModel interfészen át ér el, hogy JVM-tesztből cserélhető legyen.
- A `res/xml/backup_rules.xml` és a `data_extraction_rules.xml` csak a `habitquest_prefs.xml`-t menti;
  új SharedPreferences fájlnál ezeket is bővíteni kell.

## Parancsok

```bash
./gradlew testDebugUnitTest   # unit tesztek (JUnit + Robolectric)
./gradlew assembleDebug       # debug APK: app/build/outputs/apk/debug/app-debug.apk
```

Az `android.util.Log`-ot vagy az `R`-t érintő tesztekhez `@RunWith(RobolectricTestRunner.class)` kell.
A singleton repositoryt használó tesztekben `HabitRepository.resetInstanceForTests()` és
`ReminderScheduler.NO_OP` kell (unit tesztben nincs WorkManager).

**Figyelem:** a Gradle kimenetét ne szűrd csak `error`-ra – egy konfigurációs hiba (pl. sérült
`~/.gradle/caches/.../transforms`) így észrevétlen marad, és a régi APK / régi teszt-riport hamis
eredményt ad. Mindig nézd a `BUILD SUCCESSFUL`-t, töröld előtte az
`app/build/test-results/testDebugUnitTest` mappát, és telepítés előtt ellenőrizd az APK idejét.

## Munkaszabályok

1. **Terv először.** Minden új feladatnál előbb rövid terv (mely fájlok változnak, új osztályok,
   migráció), és meg kell várni a jóváhagyást, mielőtt kód készül.
2. **MVVM.** Logika a modellben / ViewModelben (vagy repositoryban); a Fragment csak megjelenít.
3. **Szövegek.** Minden UI-szöveg a `res/values/strings.xml`-be kerül (későbbi angol fordítás miatt),
   kódba és layoutba nem égetünk be szöveget. Sortörés a stringben: `\n`, nem valódi új sor.
4. **Függőségek.** Új külső könyvtár csak előzetes egyeztetés után.
5. **Adatbiztonság.** Ha a mentett adat szerkezete változik, kötelező a visszafelé kompatibilis
   betöltés (migráció a `StorageHelper.migrate*`-ban / `Habit.migrate()`-ben) és hozzá unit teszt
   régi JSON-ból. Felhasználói adat soha nem veszhet el. Gson-nal mentett osztálynak legyen
   paraméter nélküli (privát) konstruktora, különben a mezők alapértékei nem futnak le.
   Az import ugyanazt a migrációt használja, mint a betöltés.
6. **Óra.** Dátumfüggő logika beinjektálható órával (`java.time.Clock` vagy `LocalDate` paraméter).
7. **Ellenőrzés.** Minden lépés végén `testDebugUnitTest` + `assembleDebug`. A végén rövid
   összefoglaló és kézi tesztelési lista a telefonra.
8. **Stílus.** Fantasy / pergamen / arany: színek a `colors.xml`-ben (`ink_*`, `gold_*`, `glass_*`),
   hátterek a `glass_*` és `*_bg` drawable-ökben (`dialog_scroll_bg`, `input_fantasy_bg`,
   `button_pill_*`). Új UI elem ezekhez illeszkedjen; dialógusok gombjai arany színűek.

## Ellenőrzés emulátoron

- AVD: `Medium_Phone_API_37.0` (SDK: `%LOCALAPPDATA%\Android\Sdk`). Wi-Fi-n gyakran egy valódi
  telefon is csatlakozik – arra csak kifejezett kérésre telepítünk.
- Git Bash alatt az adb eszközútjaihoz `MSYS_NO_PATHCONV=1` kell; mentett adat betöltése:
  `adb shell run-as com.example.habitquest ...`.
- WorkManager job kényszerített futtatása:
  `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.example.habitquest <jobId>`.
- Napváltás root nélkül: `adb shell settings put global auto_time_zone 0`, majd
  `adb shell cmd alarm set-timezone Pacific/Kiritimati` (UTC+14) / `GMT` – a widgetek frissülnek.
- „Zárt app” teszthez `adb shell am kill` (csak a folyamat). A `force-stop` a widgetek
  PendingIntentjeit is törli – az app következő megnyitásáig a widget-gombok nem működnek.

## Ismert technikai adósságok (csak lista – külön feladatban javítandók)

**Méret / erőforrások**
- `res` mappa ~62 MB: a legtöbb PNG 2–3 MB (karakterképek, térképek). WebP-re és kijelzőhöz
  illő felbontásra kell konvertálni.
- Az alsó navigáció ikonjai is teljes méretű PNG-k (pl. `outfit_knight` ~2,3 MB) – vektoros
  vagy kis felbontású ikon kellene.
- Nem hivatkozott képek (~16 MB): `avatar_body`, `background`, `habitquest_logo`, `map_checkpoint`,
  `map_path`, `rpg_button`, `splash_background`. (A `role_playing` a widget-előnézetben használt.)
- `background_fantasy.xml` üres selector (nincs háttér) – ne használd háttérnek, a főképernyő és az
  onboarding a `map_forest` képet használja.
- `styles.xml`: nem használt `AppTheme` (lila), `RpgButton`, `DialogFadeAnimation` stílusok.

**Kód**
- `notifyDataSetChanged()`: `HabitAdapter`, `ShopAdapter`; a `ProfileFragment` minden változáskor
  új `AchievementAdapter`-t hoz létre → `ListAdapter` + `DiffUtil` kellene.
- Beégetett szövegek: `MainActivity` (kasztválasztás toastok), `ShopFragment`, `ShopAdapter`,
  `MapFragment`, valamint a kezdő szokások / achievementek / bolti tételek a `MainViewModel`-ben;
  layoutok: `dialog_class_selection`, `fragment_habit`, `fragment_map`, `fragment_profile`,
  `fragment_shop`, `item_achievement`, `item_habit`, `item_shop`, `view_stat_counter`, `menu/bottom_nav_menu`.
- `HabitRepository.defaultHabits()` (csak tesztek / `loadInitialData`) tartalmaz egy 1125 XP-s
  `"Teszt"` szokást; új felhasználó már nem kapja meg (onboarding), de kiadás előtt törlendő.
- A widget-lista Android 10–11-en az API 35-ben elavult `setRemoteAdapter(Intent)`-et használja
  (`HabitListWidgetService`); ha a minSdk 31-re emelkedik, ez az ág törölhető.
- Achievementek: csak a `first_xp` és a `level_up` oldódik fel; a felhasználó által létrehozott
  (`targetCount`, `linkedHabitName`) achievementeket semmi nem ellenőrzi, és név alapján (nem
  habit-id alapján) kötődnek a szokáshoz.
- `UserCharacter.applyDecay()` sehonnan nincs hívva (a statok nem romlanak); `HabitFragment.animateBackgroundTransition()` nem használt.
- A mentés/export a fő szálon fut (kis adatnál elfogadható).
- Navigációkor minden fül új Fragment-példányt kap (`replace`), az állapot nem marad meg.

**Kiadás**
- `applicationId = "com.example.habitquest"` – a Play Store `com.example` csomagnevet nem fogad el.
- Alapértelmezett Android launcher ikon; az értesítések small icon-ja a színes `ic_launcher_foreground`
  (monokróm ikon kellene).
- `isMinifyEnabled = false` a release buildben; a `material` függőség kétszer szerepel a `build.gradle.kts`-ben.
