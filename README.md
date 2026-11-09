# Life Dashboard

Mein persönliches Life-OS für Android (Galaxy S25 & Galaxy Tab S8+).
Zeigt alle Lebensbereiche auf einen Blick und **plant Tag und Woche aktiv**: Schule & Klausuren, Sport, Finanzen, Etsy, Termine und Ziele.

- Nur privat, nicht im Play Store
- Alle Daten bleiben **verschlüsselt auf dem Gerät**: kein Server, kein Tracking
- Plan & Architektur: [`docs/PLAN.md`](docs/PLAN.md)

## Stand

| Phase | Inhalt | Status |
|---|---|---|
| 1 | Grundgerüst, Stundenplan A/B, App-Sperre, automatischer APK-Build | ✅ |
| 2 | Buntes Design, Kalender (Woche), Termine, Hausaufgaben, Aufgaben, Job-Schichten, Handy-Kalender, Lernraum/IServ-Abos (iCal) | ✅ |
| 3 | Lernen: Klausuren, Themen, Lernplan rückwärts, Wiederholungen, Notenpunkte & Ziel 1,7, Fokus-Timer 25/5 | ✅ |
| 4 | Intelligente Tagesplanung: Vorschläge annehmen/verschieben/ablehnen, Neuplanung, lernt aus Entscheidungen | ✅ |
| 5 | Fitness (Health Connect: Samsung Health, MyFitnessPal & Co.), Sport-Zeitfenster, Ziele, Gewohnheiten mit Streaks & Joker | ✅ |
| 6 | Finanzen: DKB + Bargeld, CSV-Import, Kategorien lernen, Budgets, Puffer, Abos, Sparziele, Prognose | ✅ |
| 7 | Etsy: Umsatz/Netto, Motiv-Pipeline, Verkäufe, Statistik, Checkliste (API, sobald der Shop live ist) | ✅ / API ⏳ |
| 8 | Offline-Schnelleingabe („Mi 17 Uhr Zahnarzt“, „HA Mathe S. 45“ …) | ✅ |
| 9 | Benachrichtigungen (Ruhezeit, max. 5/Tag, Coach-Ton) + Wochenrückblick | ✅ |
| 10 | Homescreen-Widgets: Heute & Klausur-Countdown | ✅ |
| 11 | Verschlüsseltes Backup, Auto-Backup, Übertragung Handy ↔ Tablet | ✅ |
| Update 21 | Automatischer Abgleich Handy ↔ Tablet (Cloud-Ordner + WLAN, verschlüsselt, zusammenführen statt überschreiben) | ✅ |
| Update 2 | Kalender ganzjährig (A/B mit Ferien), Automatik überall, Sport-Zeitfenster, Fitness-Bereich, freie Klausur-Themen, Gesamtprüfung mit Screenshot-Tests | ✅ |

---

## 1. Einmalig einrichten (ca. 10 Minuten)

### 1.1 Signatur-Schlüssel als GitHub-Secrets hinterlegen
Jede App-Version muss mit **demselben Schlüssel** signiert sein. Sonst lässt sich ein Update nicht über die alte Version installieren, und du müsstest deinstallieren und deine Daten wären weg.
Der Schlüssel liegt **nicht** im Code, sondern als Secret bei GitHub:

1. Auf github.com dein Repo öffnen → **Settings** → **Secrets and variables** → **Actions**.
2. **New repository secret** antippen und anlegen:
   - `SIGNING_STORE_PASSWORD`: Passwort aus der Datei `signing-secrets.txt`
   - `SIGNING_KEYSTORE_BASE64`: die lange Zeile aus derselben Datei
3. Die Datei `signing-secrets.txt` sicher aufheben (z. B. im Passwort-Manager).

> Einen eigenen Schlüssel erzeugen (am PC):
> `keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias lifedashboard -keyalg RSA -keysize 4096 -validity 36500`
> danach `base64 -w0 release.jks` → Ergebnis als `SIGNING_KEYSTORE_BASE64` speichern. Wichtig: Der Alias muss `lifedashboard` heißen, und Schlüssel- und Store-Passwort müssen gleich sein.

### 1.2 Handy/Tablet vorbereiten (One UI)
1. **Auto Blocker** (Automatische Sperre) vorübergehend ausschalten:
   *Einstellungen → Sicherheit und Datenschutz → Auto Blocker → aus*.
   Er blockiert sonst die Installation von Apps außerhalb des Stores. Nach der Installation kannst du ihn wieder einschalten. Für jedes Update musst du ihn erneut kurz ausschalten.
2. Beim ersten Öffnen einer APK fragt Android, ob dein Browser (z. B. Chrome oder Samsung Internet) **Apps installieren** darf → erlauben.

---

## 2. App installieren / aktualisieren

1. Jeder Push auf GitHub baut automatisch eine neue APK. Das dauert ca. 5–8 Minuten.
   Den Fortschritt siehst du im Repo unter **Actions**.
2. Auf dem Handy im Browser öffnen: **github.com/Daniel-nufc/Life-Dashbord-1/releases**
3. Beim obersten „Life Dashboard Build …“ die Datei `LifeDashboard-XX.apk` antippen → herunterladen → **Öffnen** → **Installieren**.
4. Bei einem Update genauso vorgehen. Deine Daten bleiben erhalten.

> Ohne die Secrets aus 1.1 gibt es keinen Release. Die APK liegt dann nur als ZIP unter
> *Actions → Build → Artifacts* und lässt sich nicht als Update installieren.

### Alternative: Android Studio + USB-Debugging (optional, zum Entwickeln)
1. Android Studio installieren und dieses Repo öffnen (*File → Open*).
2. Auf dem Handy: *Einstellungen → Telefoninfo → Softwareinformationen → 7× auf „Buildnummer“ tippen* → Entwickleroptionen sind aktiv.
3. *Einstellungen → Entwickleroptionen → USB-Debugging* einschalten, Handy per USB verbinden und den Zugriff erlauben.
4. In Android Studio oben das Gerät wählen und auf ▶ **Run** klicken. Dabei wird die Debug-Variante installiert (`app.lifedashboard.debug`). Sie läuft neben der normalen App und hat eigene Daten.

---

## 3. Kurzer Rundgang

- **Heute:** Begrüßung, Coach-Satz und Freizeit-Ring. Darunter die Schnelleingabe („Mi 17 Uhr Zahnarzt“). Der **Tagesplan** zeigt Schule, Training und Termine, die App schlägt Blöcke dafür vor. Mit ✓ Passt, ⏭ Später oder ✕ entscheidest du, und die App lernt daraus. Dazu kommen Kacheln für Aufgaben, Klausur, Gewohnheiten, Geld und Etsy. Die Karten lassen sich unter *Mehr → Einstellungen* sortieren und ausblenden.
- **Kalender:** Wochenansicht mit allem Wichtigen und der Stundenplan (A/B), über **+** trägst du Neues ein.
- **Aufgaben:** Hausaufgaben (automatisch fällig zur nächsten Stunde des Fachs) und To-dos mit Filtern.
- **Lernen:** Klausuren mit Themen und Fortschritt, automatischer Lernplan, Wiederholungen, Notenpunkte mit Ziel 1,7, Fokus-Timer.
- **Mehr:**
  - Fitness (Schritte, Schlaf, Puls, Kalorien, Ernährung, Gewicht, Wasser, Sport-Zeitfenster, Trainings-Log, Floorball-Zeiten)
  - Ziele & Gewohnheiten
  - Finanzen
  - Etsy
  - Wochenrückblick
  - Kalender & Abos (Handy-Kalender, Lernraum-Link)
  - Backup & Übertragen
  - Einstellungen (A/B-Woche, Schulweg, Wochenende, Prioritäten, App-Sperre)
- **Widgets:** Homescreen lange drücken → Widgets → „Life Dashboard“.

### Erste Schritte nach der Installation
1. *Mehr → Einstellungen:* Schulweg-Minuten prüfen und den Namen anpassen.
2. *Lernen → Klausuren:* Klausuren mit Themen eintragen, dann entsteht der Lernplan.
3. *Lernen → Noten:* Pro Fach einstellen, wie schwer es dir fällt (Antippen).
4. *Mehr → Finanzen:* den Startstand von DKB und Bargeld eintragen und den **Puffer** festlegen.
5. *Mehr → Kalender & Abos:* Handy-Kalender erlauben, optional den Lernraum-Link eintragen.
6. *Mehr → Fitness:* Health Connect verbinden. Danach in **Samsung Health** (Einstellungen → Health Connect) und **MyFitnessPal** (Apps & Geräte → Health Connect) das Teilen erlauben.

### Handy ↔ Tablet abgleichen
*Mehr → Handy ↔ Tablet*
1. Auf **beiden** Geräten dasselbe **Abgleich-Passwort** eintragen (mind. 8 Zeichen).
2. **Cloud-Ordner (auch unterwegs):** Auf dem ersten Gerät „Neue Abgleich-Datei anlegen“ und in Google Drive speichern. Auf dem zweiten Gerät „Vorhandene Abgleich-Datei wählen“ und dieselbe Datei auswählen.
   Abgleich danach automatisch beim Öffnen, beim Verlassen und alle 30 Min.
3. **WLAN (ohne Cloud):** Ist standardmäßig an. Sind beide Geräte im selben WLAN und die App ist offen, gleichen sie sich direkt ab.

Einträge werden **zusammengeführt**, nicht überschrieben. Ändern beide Geräte denselben Eintrag, gewinnt die neuere Änderung. Löschen wird übertragen.
Einstellungen bleiben pro Gerät. Alles ist mit dem Abgleich-Passwort verschlüsselt (AES-256), Google sieht nur unlesbare Daten.

### Sport-Zeitfenster
Die App schreibt dir **keinen Sport vor**. Sie hält pro Woche freie Zeitfenster frei (Standard: 3 × 60 Min zusätzlich zu Floorball, nie an Floorball-Tagen, sonntags frei, in der Klausurphase etwas weniger).
Was du darin machst, entscheidest du. Beim Abhaken kannst du optional eintragen, was es war. Trainings aus Samsung Health, Strava & Co. haken das Zeitfenster automatisch ab.
Anzahl und Länge änderst du unter *Fitness → ⚙*.

### Klausuren & Tests
Themen trägst du **selbst frei** ein. Typische Themen des Fachs erscheinen nur als Vorschläge zum Antippen. Ohne Themen plant die App pauschal 4 Std Lernzeit. Themen antippen → umbenennen, Aufwand ändern, löschen.

---

## 4. Technik

| | |
|---|---|
| Sprache/UI | Kotlin, Jetpack Compose, Material 3 (Dynamic Color, Dark Mode), One-UI-Stil |
| Architektur | MVVM + Repository, Hilt; Planungs-Logik als reines Kotlin-Modul `:planner` |
| Daten | Room + SQLCipher (verschlüsselt, Schlüssel im Android Keystore), DataStore |
| Sicherheit | BiometricPrompt, kein Cloud-Backup (`allowBackup=false`), Secrets nur als GitHub-Secrets |
| Build | Gradle 8.14, AGP 8.13, Kotlin 2.2, minSdk 34 (Android 14), targetSdk 36 |

```
app/        Android-App (UI, Datenbank, Repositories)
planner/    Planungs-Logik ohne Android (freie Zeitfenster, A/B-Woche, später Lernplan)
docs/       Plan & Doku
```

**Tests lokal ausführen:** `./gradlew :planner:test :app:testDebugUnitTest`

Getestet werden: Planer (freie Zeiten, A/B-Woche über ein ganzes Schuljahr inkl. Ferien, Jahreswechsel und Zeitumstellung, Tagesplanung, Sport-Zeitfenster), Lernplan & Spaced Repetition, Noten, Streaks, iCal-Leser, DKB-CSV, Kategorisierung, Abo-Erkennung, Monatsprognose, Etsy-Gebühren, Schnelleingabe und **alle Datenbank-Migrationen** (damit Updates nie Daten verlieren).

Dazu kommen Tests mit der echten App (Robolectric, ohne Gerät):
- **Planung** mit echter Datenbank: leere Datenbank, Klausur morgen, Klausur ohne Themen, gelöschte Klausur, Ferien, voller Tag
- **Abgleich Handy ↔ Tablet:** Startdaten nicht doppelt, neue/geänderte/gelöschte Einträge, Verweise (Klausur → Themen), Konflikte, falsches Passwort, echter WLAN-Austausch
- **Hintergrund-Jobs:** Erinnerungen, Kalender-Abo und Backup laufen ohne Absturz und sind genau einmal geplant
- **Screenshot-Tests:** alle Hauptbildschirme auf Handy- und Tablet-Größe, die Bilder landen unter `app/build/screenshots/`
Die Tests laufen auch bei jedem Push auf GitHub.

> Die Versionen sind bewusst bewährte Stände (AGP 8.13 / API 36). Die App läuft damit auf Android 17 (S25) und Android 16 (Tab S8+). Ein Upgrade auf AGP 9 / API 37 folgt in einer späteren Phase.
