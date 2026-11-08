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
| 2 | Kalender, Aufgaben, Hausaufgabenplaner, Job-Schichten | ⏳ |
| 3 | Lernen & Klausuren, Lernplan | ⏳ |
| 4 | Heute + Tagesplanung | ⏳ |
| 5–11 | Sport, Finanzen, Etsy, Schnelleingabe, Benachrichtigungen, Widgets, Backup | ⏳ |

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

## 3. Was du in Phase 1 testen kannst
- **App-Sperre:** Beim Öffnen fragt die App nach Fingerabdruck oder PIN. Nach mehr als 1 Minute im Hintergrund sperrt sie sich wieder. Abschalten unter *Mehr → App-Sperre*.
- **Heute:** Zeigt deinen Tagesablauf mit Unterricht, Schul- und Heimweg, Floorball und **freien Zeitfenstern** sowie „Jetzt / Als Nächstes“.
- **Kalender → Stundenplan:** Umschalten zwischen Woche A und B. Ein Tipp auf einen Block öffnet den Dialog zum Ändern (Fach, Raum, „gilt für A/B/jede Woche“).
- **Mehr:** A/B-Woche korrigieren, Schulweg-Minuten einstellen.
- **Tablet:** Navigation an der Seite, im Stundenplan mehrere Tage nebeneinander.
- **Dark Mode & Dynamic Color:** Die Farben folgen deinem Hintergrundbild und dem Dark-Mode-Schalter.

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

**Tests lokal ausführen:** `./gradlew test`
Die Tests laufen auch bei jedem Push auf GitHub.

> Die Versionen sind bewusst bewährte Stände (AGP 8.13 / API 36). Die App läuft damit auf Android 17 (S25) und Android 16 (Tab S8+). Ein Upgrade auf AGP 9 / API 37 folgt in einer späteren Phase.
