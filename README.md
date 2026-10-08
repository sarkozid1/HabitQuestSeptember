# HabitQuest

Szokáskövető Android-alkalmazás RPG- és Tamagotchi-elemekkel. Minden teljesített szokás
tapasztalati pontot (XP), érmét és stat-jutalmat ad a karakterednek, aki ettől szintet lép,
fejlődik a kinézete, és új világzónákba jut el.

## Funkciók

- **Szokások** – szokások felvétele, szerkesztése, törlése és teljesítése. A program számolja
  a napi és az összes teljesítést, valamint az aktuális és a leghosszabb napi sorozatot (streak).
- **Karakter és kasztok** – első indításkor kasztot választasz: **Harcos ⚔️**, **Mágus 🔮**
  vagy **Tolvaj 🗡️**. A kasztok szintlépéskor eltérő bónuszokat adnak, és a karakter képe
  szintenként változik.
- **Tamagotchi-statok** – a karakternek **HP**, **Energia** és **Hangulat** értéke van.
  A szokások típusuk szerint mást adnak (pl. a vízivás energiát, a séta hangulatot ad, a tanulás
  viszont fáraszt).
- **Szintek és XP** – a következő szinthez `szint × 100` XP kell. Szintlépéskor a statok
  feltöltődnek, a maximumok nőnek, és érmét kapsz.
- **Világtérkép** – a szinted alapján haladsz a zónák között:
  Erdő (1–5. szint) → Hegység (6–10.) → Város (11.+).
- **Bolt** – érméből italokat és ételeket vehetsz, amik azonnal visszatöltik a statokat.
- **Achievementek** – feloldható eredmények a profil oldalon.
- **Vízivás-emlékeztető** – WorkManagerrel 30 percenként értesítés, hogy igyál egy pohár vizet
  (Android 13+ esetén értesítési engedély kell hozzá).
- **Helyi mentés** – minden adat a készüléken marad (SharedPreferences + Gson JSON).

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
├── MainActivity.java          # navigáció, kasztválasztás, értesítések beállítása
├── model/                     # Habit, UserCharacter, WorldZone, ShopItem, Achievement
├── viewmodel/MainViewModel    # játéklogika: jutalmak, szintlépés, bolt, zónák
├── view/                      # fragmentek (Szokások, Térkép, Bolt, Profil) és adapterek
├── notifications/WaterWorker  # vízivás-emlékeztető
└── utils/StorageHelper        # mentés és betöltés
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
