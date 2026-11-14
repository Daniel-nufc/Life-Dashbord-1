# Ideen & Konzepte (ab Version 36)

Leitfrage für jede Idee: **Hilft es Daniel, anzufangen, den Alltag machbar zu strukturieren oder seine Ziele
(Abi 1,7 · Ironman/10 km · Führerschein sparen) zu erreichen – ohne ihn zu gängeln?**
Alles bleibt lokal, nichts kostet Geld, Pläne sind Vorschläge.

Status: ✅ umgesetzt · 🔜 geplant · 💭 später

## A. Anfangen & Dranbleiben
| # | Feature | Warum | Status |
|---|---|---|---|
| 1 | **„Nur 5 Min“-Start** in der Kopfkarte: Fokus-Timer läuft erst 5 Min, danach „Weitermachen“ oder „Für jetzt reicht's“ | Anfangen fällt am schwersten | ✅ v36 |
| 2 | **Nach dem Fokus: „Wie gut sitzt es jetzt?“** – Verständnis-Regler fürs Thema, senkt die Restlernzeit | Fortschritt = Verständnis | ✅ v36 |
| 3 | **Abend-Check** (Energie 1–5): müde → morgen 30 Min weniger, fit → bis 30 Min mehr | Plan passt sich dem Menschen an | ✅ v36 |
| 4 | **Schlaf zählt**: unter 6 Std Schlaf (Health Connect) → heute 30 Min leichter | Kein Plan gegen den Körper | ✅ v36 |
| 5 | **Zockzeit-Timer**: beim Einlösen läuft ein Timer, Hinweis 5 Min vor Ende und am Ende | Belohnung ohne Endlos-Zocken | ✅ v36 |

## B. Schule
| # | Feature | Warum | Status |
|---|---|---|---|
| 6 | **Noten-Rechner**: Schnitt je Fach, Gesamtpunkte, Prognose Abi-Note (gab es schon, jetzt mit 7 + 8) | Ziel 1,7 sichtbar machen | ✅ |
| 7 | **„Was brauche ich?“** je Fach: Punkte in der nächsten Klausur, um das Ziel zu halten | Klares Ziel pro Klausur | ✅ v36 |
| 8 | **Größter Hebel**: welches Fach den Schnitt am meisten hebt | Lernzeit gezielt einsetzen | ✅ v36 |
| 9 | **Klausur vorbei → Note eintragen** (Karte unter Lernen) | Noten-Rechner bleibt aktuell | ✅ v36 |
| 10 | **Hausaufgaben nach der Schule erfassen**: Fächer von heute als Chips, ein Tipp → fällig zur nächsten Stunde | Nichts vergessen, null Tipparbeit | ✅ v36 |
| 11 | **Stunde fällt aus** markieren → Zeit wird frei und neu verplant | Vertretung/Ausfall ist Alltag | ✅ v36 |
| 12 | **„Lernwoche startet“** – Hinweis 7 Tage vor jeder Klausur (Hausaufgaben-Dauer je Fach lernt die App schon seit v28) | Rechtzeitig loslegen | ✅ v36 |
| 13 | **Ferien 2027/28** (vorläufig) + Erinnerung, wenn die Ferien auslaufen | A/B-Woche & Planung stimmen weiter | ✅ v36 |
| 14 | **Ferien-Countdown** in der Kopfkarte („noch 12 Schultage bis …“) | Motivation | ✅ v36 |

## C. Sport (keine Sportart vorschreiben!)
| # | Feature | Warum | Status |
|---|---|---|---|
| 15 | **Weg zu 10 km**: längster Lauf der letzten 4 Wochen, Wochen-km, Prognose nach 10-%-Regel | Erstes Ironman-Ziel | ✅ v36 |
| 16 | **Bestleistungen**: längster Lauf, schnellste km-Zeit (≥ 3 km), längste Radfahrt, längstes Training | Fortschritt fühlbar | ✅ v36 |
| 17 | **Losgehen-Erinnerung** vor eigenen Terminen mit Ort (z. B. Spieltag, Turnier) | Pünktlich sein | ✅ v36 |
| 18 | **Tasche packen** am Vorabend eines eigenen Spieltags/Turniers | Nichts vergessen | ✅ v36 |

## D. Überblick & Erkenntnisse
| # | Feature | Warum | Status |
|---|---|---|---|
| 19 | **Lern-Heatmap**: geschaffte Minuten je Tag der letzten 8 Wochen | Serie sichtbar | ✅ v36 |
| 20 | **Deine beste Zeit**: zu welcher Uhrzeit du Blöcke wirklich schaffst | Planung nach echter Leistung | ✅ v36 |
| 21 | **Morgen-Briefing** erweitert: eigene FamilyWall-Termine (Spieltag, Turnier) und „heute leichter geplant“ | Tag ohne Nachdenken starten | ✅ v36 |
| 22 | **Schnellkachel „Fokus“** (Schnelleinstellungen) startet den nächsten Block | Ohne Umweg ins Handy-Ablenkungsloch | ✅ v36 |
| 23 | **„Schon gelernt“** je Klausur (geschaffte Lern-Blöcke) neben dem Lernplan | Fortschritt sehen | ✅ v36 |
| 24 | **„Heute fällig, noch offen“** – Erinnerung 60–100 Min vor der Stunde | Keine verpasste Abgabe | ✅ v36 |
| 25 | **Widget „Fokus starten“** im Heute-Widget | Vom Homescreen direkt in den Fokus | ✅ v36 |
| 26 | **Reservierte Tage sichtbar** (Kalender: „Reserviert: Spieltag“, Heute: „nur Dringendes“) | Versteht man die Planung | ✅ v36 |
| 27 | **Überschneidungen** fester Termine (z. B. Spieltag ↔ Training/Schicht/Unterricht) im Kalender und auf Heute | Rechtzeitig klären | ✅ v36 |

## Runde 2 (Version 41): von beliebten Apps abgeschaut
Rechenlogik: `planner/Inspired.kt` (Tests: `InspiredTest`). Alles aus vorhandenen Daten, nichts kostet Geld, nichts verlässt das Gerät.

| # | Feature | Abgeschaut von | Wo | Status |
|---|---|---|---|---|
| 28 | **Fokus-Wald**: je 25 geschaffte Minuten ein Baum, verpasste Blöcke verwelken | Forest | Fortschritt | ✅ v41 |
| 29 | **Wochen-Boss**: Wochenziel als Gegner mit Lebenspunkten, jede Woche ein anderer | Habitica | Fortschritt | ✅ v41 |
| 30 | **Du gegen letzte Woche** (zum selben Zeitpunkt) | Duolingo-Liga | Fortschritt | ✅ v41 |
| 31 | **Energie-Verlauf** (14 Tage) + „an fitten Tagen schaffst du X %“ | Daylio | Fortschritt | ✅ v41 |
| 32 | **Schlaf-Karte**: Schnitt, Schlafschuld, „heute bis 21:45 ins Bett“ | Sleep Cycle | Fitness | ✅ v41 |
| 33 | **Trainingsbelastung**: 7 Tage gegen den 4-Wochen-Schnitt (wenig / gleichmäßig / Aufbau / zu viel) | Strava | Fitness | ✅ v41 |
| 34 | **Sport-Monat**: dieser Monat gegen den letzten | Strava-Monatsrückblick | Fitness | ✅ v41 |
| 35 | **Neue Bestleistung** als Benachrichtigung (längster Lauf, schnellste Pace) | Strava | Erinnerungen | ✅ v41 |
| 36 | **Pro Tag verfügbar** bis zum nächsten Taschengeld | YNAB „safe to spend“ | Finanzen | ✅ v41 |
| 37 | **Tage ohne Ausgabe** am Stück | No-Spend-Challenge | Finanzen | ✅ v41 |
| 38 | **Was kostet das in Arbeit?** Preis → Stunden auf der Baustelle | Spar-Apps | Finanzen | ✅ v41 |
| 39 | **Lernzeit je Fach** diese Woche | Focus To-Do | Lernen | ✅ v41 |
| 40 | **Noten-Trend** je Fach (steigt / fällt) | Noten-Apps | Lernen → Noten | ✅ v41 |
| 41 | **Tagesabschluss** ab 20 Uhr: was geschafft, was morgen zuerst | Sunsama | Heute | ✅ v41 |
| 42 | **Ein Satz zum Tag** – erscheint im Wochenrückblick | Daylio | Heute, Wochenrückblick | ✅ v41 |
| 43 | **Tasche für morgen**: Fächer, Sportsachen, fällige Hausaufgaben | MyStudyLife | Heute (ab 18 Uhr) | ✅ v41 |
| 44 | **Countdown** zu eigenen Terminen und Klausuren (6 Wochen) | Countdown-Apps | Heute | ✅ v41 |
| 45 | **Wiederkehrende Aufgaben** (täglich / wöchentlich / alle 2 Wochen / monatlich) | Todoist | Aufgabe bearbeiten | ✅ v41 |
| 46 | **Überfällige neu verteilen** auf drei Tage | Todoist | Aufgaben | ✅ v41 |
| 47 | **App-Symbol lange drücken**: „Fokus starten“, „Neu eintragen“ | Todoist/TickTick | Homescreen | ✅ v41 |
| 48 | **Zeit-Einblicke**: wohin die Stunden der Woche gehen | Google Kalender | Kalender | ✅ v41 |

## Später / Ideen-Speicher 💭
- Hausaufgaben per Foto (Tafelbild) – bewusst nicht: Fotos sind sensibel, Speicherplatz.
- Wetter für Sport-Zeitfenster (Open-Meteo, ohne Konto) – nur, wenn Daniel es will (Netzwerk-Zugriff).
- Führerschein-Theorie-Fortschritt (Fahrschul-App hat keine Schnittstelle).
- Abi-Fächerwahl-Rechner für die Qualifikationsphase (Kurse, Unterkurse) – sobald die Fächer feststehen.
- Galaxy-Watch-Kachel (nächster Block, Timer).
