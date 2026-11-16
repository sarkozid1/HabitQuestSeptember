# HabitQuest

Szokáskövető Android-alkalmazás RPG- és Tamagotchi-elemekkel. Minden teljesített szokás
tapasztalati pontot (XP), érmét és stat-jutalmat ad a karakterednek, aki ettől szintet lép,
fejlődik a kinézete, és új világzónákba jut el.

## Funkciók

- **Szokások** – szokások felvétele, szerkesztése, törlése és teljesítése. A program számolja
  a napi és az összes teljesítést, valamint az aktuális és a leghosszabb sorozatot (streak).
  Minden szokásnak stabil azonosítója (UUID) van; a régi mentések betöltéskor automatikusan
  migrálódnak, adatvesztés nélkül.
- **Rugalmas ütemezés** – egy szokás lehet *minden napos*, *a hét bizonyos napjain* esedékes
  (pl. H, Sze, P) vagy *heti X alkalom* (bármelyik napon). A sorozat csak akkor szakad meg, ha
  egy esedékes alkalom kimarad – a nem esedékes napok kihagyása nem számít. A listán a ma
  esedékes szokások vannak elöl, a nem esedékesek halványítva a végén.
- **Szokásonkénti emlékeztető** – szokásonként beállítható időpont, ami csak esedékes napokon
  szól, és akkor sem, ha a szokás aznap már teljesítve van. Az értesítésre koppintva az app a
  Szokások képernyőn nyílik meg. (WorkManager, nem kér pontos-riasztás engedélyt; Doze módban
  néhány perc csúszás előfordulhat.)
- **Bevezető (onboarding)** – első indításkor lapozható bevezető: mi a HabitQuest, hogyan kapsz
  XP-t és érmét, a térkép és a zónák, majd kasztválasztás, végül kezdő szokások egy koppintással
  (víz, séta, olvasás, tanulás, edzés, meditáció) és az értesítési engedély kérése magyarázattal.
  Csak egyszer jelenik meg; meglévő felhasználóknál nem.
- **Karakter és kasztok** – kasztot választasz: **Harcos ⚔️**, **Mágus 🔮** vagy **Tolvaj 🗡️**.
  A kasztok szintlépéskor eltérő bónuszokat adnak, és a karakter képe szintenként változik.
- **Tamagotchi-statok** – a karakternek **HP**, **Energia** és **Hangulat** értéke van.
  A szokások típusuk szerint mást adnak (pl. a vízivás energiát, a séta hangulatot ad, a tanulás
  viszont fáraszt).
- **Szintek és XP** – a következő szinthez `szint × 100` XP kell. Szintlépéskor a statok
  feltöltődnek, a maximumok nőnek, és érmét kapsz.
- **Világtérkép** – a szinted alapján haladsz a zónák között:
  Erdő (1–5. szint) → Hegység (6–10.) → Város (11.+).
- **Bolt** – érméből italokat és ételeket vehetsz, amik azonnal visszatöltik a statokat.
- **Achievementek** – feloldható eredmények a profil oldalon.
- **Statisztika** – a Profilból nyílik: GitHub-stílusú hőtérkép az elmúlt 3 hónapról (összesítve
  vagy szokásonként szűrve, a nem esedékes napok jelölve), heti összesítő az előző héthez képest,
  és szokásonként aktuális / leghosszabb sorozat, sikerességi arány az esedékes napokhoz képest,
  összes teljesítés. A napi teljesítési előzményt az app kb. 13 hónapig őrzi.
- **Kezdőképernyő-widgetek** – *Kicsi* (avatar, szint, XP, „ma X/Y kész”) és *Közepes* (a ma
  esedékes szokások listája, mindegyik mellett teljesítés-gomb; a kész szokás kipipálva). A widgetről
  teljesítés ugyanazt a jutalmat adja, mint az appban, és a nyitott app is azonnal frissül.
  Éjfélkor és időzóna-váltáskor a widgetek új napra váltanak.
- **Vízivás-emlékeztető** – WorkManagerrel 30 percenként értesítés, hogy igyál egy pohár vizet.
  A Profil → Beállítások / Adatok részen ki-/bekapcsolható. (Android 13+ esetén minden
  értesítéshez értesítési engedély kell.)
- **Mentés és visszaállítás** – a Profil → Beállítások / Adatok részen az összes adat
  (karakter, szokások, achievementek, beállítások) egyetlen verziózott JSON fájlba exportálható,
  és onnan visszatölthető (pl. telefonváltáskor). A rendszer fájlválasztóját használja, így nem
  kér tárhely-engedélyt; import előtt validál és megerősítést kér.
- **Helyi mentés + Google-mentés** – minden adat a készüléken marad (SharedPreferences + Gson
  JSON); az Android automatikus mentése és az eszközök közötti átvitel ezt a fájlt is viszi.

## Technológia

| | |
|---|---|
| Nyelv | Java 11 |
| Platform | Android, minSdk 29 (Android 10), targetSdk 35 |
| Architektúra | MVVM (`ViewModel` + `LiveData`) |
| UI | Fragmentek, Material Components, alsó navigáció |
| Háttérfeladatok | AndroidX WorkManager |
| Tárolás | SharedPreferences + Gson |
| Tesztek | JUnit 4, Robolectric, AndroidX Test / Espresso |

## Projektstruktúra

```
app/src/main/java/com/example/habitquest/
├── MainActivity.java          # navigáció, onboarding-átirányítás, értesítésből nyitott fül
├── data/HabitRepository       # közös adatréteg: app, widget és workerek ugyanazt használják
├── model/                     # Habit, Schedule, HabitStats, RewardRules, UserCharacter, WorldZone, …
├── viewmodel/MainViewModel    # játéklogika: jutalmak, szintlépés, bolt, zónák
├── view/                      # fragmentek (Szokások, Térkép, Bolt, Profil) és adapterek
├── notifications/             # WaterWorker, HabitReminderWorker, ReminderScheduler (WorkManager)
├── widget/                    # kezdőképernyő-widgetek (providerek, RemoteViews, éjféli frissítés)
└── utils/                     # StorageHelper (mentés, migráció), BackupManager (export/import)
```

## Futtatás

1. Klónozd a repót:
   ```bash
   git clone https://github.com/sarkozid1/HabitQuestSeptember.git
   ```
2. Nyisd meg a mappát **Android Studióban**, és várd meg a Gradle-szinkronizálást.
3. Indítsd el emulátoron vagy Android 10+ rendszerű készüléken.

Parancssorból is buildelhető:

```bash
./gradlew assembleDebug
```

## Tesztek

Unit tesztek (modellek, ViewModel, StorageHelper):

```bash
./gradlew test
```

Instrumentált tesztek csatlakoztatott eszközön vagy emulátoron:

```bash
./gradlew connectedAndroidTest
```
