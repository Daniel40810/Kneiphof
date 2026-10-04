# Datenbank (Phase 11)

Optional: Kneiphof läuft auch ohne Datenbank (Zustand dann in `~/.kneiphof/`).

1. `db/db.properties` prüfen (URL `jdbc:oracle:thin:@//localhost:1521/PDBORCL`, Benutzer `DEMO`).
2. Passwort: im Startbild eingeben (nur im Speicher) oder Umgebungsvariable `KNEIPHOF_DB_PASSWORD`.
3. Startbild -> "Einrichten ..." legt KNH_SESSION, KNH_STATE, KNH_EVENT und das Paket KNH_API an
   (idempotent, beliebig oft wiederholbar). Erwartet: "ERGEBNIS: alles in Ordnung".

Skripte: 01_tables.sql, 02_objekte.sql, 03_api.sql (Statements enden mit einer Zeile `/`).
Ansichten: Taste V merkt die aktuelle Kamera als benannte Ansicht (KNH_STATE, Art ANSICHT).
Automatische Sicherung alle 60 s und beim Beenden (Art AUTO, die letzten 10 bleiben).
