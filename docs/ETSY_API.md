# Etsy-API verbinden (später, wenn der Shop live ist)

Bis dahin trägst du Verkäufe und Statistiken in der App selbst ein (Mehr → Etsy-Shop).
Die automatische Anbindung nutzt die **offizielle Etsy Open API v3** mit **OAuth 2.0 (PKCE)**, und zwar **nur mit Leserechten**.

## Schritt 1: App bei Etsy registrieren
1. Mit deinem Etsy-Konto auf **https://www.etsy.com/developers** einloggen.
2. **„Create a New App“** (bzw. „Your Apps“ → „Create“) wählen.
3. Ausfüllen:
   - **Name:** z. B. `Life Dashboard Privat`
   - **Beschreibung:** „Private App, liest nur meine eigenen Bestellungen und Statistiken.“
   - **Wer nutzt die App:** nur ich selbst (persönliche Nutzung)
   - **Kommerziell:** nein
4. Nach dem Absenden siehst du einen **Keystring** (API-Key) und ein **Shared Secret**.
   - **Wichtig:** Diese Werte niemals in Chats, Screenshots oder Git teilen.
5. Bei **Callback-URLs / Redirect URIs** trägst du die Adresse ein, die die App beim Verbinden anzeigt.

> Neue Etsy-Apps starten meist mit „Personal Access“. Das reicht genau für den eigenen Shop.

## Schritt 2: In der App verbinden
*(wird gebaut, sobald dein Shop live ist)*
1. Mehr → Etsy-Shop → „Etsy-API verbinden“.
2. Den Keystring einfügen. Er wird verschlüsselt im Android Keystore gespeichert, nicht im Code und nicht in Git.
3. Die App öffnet die Etsy-Anmeldung. Du erlaubst dort **nur Lesen** (`shops_r`, `listings_r`, `transactions_r`).
4. Danach holt die App alle paar Stunden im Hintergrund (WorkManager):
   - Bestellungen & Umsätze → Verkäufe, Netto, Vormonatsvergleich
   - Listings mit Aufrufen und Favoriten
   - neue Bestellung → Benachrichtigung

## Sicherheit
- Zugriffstoken liegen verschlüsselt auf dem Handy und laufen automatisch ab (Refresh-Token).
- Kein eigener Server: Die App spricht direkt mit `openapi.etsy.com`.
- Trennen geht jederzeit in der App oder bei Etsy unter „Apps & Verbindungen“.
