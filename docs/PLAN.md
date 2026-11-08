# Life Dashboard – Projektplan (Entwurf v1, 08.11.2026)

> Status: **wartet auf OK**. Erst nach Freigabe wird Code geschrieben.

---

## 1. Rahmen & Entscheidungen

| Thema | Entscheidung |
|---|---|
| Geräte | Galaxy S25 (One UI 9 = **Android 17**, API 37) und Galaxy Tab S8+ (max. One UI 8 = **Android 16**, API 36) |
| SDK | `minSdk 34` (Android 14), `targetSdk/compileSdk` = neueste stabile Version, die die Build-Tools unterstützen (Ziel: 37) |
| Layout | Adaptiv: Handy = Bottom-Bar, Tablet = Navigation-Rail + zweispaltige Ansichten |
| Sprache / Währung | Deutsch, EUR |
| Build & Installation | **GitHub Actions** baut bei jedem Push eine APK → Download aufs Handy/Tablet. Android Studio optional zum Debuggen |
| Bank | **DKB, manuelle Eingabe**. Optional: DKB-CSV-Export importieren (Datei bleibt auf dem Handy, niemand sonst sieht sie) |
| Etsy | Shop entsteht gerade → zuerst **manuelles Etsy-Modul** (Motiv-Pipeline, To-dos, Verkäufe eintragen). Etsy-API (OAuth/PKCE, nur lesen), sobald der Shop live ist |
| Kalender | Eigener Kalender in der App (lesen **und** schreiben) + Android Calendar Provider (Samsung-/Google-Kalender lesen, optional in einen gewählten Kalender schreiben) |
| KI-Assistent | Claude API mit Tool-Use (siehe Abschnitt 7). Opt-in, jede Aktion wird vorher zur Bestätigung angezeigt |
| Benachrichtigungen | Ruhezeit 22:00–6:00, max. 5 pro Tag, Morgen-Zusammenfassung 6:15 |
| Backup | Wöchentlich automatisch, mit Passwort verschlüsselt, in einen Ordner deiner Wahl |
| Streaks | 1 Joker-Tag pro Woche – ein verpasster Tag zerstört nichts |
| Minijob | Eigener Bereich „Job“: Schichten als feste Blöcke (der Planer weicht aus), Stunden + Verdienst landen in Finanzen, Warnung vor der Minijob-Grenze |

---

## 2. Architektur (kurz erklärt)

```
┌──────────── UI (Jetpack Compose, Material 3, One-UI-Stil) ────────────┐
│  Screens  ←→  ViewModels (StateFlow<UiState>)                          │
└───────────────────────────────┬────────────────────────────────────────┘
                                │ ruft auf
┌──────────── Domain ───────────▼────────────────────────────────────────┐
│  UseCases  •  Planner-Engine (reines Kotlin, voll unit-getestet)       │
│  Lernplan-Verteiler • Spaced Repetition • Kategorisierer • Prognosen   │
└───────────────────────────────┬────────────────────────────────────────┘
┌──────────── Data ─────────────▼────────────────────────────────────────┐
│  Repositories → Room (SQLCipher-verschlüsselt)  = einzige Wahrheit     │
│               → Calendar Provider • Health Connect • Etsy API • Claude │
│  WorkManager: Sync, Neuplanung, Backup, Benachrichtigungen             │
└────────────────────────────────────────────────────────────────────────┘
```

- **MVVM + Repository + Hilt** (Dependency Injection).
- **Module:** `:app` (UI + Data, nach Features gegliedert) und `:planner` (reine Kotlin-Logik ohne Android → schnelle Unit-Tests).

### „Alles hängt zusammen“ – so funktioniert die Verknüpfung
1. **Eine Datenbank als einzige Wahrheit.** Jeder Screen beobachtet die Daten live (Kotlin `Flow`). Ändert sich etwas, aktualisieren sich alle Stellen automatisch – Heute, Kalender, Lernen, Widget.
2. **Verknüpfungen über IDs.** Ein Plan-Block weiß, woher er kommt (`sourceType` + `sourceId`), z. B. „Lerneinheit Thema X der Mathe-Klausur“ oder „Etsy-To-do Mockups“.
3. **Neuplanung bei Änderungen.** Ein `ChangeObserver` merkt sich relevante Änderungen und stößt (gebündelt, ~2 s Verzögerung) die Neuplanung an.

Beispiele:
- Klausur verschoben → Lernplan wird neu verteilt → Tagesplan, Kalender und Widget ändern sich.
- Floorball fällt aus → freier Slot → Planer schlägt dort Lernen/Etsy vor.
- Termin über den Assistenten eingetragen → erscheint im Kalender, der Tagesplan weicht aus.
- Etsy-To-do erledigt → Etsy-Fortschritt und zugehöriges Ziel steigen.
- Ausgabe eingetragen → Budget-Karte, Monatsprognose und ggf. 80-%-Hinweis.

---

## 3. Datenmodell (Room)

| Bereich | Tabellen |
|---|---|
| Schule | `Subject` (Kürzel, Name, Farbe, LK/GK, Schwierigkeit 1–5), `TimetableLesson` (Wochentag, Block, Woche A/B, Raum), `TimetableException` (Ausfall/Vertretung), `SchoolHoliday` |
| Klausuren & Lernen | `Exam` (Fach, Datum, Art), `Topic` (Klausur, Name, Aufwand, Fortschritt %, nächste Wiederholung), `StudySession` (Pomodoro-Log), `Grade` (Punkte 0–15, Gewicht), `Assignment` (Abgabe) |
| Job | `JobShift` (Datum, Start, Ende, Stundenlohn), `JobSettings` (Minijob-Grenze) |
| Kalender & Aufgaben | `Event` (eigene Termine, optional mit Systemkalender verknüpft), `ExternalCalendarLink`, `Task` (Bereich, Fälligkeit, Dauer-Schätzung, Verknüpfung) |
| Planung | `PlanBlock` (Datum, Start, Ende, Typ, Quelle, Status: vorgeschlagen/angenommen/verschoben/abgelehnt/erledigt/verpasst), `PlanFeedback` (Entscheidung + Kontext für das Lernen), `PlannerSettings` (Prioritäten, Tagesfenster, Blocklängen) |
| Sport | `TrainingTemplate` (z. B. Floorball Mo/Mi), `Workout`, `WorkoutExercise`, `ExerciseSet`, `HealthDaily` (Schritte, Schlaf aus Health Connect) |
| Ziele | `Goal` (Bereich, Zielwert), `Milestone`, `Habit`, `HabitLog` (inkl. Joker) |
| Finanzen | `Account`, `Transaction`, `Category`, `CategoryRule` (gelernt aus Korrekturen), `Budget`, `RecurringPayment`, `SavingsGoal` |
| Etsy | `DesignSet` (z. B. Winter-Set), `Design` (Motiv, Status, Canva-Link, Nachweis), `Listing`, `EtsyOrder`, `EtsyDailyStats`, `EtsyFeeSettings` |
| System | `NotificationLog`, `AssistantAction` (Protokoll, was der Assistent geändert hat → rückgängig machbar) |

Zugangsdaten (Etsy-Token, Claude-API-Key) **nicht** in der Datenbank, sondern verschlüsselt per Android Keystore.

---

## 4. Planungs-Logik (Herzstück, Modul `:planner`)

**Tagesplan (jeden Morgen + bei Änderungen):**
1. Feste Blöcke sammeln: Stundenplan (A/B-Woche, Ausfälle), Kalendertermine, Training, Schlafenszeit (22:00), Puffer für Wege.
2. Freie Zeitfenster berechnen (z. B. Do ab 9:30 = großes Fenster).
3. Bedarfe sammeln: fällige Lerneinheiten, Wiederholungen, Aufgaben, Etsy-Arbeit, Habits.
4. Jeder Bedarf bekommt einen **Score**: Dringlichkeit (Tage bis Klausur/Deadline) × Bereichs-Priorität (Klausurphase > Sport > Etsy, änderbar) × Fach-Schwierigkeit × gelernte Vorliebe (Tageszeit/Wochentag).
5. Platzieren (greedy, beste Slots zuerst), mit Regeln: max. 90 min am Stück, Pausen, nicht direkt nach dem Training lernen usw.

**Wochenplan – Lernplan rückwärts von der Klausur:**
- Benötigte Stunden pro Klausur = Σ Themen-Aufwand × Schwierigkeitsfaktor × (1 − Fortschritt).
- Verteilung rückwärts vom Klausurtag mit Steigerung zum Ende hin; der letzte Tag vor der Klausur ist nur Wiederholung.
- **Spaced Repetition** pro Thema: Wiederholung nach 1 → 3 → 7 → 14 Tagen. Das Intervall wächst oder schrumpft je nachdem, wie gut es lief („leicht/mittel/schwer“ nach der Einheit).

**Kein Bestrafen:** Ein verpasster Block wird als „verpasst“ markiert und automatisch in den nächsten passenden Slot verschoben. Wird es eng, kürzt der Planer weniger wichtige Blöcke, statt zu stapeln.

**Lernen aus Entscheidungen:** Annehmen, Verschieben und Ablehnen werden pro (Typ, Wochentag, Tageszeit) gezählt. Daraus entstehen Gewichte für die Slot-Wahl. Dazu kommt ein Korrekturfaktor für Dauer-Schätzungen (wenn Einheiten regelmäßig länger dauern). Alles läuft lokal und ist nachvollziehbar („Warum hier?“-Info am Block).

**Wochenrückblick (Sonntag):** Geplant vs. erledigt pro Bereich, Lernstunden pro Fach, Trainings, Habits, Ausgaben, Etsy. Danach 2–3 konkrete Vorschläge für die neue Woche, die du übernehmen kannst.

---

## 5. Screens & Navigation

Unten (Einhand): **Heute · Kalender · Lernen · Sport · Mehr** (Finanzen, Etsy, Ziele, Einstellungen). Dazu eine **Assistent-Eingabe** unten („Sag mir, was ich eintragen soll…“).

| Screen | Inhalt |
|---|---|
| Heute | Großer Titel, Zeitstrahl mit Plan-Blöcken (annehmen/verschieben/ablehnen per Wischen), Karten: nächste Klausur + Countdown, Training, Lernfortschritt, Budget, Etsy, Habits – sortier- und ausblendbar |
| Kalender | Tag/Woche/Monat, Stundenplan + Termine + Plan-Blöcke, Eintragen per Langdruck |
| Lernen | Fächer, Klausuren, Themenlisten mit Fortschritt, Lernplan, Fokus-Timer, Noten (Punkte, Durchschnitt), Abgaben |
| Sport | Wochenplan (Floorball Mo/Mi + Zusatz), Workout-Log, Diagramme, Schritte/Schlaf |
| Ziele | Ziele pro Bereich (z. B. Ironman-Weg in Stufen), Habits, Streaks mit Joker |
| Finanzen | Konten, Umsätze, Budgets, Abos, Sparziele, Monatsprognose |
| Etsy | Motiv-Pipeline (Idee → erstellt → geprüft → Probedruck → Mockup → Listing → online), To-dos, Verkäufe, Gebühren & Netto |
| Einstellungen | Prioritäten, Tagesfenster, A/B-Woche, Benachrichtigungen, App-Sperre, Backup, Verbindungen |

Design: große, einklappbare Überschrift oben (One-UI-Stil), runde Karten (24 dp), Dynamic Color, Dark Mode.

---

## 6. Startdaten (echte Daten statt nur Beispiele)

**Stundenplan** (Blöcke: 08:00–09:30 · 09:50–11:20 · 11:40–13:10 · 13:30–15:00). Ob KW 46 die A- oder B-Woche ist, ist noch offen.

| | Woche wie KW 46 | Woche wie KW 47 |
|---|---|---|
| Mo | DE-B F309 · BIO F305 · GeWi-B E207 · DS G025 | MA-B F309 · BIO F305 · GeWi-B E207 · DS G025 |
| Di | TuMM-B G112 · TuMM-B G112 · TuMT-B G113 · TuMT-B G113 | gleich |
| Mi | BIO-Pro F305 · SP GYM · DE-B F309 · CH F305 | gleich |
| Do | E-B F309 · frei ab 9:30 | gleich |
| Fr | MA-B F309 · PM F210 · E-B F309 · GeWi-B E207 | MA-B F309 · PM F210 · TuMT-B G113 · frei |

- Minijob in Aussicht (vermutlich im freien Donnerstag-Fenster): Sobald es Schichten gibt, werden sie als feste Termine eingetragen und Lernzeit wird automatisch woandershin verteilt.
- Tagesfenster: Aufstehen 6:00, Schlafen 22:00. Floorball Mo + Mi (Berliner TSC), Uhrzeiten offen.
- Ziel „Ironman“ als Stufenweg: 5 km laufen → Sprint-Triathlon (0,75/20/5) → Olympisch (1,5/40/10) → Mitteldistanz (1,9/90/21,1) → Ironman (3,8/180/42,2). Kleine Wochen-Habits mit „Minimalversion“ (10 Minuten zählen auch).
- Etsy: Winter-Set (Rotkehlchen, Hütte, Fuchs) als Set mit drei Motiven. Offene To-dos: Probedruck Oma, Google-Lens-Check, Mockups + Titelbild, 15-oz-Datei, Entscheidung „Tagesaufgabe pausieren?“. Dazu eine Upload-Checkliste mit KI-Häkchen, „Designed by“ und Nachweis.
- Berliner Schulferien 2026/27 hinterlegt.

---

## 7. KI-Assistent

- Eingabe per Text oder Sprache, z. B. „Trag Mittwoch 17 Uhr Zahnarzt ein“, „Mathe-Klausur am 2.12., Themen: Ableitungen, Integrale“, „Verschieb mein Lernen heute auf morgen“.
- Technik: **Claude API mit Tool-Use**. Die App stellt Werkzeuge bereit (`termin_anlegen`, `aufgabe_anlegen`, `klausur_anlegen`, `plan_verschieben`, `ausgabe_eintragen` …). Claude wählt aus, die App zeigt eine **Bestätigungskarte**, erst dann wird gespeichert. Jede Aktion ist rückgängig machbar.
- Datenschutz: Das ist die einzige Funktion, bei der Daten das Handy verlassen. Gesendet werden nur dein Satz und der nötige Kontext (z. B. Termine der betroffenen Tage), keine Bankdaten. Der Assistent ist abschaltbar. Ohne Internet gibt es einen einfachen Offline-Parser für Standardsätze („morgen 15 Uhr …“).
- Kosten: eigener API-Key (console.anthropic.com, Prepaid-Guthaben), sicher im Keystore gespeichert. Modell wählbar:
  - **Claude Opus 5.5** (Standard, am klügsten): ca. 1–2 Cent pro Anfrage → bei ~10 Anfragen am Tag ca. 3–6 € pro Monat.
  - **Claude Haiku 5.5** (sehr günstig): Bruchteile eines Cents pro Anfrage, für einfache Einträge meist ausreichend.

---

## 8. Phasen

Weil die Klausurphase in ~2 Wochen beginnt, kommt **Lernen + Planung früher** als ursprünglich vorgeschlagen.

| Phase | Inhalt | Ergebnis zum Testen |
|---|---|---|
| 1 | Grundgerüst: Projekt, GitHub-Actions-APK, Theme, Navigation (Handy + Tablet), Room + SQLCipher, Hilt, App-Sperre (Fingerabdruck), Stundenplan A/B | App startet, zeigt Stundenplan, Fingerabdruck-Sperre |
| 2 | Kalender & Aufgaben (+ Job-Schichten): eigener Kalender, Systemkalender lesen/schreiben, To-dos mit Bereichen | Termine und Aufgaben eintragen, im Samsung-Kalender sichtbar |
| 3 | Lernen & Klausuren: Fächer, Klausuren, Themen, Lernplan rückwärts + Wiederholungen, Fokus-Timer, Noten (Punkte) | Klausuren eintragen → automatischer Lernplan |
| 4 | Heute + Tagesplan-Engine: Zeitstrahl, Vorschläge annehmen/verschieben/ablehnen, Neuplanung, Morgen-Benachrichtigung | Täglicher Plan für die Klausurphase |
| 5 | Sport + Ziele & Habits: Floorball, Workout-Log, Diagramme, Health Connect, Ironman-Stufen, Streaks | Training loggen, Schritte/Schlaf sehen |
| 6 | Finanzen: Konten, manuelle Umsätze, DKB-CSV-Import, Kategorien lernen, Budgets, Abos, Prognose | Ausgaben vs. Budget |
| 7 | Etsy: Motiv-Pipeline, To-dos, Verkäufe, Gebühren/Netto, Trends; danach Etsy-API mit Registrierungs-Anleitung | Etsy-Karte auf „Heute“ |
| 8 | KI-Assistent | „Trag … ein“ funktioniert |
| 9 | Benachrichtigungen komplett, Wochenrückblick, Planer lernt aus Feedback | Sonntags-Rückblick |
| 10 | Widgets (Glance): Heute-Plan, nächste Klausur, Fokus-Timer | Widgets auf dem Homescreen |
| 11 | Verschlüsseltes Backup/Export + Sync Handy ↔ Tablet | Gleiche Daten auf beiden Geräten |

Jede Phase endet mit: App kompiliert, Unit-Tests grün, APK per GitHub Actions, Test-Anleitung.

---

## 9. Sicherheit & Qualität
- Room-Datenbank mit **SQLCipher** verschlüsselt; der Schlüssel liegt im Android Keystore.
- **BiometricPrompt** beim Öffnen und nach X Minuten im Hintergrund.
- Kein Tracking, keine Analytics, kein eigener Server. Netzwerk nur für Etsy, Claude und (optional) Sync.
- Secrets nie in Git (`.gitignore`, GitHub Secrets für den Signatur-Schlüssel).
- Zustände für Laden, Leer und Fehler (offline, Token abgelaufen, API-Fehler) in jedem Screen.
- Unit-Tests: Planer, Lernplan-Verteilung, Spaced Repetition, Kategorisierung, Budget/Prognose, Etsy-Netto, Punkte-Durchschnitt.
- README mit kompletter Setup-Anleitung.
