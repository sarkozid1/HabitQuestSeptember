# HabitQuest – Roadmap

Jelölés: `[x]` kész · `[ ]` hátravan · **Függ:** mire épül a feladat.
A munkaszabályokat a [CLAUDE.md](CLAUDE.md) tartalmazza (terv → jóváhagyás → tesztek + build).

---

## [x] 1. fázis – Alapok

A kódban ellenőrizve kész: 135 unit teszt zöld, és emulátoron is végigpróbálva.
A munka a `feature/phase1-alapok` ágon van (commit `7c51566`, pusholva). **A `main`-be még nincs
merge-ölve.**

- [x] Stabil szokás-azonosító (UUID), id alapú ViewModel/adapter; régi JSON migrációja (`Habit.migrate`, `StorageHelper.migrateHabits`)
- [x] Rugalmas ütemezés (`Schedule`: minden nap / adott napok / heti X), streak csak esedékes alkalom kihagyásánál szakad; esedékesek elöl, a többi halványítva
- [x] Szokásonkénti emlékeztető (`HabitReminderWorker`, unique work `habit_reminder_<id>`), koppintásra a Szokások fül; vízivás-kapcsoló a Profilon
- [x] Export/import verziózott JSON-ba (`BackupManager`, `schemaVersion: 1`, SAF), validálás, megerősítés
- [x] Auto Backup / device-transfer szabályok a `habitquest_prefs.xml`-re
- [x] Javítva: a mentett karakter induláskor nem került a LiveData-ba (elveszett haladás)

---

## [x] 2. fázis – Láthatóság

A `feature/phase2-lathatosag` ágon (az 1. fázis ágára épül), 185 unit teszt zöld, emulátoron
végigpróbálva. **Még nincs commitolva / merge-ölve.**

- [x] **Közös adatréteg** – `HabitRepository` (folyamatonként egy példány): az app, a widget és a
  workerek ugyanazt a jutalom-, streak- és achievement-logikát használják (`RewardRules`); a nyitott
  app LiveData-i azonnal frissülnek a widgetes teljesítéskor. A „ma” mindig az aktuális időzóna
  szerint számolódik (utazás / időzóna-váltás).
- [x] **Teljesítési napló** – `Habit.history` (dátum → darab, max. 400 nap) + `createdDate`;
  migráció régi mentésből (üres előzmény, számlálók változatlanok); export/import is viszi
  (`schemaVersion` maradt 1, mert a bővítés visszafelé kompatibilis).
- [x] **Kezdőképernyő-widgetek** – kicsi + közepes (lista teljesítés-gombbal). Lista: Android 12+
  natív `RemoteCollectionItems`, Android 10–11 `RemoteViewsService` (új függőség nélkül).
  Frissítés minden változáskor, éjfélkor (`WidgetMidnightWorker`), idő/időzóna-váltáskor.
- [x] **Statisztika képernyő** – `HeatmapView` (13 hét), heti összesítő, szokásonkénti arány az
  ütemezés szerint (`HabitStats`); a Profilból nyílik.
- [x] **Onboarding** – `OnboardingActivity` (ViewPager2): 3 ismertető lap, kasztválasztás (a régi
  dialógus helyett), kezdő szokások, értesítési engedély magyarázattal; meglévő felhasználónál nem
  jelenik meg (`onboarding_done`), kaszt nélküli meglévő felhasználónál csak a kasztválasztó lap.
- [x] Tesztek: repository, napló-migráció, statisztika (ütemezés, üres előzmény, hónap/év-váltás),
  widget-állapot, onboarding, időzóna-váltás.

Ismert korlát: a sikerességi arány a szokás *jelenlegi* ütemezésével számol (a korábbi ütemezések
nincsenek tárolva).

---

## [x] 3. fázis – Élő avatar és térkép

A `feature/phase3-elo-avatar` ágon (a 2. fázis ágára épül), 200 unit teszt zöld, emulátoron
végigpróbálva. **Még nincs commitolva / merge-ölve.** A karakterképek csak merev mozgást kapnak
(eltolás, méretezés, tükrözés, billenés) – a rajz nem torzul.

- [x] **Szintlépés mint állapot** – `LevelUpCelebration` + mentett „utoljára megünnepelt szint”
  (`celebrated_level`, `pending_level_coins`): nyugtázásig függő, újraindítást/forgatást túlél,
  több szintet összesít, a widgetes szintlépést is megünnepli. Régi mentésnél / importnál nincs
  utólagos ünneplés. (A tervezett egyszeri `Event<T>` helyett – így robusztusabb.)
- [x] **Szintlépés-animáció** – `LevelUpOverlay` + `LevelUpRaysView` (forgó sugarak, szikrák),
  új zóna jelzése; a térkép régi „Új terület” ablaka és a memóriabeli `lastDiscoveredZone` megszűnt.
- [x] **Avatar idle animáció** – `IdleFloatAnimator` (lebegés + árnyék), életciklushoz és a fül
  láthatóságához kötve.
- [x] **Térkép: tükrözés + lépés-ugrálás** – `MapMotion` (tesztelt görbék), billegés, talajon
  maradó árnyék, leérkezés; a zónanevek és a térkép szövegei magyarul a `strings.xml`-ből.
- [x] **Folyó ösvény és pulzáló checkpoint** – egy animátor, csak látható nézetnél
  (`onVisibilityAggregated`).
- [x] **Fragmentváltás animáció** – irány szerinti csúszás + áttűnés; a fülek show/hide-dal
  váltanak (állapot megmarad), a statisztika add+hide-dal nyílik; forgatás után a fül megmarad.

---

## [ ] 4. fázis – Játékmenet

Cél: motiváló, de kíméletes játékmenet, ami a saját szokásokra épül.

- [ ] **Streak-pajzs** – coinért vehető tárgy, egy kimaradt esedékes alkalmat „lefed”; a `Habit.isStreakBrokenBetween` figyelembe veszi.
  **Függ:** 1. fázis (ütemezés-alapú streak), bolt.
- [ ] **Pihenőnap** – heti/havi keret, amikor egy nap kimaradása semmilyen streaket nem tör meg (pl. betegség).
  **Függ:** 1. fázis (streak-logika), injektálható óra.
- [ ] **Napi küldetések a saját szokásokból** – naponta 1–3 véletlen kihívás a ma esedékes szokásokból (pl. „teljesíts 2 szokást dél előtt”), extra XP/coin.
  **Függ:** 1. fázis (`isDueOn`, habit id), 2. fázis (teljesítési napló az időpontokhoz).
- [ ] **Zóna-boss** – minden zóna végén boss, akinek a HP-ját a teljesítések sebzik; legyőzése nyitja a következő zónát.
  **Függ:** 3. fázis (`LevelUpCelebration` / zónaváltás, animációk), `WorldZone.forLevel`.
- [ ] **Rövid zónatörténet** – zónánként 2–3 pergamen-szöveg (felfedezéskor, boss előtt és után), `strings.xml`-ben.
  **Függ:** zóna-boss, 3. fázis eseményrendszere.
- [ ] **Achievementek rendbetétele** – a `targetCount` / linkelt achievementek tényleges ellenőrzése, név helyett habit-id alapú kötés, migrációval.
  **Függ:** 1. fázis (habit id, migráció).

---

## [ ] 5. fázis – Kiadás előtti csiszolás

Cél: Play Store-kész, kicsi, stabil, kétnyelvű app.

- [ ] **Angol lokalizáció** – előbb a maradék beégetett szövegek kiszervezése (lista a `CLAUDE.md`-ben), utána `values-en/strings.xml`; a kezdő szokások és a bolti tételek is lokalizálva.
  **Függ:** minden korábbi fázis UI-szövegei.
- [ ] **Képek WebP-re, kisebb méretre** – ~62 MB `res` → cél < 10 MB; a nem használt képek törlése (~16 MB); `isMinifyEnabled` + `shrinkResources` a release-ben.
  **Függ:** 3. fázis (az avatar-/térképképek véglegesítése).
- [ ] **`ListAdapter` + `DiffUtil`** – `HabitAdapter`, `ShopAdapter`, `AchievementAdapter` (a `notifyDataSetChanged` és az adapter-újralétrehozás helyett).
  **Függ:** 1. fázis (stabil id = DiffUtil kulcs).
- [ ] **In-App Review API** – értékelés kérése jó pillanatban (pl. 7 napos sorozat után), ritkítva.
  **Függ:** új függőség (`com.google.android.play:review`) – előzetes egyeztetés kell.
- [ ] **Ikon és arculat** – saját adaptív launcher ikon (+ monokróm), monokróm értesítési ikon, `applicationId` cseréje `com.example`-ről (ez a már telepített példányok szempontjából új app!).
  **Függ:** –
- [ ] **Crash-mentesség** – a `Teszt` szokás törlése, Robolectric/Espresso smoke tesztek a fő képernyőkre, StrictMode debugban, a fő szálas IO kiszervezése.
  **Függ:** –

---

## [ ] 6. fázis – Később

- [ ] **Kozmetikai bolt** – ruhák/kiegészítők az avatarra (pl. a meglévő, de nem használt `avatar_body` alapkép és a `outfit_knight` stílusa alapján), coinért.
  **Függ:** 3. fázis (rétegzett avatar), 5. fázis (képoptimalizálás).
- [ ] **Premium** – egyszeri vásárlás vagy előfizetés (Google Play Billing), extra kozmetikumok/statisztikák.
  **Függ:** kozmetikai bolt, új függőség (Play Billing) – egyeztetés kell.
- [ ] **Közösségi funkciók** – barátok, közös kihívások, ranglista; szerveroldal / fiók kell (pl. Firebase).
  **Függ:** 2. fázis (napló), backend és adatvédelmi döntések (adatvédelmi nyilatkozat, Play Data safety).
- [ ] **Felhőszinkron** – a JSON export/import helyett automatikus szinkron fiókkal.
  **Függ:** 1. fázis (`BackupManager` formátum), közösségi funkciók backendje.
