# CLAUDE.md – Übergabe für neue Sitzungen

Private Android-App **Life Dashboard** für Daniel (Schüler, 11. Klasse, OSZ Max Taut Berlin). Läuft auf Galaxy S25 (Android 17) und Galaxy Tab S8+ (Android 16).
Nicht im Play Store. Die APK wird per Datei geschickt und von Hand installiert (vorher „Auto Blocker“ kurz ausschalten).

**Aktueller Stand: Version 26** (versionCode 26, Datenbank-Version 12 – unverändert, alle Tests grün).
**Arbeits-Branch:** `ccr-4b9f7b2a-x8cb8a` (Version 26, baut auf `claude/life-dashboard-android-1h4pmk` = Version 23 auf). Dort liegt der ganze Code. `main` enthält nur den ersten, leeren Commit.

## Wie mit Daniel arbeiten
- Immer **auf Deutsch**, einfach und kurz erklären. Daniel ist kein Entwickler und liest oft auf dem Handy.
- Nach jedem Schritt: kurze Zusammenfassung, **neue signierte APK** schicken, alle Tests grün.
- **Jedes Update muss die Daten behalten.** Schema-Änderung → `version` erhöhen, Migration schreiben, `MigrationTest` muss grün sein. Gleiche Signatur verwenden (siehe unten).
- Alles, was die App plant, ist ein **Vorschlag** (✓ Passt / ⏭ Später / ✕). Die App lernt aus den Entscheidungen.
- Sport: **keine Sportart vorschreiben**, nur freie **Sport-Zeitfenster**. Klausur-/Test-Themen trägt Daniel **frei** ein, Vorschläge nur als antippbare Chips.
- Datenschutz: Daten nur lokal (SQLCipher), kein eigener Server, kein Tracking, keine kostenpflichtigen Dienste. Geheimnisse nur im Android Keystore (`SecretStore`), nie im Code oder in Git.
- Ton in der App: Coach-Ton (motivierend, etwas fordernd). Ruhezeit 22–6 Uhr, max. 5 Benachrichtigungen pro Tag.

## Profil (für die Planung)
- Werktags 6:00–22:00, Wochenende ab ca. 9:30. Lernt am besten nachmittags, Pomodoro 25 Min. Schulweg < 30 Min.
- Stundenplan A/B-Woche: KW 46/2026 (9.11.2026) = **Woche A**. Gezählt werden Schulwochen, reine Ferienwochen werden übersprungen. Berliner Ferien 2026/27 sind hinterlegt.
- Floorball Berliner TSC: Mo + Mi, Training 18:30–20:00, Abfahrt 18:00.
- Notenpunkte 0–15, Ziel Abi 1,7. Fächer u. a. TuMM = Management, TuMT = Technik, PM = Projektmanagement, BIO-Pro = Bio, DS = Darstellendes Spiel.
- Ziel Ironman (zuerst 10 km). Minijob als Baustellenhelfer. Taschengeld 20 € am 21. (DKB) + 20 € bar vom Opa. Erspartes = geschützter **Puffer**. Sparziele: Führerschein, Feriencamp.
- Etsy-Shop mit digitalen Downloads (Motiv-Pipeline). Die Etsy-API kommt später, sobald der Shop live ist (`docs/ETSY_API.md`).

## Design & Motivation (seit Version 24)
- Look „ruhig & edel“: feste Farben in `ui/theme/Theme.kt` (kein Dynamic Color mehr), eine Akzentfarbe (Grün), Bereichsfarben nur als kleine Markierung, feine Kartenränder statt Schatten. **Keine Emojis in App-Texten**, keine bunten Verläufe. Hell/Dunkel/Automatisch unter Mehr → Erscheinungsbild.
- Bausteine in `ui/components/`: `SectionCard`, `GradientCard` (= dunkle Kopfkarte), `TileHeader`, `Pill`, `ProgressRing`, `Meter`, `CheckMark` (animierter Haken statt Checkbox), `Modifier.focusOnScroll()` (Rubriken am Bildschirmrand werden leicht kleiner). Nicht `val app = …` als lokalen Namen nehmen (verdeckt das Paket `app.`).
- Navigation `ui/navigation/`: Handy = schwebendes Dock unten (`FloatingDock`) + Seitenleiste per Wisch von links; Tablet = Dock links (`SideDock`), aufklappbar. Fitness und Finanzen liegen direkt im Dock.
- Motivation: `planner/Progress.kt` (Punkte, Level, Serie mit Joker, Wochenringe, Ironman-Weg in 7 Etappen, Abzeichen, Abend-Erinnerung) + `data/repository/ProgressRepository.kt`. **Alles wird aus vorhandenen Tabellen berechnet – keine neue Tabelle, keine Migration.** Anzeige: `ui/progress/`. Weiterhin gilt: keine Sportart vorschreiben.
- Lernplan: `StudyPlanner.plan` rechnet mit der echten freien Zeit pro Tag (Training, Job, Ferien), fängt früher an, wenn es knapp wird (bis 21 Tage), gibt schwachen Fächern mehr Zeit; `StudyPlanner.forecast` liefert „Plan passt / Wird eng / Zeit fehlt“ (Anzeige in der Klausur-Karte unter Lernen).

## Gleichmäßige Planung & Kalender (seit Version 25)
- Daniels Wunsch: **kein Tag mit 15 Minuten neben einem mit vielen Stunden.** Lernzeit: `StudyPlanner.plan` verteilt Einheiten (50 Min) in gleichen Abständen auf den am wenigsten ausgelasteten Tag. Hausaufgaben/Aufgaben mit Termin: `planner/LoadBalancer.kt`, eingebunden in `PlanningEngine.balance()` (14 Tage voraus, Lernplan + Sport + angenommene Blöcke zählen als Last). Etsy und Ziel-Schritte füllen nur leichte Tage bis zur „angenehmen“ Menge (werktags 100, frei 150 Min). Fällige Wiederholungen: nur heute, als ein Block.
- Kalender: Wochenleiste mit Auslastungs-Balken je Tag, darunter der gewählte Tag als Zeitstrahl (`ui/components/DayTimeline.kt`, auch auf „Heute“). Tablet: Wochenliste links, Tag rechts. Keine schmalen Spalten mit umbrechendem Text mehr.
- Finanzen: Karte „Sparplan“ (`planner/Finance.kt` → `SavingsPlan`): Sparraten gegen Taschengeld, Job und Etsy-Gewinn, Lücke in Job-Stunden bzw. Etsy-Verkäufen.
- App-Symbol: Fortschrittsring mit drei Balken (`res/drawable/ic_launcher_foreground.xml`).

## Antrieb & Tagesstruktur (seit Version 26, aus Daniels Antworten)
- **Keine Lern-App** (keine Karteikarten, kein Themen-Abfragen) – die App soll antreiben und den Alltag machbar strukturieren.
- Hindernisse: Handy lenkt ab, Anfangen fällt schwer. Antrieb: **strenger Coach** (Einstellung `strictCoach`, Standard an): verpasste Schul-Blöcke kosten 1 Punkt/Minute, brechen die Serie (kein Joker), Rückstand steht in der Kopfkarte. **Zockzeit** als Belohnung (25 Min Schule / 45 Min Sport → 15 Min), ab 1 Std Rückstand gesperrt, Einlösen in 30-Min-Schritten (DataStore, pro Woche). Logik: `planner/Progress.kt` (BlockLog, RewardState), Daten: `ProgressRepository` (liest `plan_blocks`).
- Handy: nur **Spiegel** (ab 17 Uhr Handy- gegen Schulzeit, `UsageRepository`), nichts sperren. **Wochenbericht** als Bild teilen (`ui/progress/WeeklyReport.kt`, FileProvider `${applicationId}.files`).
- Tag: Plan für morgen abends bestätigen (Karte „Plan für morgen“ ab 19 Uhr + Benachrichtigung 20:30), 21:30 „Handy weg um 22 Uhr“. 1 Std **Pause nach der Schule** (`EntryKind.BREAK`). Schul-Pensum `DayAgenda.schoolCapMinutes`: werktags 2 Std, mit Training/Job 1 Std, Samstag 2 Std, **Sonntag frei** (außer Klausur in 1–2 Tagen). Blocklänge flexibel bis 90 Min. Freizeit nach dem letzten Block wird im Zeitstrahl als „verdient“ markiert.
- Startseite: Kopfkarte „Als Nächstes“ mit **Fokus-Modus** (`ui/focus/FocusMode.kt`, Vollbild-Timer, Punkte + Zockzeit am Ende). Standard-Karten: Fortschritt, Plan für morgen, Aufgaben, Tagesplan (Rest ausgeblendet, `DEFAULT_HIDDEN_CARDS`). Schnell-Eingabe steckt im „Neu“-Menü.
- Sport: 3 × 45 Min Zeitfenster; beim Ablehnen „Nur 15 Minuten?“ (`engine.miniSport`). Daniel ist schon 12 km gelaufen (Winterberg) und will wieder durchstarten.
- **Strava** (`data/sync/StravaRepository.kt`): eigener kostenloser API-Zugang (Callback-Domain `localhost`), Redirect `lifedashboard://localhost/strava` (MainActivity singleTask), Fallback „Link einfügen“. Tokens nur im `SecretStore`. Holt beim Verbinden die ganze Historie, dann alle 3 Std; Doppelte mit Health Connect werden erkannt (gleicher Tag/Art, ±5 Min). Echt gegen Strava getestet ist das noch nicht – nur die Auswertung (`StravaParsingTest`).
- Look: kräftigeres Grün `#3EE29A`/`#07875A`, Energie-Orange für Serie/Zockzeit, wandernde Lichtflecken in Kopfkarten, Ringe mit Verlauf und Lichtpunkt, fette Zahlen. Daniel fand v25 „zum Einschlafen“ – lieber lebendig als zu ruhig.

## Technik
- Kotlin 2.2.21, AGP 8.13.0, Gradle 8.14.3, compileSdk/targetSdk 36, minSdk 34, KSP, Hilt 2.57.2, Compose BOM 2025.05.00, Material 3.
- Room 2.7.1 + SQLCipher 4.10 (Passwort aus dem Keystore), DataStore, WorkManager + hilt-work, Health Connect 1.1.0, Glance-Widgets.
- Module: `:app` (Android) und `:planner` (reines Kotlin, alle Planungs-Algorithmen, JUnit5).
- Architektur: MVVM + Repository, Room ist die einzige Datenquelle (Flows). `PlanningEngine` erzeugt Tagesplan-Vorschläge (`plan_blocks`).

### Wichtige Orte
| Was | Wo |
|---|---|
| Datenbank, Migrationen, Schema | `app/.../data/db/` (`AppDatabase.ALL_MIGRATIONS`), Schemas in `app/schemas/` |
| Startdaten (Stundenplan, Ferien, Ziele …) | `data/seed/SeedDataInitializer.kt` (seedVersion, aktuell 8) |
| Einstellungen | `data/settings/SettingsRepository.kt` |
| Tagesplanung | `data/planning/PlanningEngine.kt` + `planner/DayPlanner.kt` |
| Lernplan | `planner/StudyPlanner.kt`, `data/repository/StudyRepository.kt` |
| Sport-Zeitfenster | `planner/SportWindows.kt`, `data/repository/TrainingRepository.kt` |
| Fitness / Health Connect | `data/sync/HealthRepository.kt`, `ui/sport/FitnessScreen.kt` |
| Abgleich Handy ↔ Tablet | `data/devicesync/` (siehe unten) |
| Backup | `data/backup/BackupManager.kt`, Krypto in `core/security/PasswordCrypto.kt` |
| Erinnerungen / Hintergrund | `data/notify/ReminderWorker.kt` (alle 30 Min), `devicesync/SyncWorker.kt` |
| Navigation | `ui/navigation/LifeDashboardRoot.kt`, Bereichsliste in `ui/more/MoreScreen.kt` |

### Abgleich Handy ↔ Tablet (`data/devicesync/`)
- Die Tabelle `sync_meta` (Kennung `gid`, `updatedAt`, `deleted`) wird von **SQLite-Triggern** gepflegt (`SyncSchema.createTriggers`, angelegt im Callback `AppDatabase.SYNC_CALLBACK`).
- `SyncEngine`: Schnappschuss und Zusammenführen. Kennungen werden aus Merkmalen gebildet (`naturalKey`), damit Startdaten nicht doppelt werden. Verweise laufen über die Kennungen (`refs`). Bei Konflikten gewinnt die neuere Änderung, Löschen wird übertragen.
- Übertragung: verschlüsselte Datei per SAF (z. B. Google Drive) **und** WLAN (NSD `_lifedash._tcp` + Socket, `LanSyncManager`). Ein Abgleich startet etwa 2 bzw. 10 Sekunden nach jeder Änderung.
- **Neue Tabelle mit Nutzerdaten → in `SyncSchema.tables` eintragen** (mit naturalKey/refs), sonst wird sie nicht abgeglichen.
- Netzwerk nur auf `Dispatchers.IO` (bereits einmal `NetworkOnMainThreadException`, behoben in v23).

## Bauen & Testen (Cloud-Container)
1. Android SDK (Netzwerk-Freigabe für `dl.google.com` nötig):
   ```bash
   mkdir -p $SDK/cmdline-tools && cd $SDK/cmdline-tools
   curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && unzip -q clt.zip && mv cmdline-tools latest
   yes | $SDK/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
   $SDK/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
   echo "sdk.dir=$SDK" > local.properties   # local.properties ist in .gitignore
   ```
2. Signatur (Schlüssel **nicht** in Git, Daniel hat die Dateien):
   - `lifedashboard-release.jks` (Alias `lifedashboard`, Store- und Key-Passwort sind gleich), Passwort in `1_SIGNING_STORE_PASSWORD.txt`
   - alternativ `2_SIGNING_KEYSTORE_BASE64.txt` → `base64 -d > lifedashboard-release.jks`
   - SHA-256 des Zertifikats: `b32c0d0261e207b39411391dd7a6a4f3df17c81f81239220d47c4b445f836134`. **Muss gleich bleiben**, sonst lässt sich das Update nicht installieren.
3. Bauen (nächste Version = 27):
   ```bash
   export SIGNING_KEYSTORE_PATH=/pfad/lifedashboard-release.jks SIGNING_STORE_PASSWORD='…' GITHUB_RUN_NUMBER=27
   ./gradlew --max-workers=2 :planner:test :app:testDebugUnitTest :app:assembleRelease
   # → app/build/outputs/apk/release/app-release.apk  (als LifeDashboard-27.apk schicken)
   ```
   Prüfen: `apksigner verify --print-certs …apk` muss den SHA-256 oben zeigen.
- Maven Central antwortet manchmal mit **429** → mit Pause wiederholen.
- Tests: Planer (`:planner:test`), Migrationen (`MigrationTest` vergleicht mit dem neuesten Schema-JSON), Robolectric-Tests mit echter App: `PlanningIntegrationTest`, `BackgroundJobsTest`, `DeviceSyncTest`, `ScreenshotTest` (Screenshots Handy + Tablet unter `app/build/screenshots/`). Das Android-Abbild für Robolectric lädt Gradle (`robolectricRuntime`), Robolectric selbst läuft offline.

## Offene Punkte / Ideen
- Abgleich Handy ↔ Tablet auf den echten Geräten bestätigen lassen (v23 behebt den Fehler beim Knopf „Jetzt abgleichen“).
- Etsy-API anbinden, sobald der Shop live ist.
- Später: Upgrade auf AGP 9 / API 37.
- `.github/workflows/android.yml` baut bei jedem Push. Die Releases brauchen die Secrets `SIGNING_STORE_PASSWORD` und `SIGNING_KEYSTORE_BASE64`. Ob der Workflow nach der früheren GitHub-Sperre wieder läuft, ist nicht geprüft.
