# Kneiphof: die sieben Brücken von Königsberg um 1910

Rekonstruktion der Pregelinseln von Königsberg um 1910 als animierte 3D-Szene, in Java mit JOGL (OpenGL 4.x) auf der Grafikkarte: Gelände, Pregel mit Spiegelung, Dom, Bürgerhäuser, sieben Klappbrücken mit Schiffsverkehr, Wetter, Tag und Nacht entstehen zur Laufzeit aus Code und Messwerten. Es gibt keine Bilddateien für Materialien: Backstein, Granit, Kupfer, Wasser und Himmel malen die Shader.

![Blick über die Pregelinseln mit Dom, Börse und Brücken](docs/bilder/status_phase12.jpg)

*Bildschirmfoto der Phase 12 auf einer integrierten AMD Radeon 680M: 52 Bilder/s bei 1508 × 940.*

Die Bilder unten stammen aus den Prüfläufen der Shader (Mesa-Software-Renderer, ohne Grafikkarte). Sie zeigen dieselbe Szene, sind aber nicht schärfer oder schöner als das Programm selbst.

## Stand

| Phase | Inhalt | Stand |
|------:|--------|-------|
| 1 | Fundament: Gelände, Himmel mit Sonne und Mond, Pregel, Bedienfeld | fertig |
| 2 | Licht und Material auf der Grafikkarte: Schatten in Kaskaden, HDR, Backstein, Marmor, Kupfer | fertig |
| 3 | Pregel, Kaimauern, Bäume, Laternen | fertig |
| 4 | Dom und Stadt auf dem Plan von 1904 | fertig |
| 5 | Dom innen mit Rundgang, sieben Brücken mit beweglichen Klappen | fertig |
| 6 | Spiegelung, Schaum, Kielwasser, Schiffe | fertig |
| 7 | Dunst, Lichtstrahlen, Rauch, Mond | fertig |
| 8 | Regie: fünf Kamerafahrten mit Kinobalken | fertig |
| 9 | Leben: Schiffe fahren durch die Brücken, Fußgänger | fertig |
| 10 | Zugaben: Wetter (Regen, Nässe, Regenbogen), Möwen, Foto | fertig |
| 11 | Oberfläche und Datenbank: Startbild, Zustand, Ansichten, Protokoll, Einrichter | fertig |
| **12** | **Schliff: Kameraschutz über Dächern, Rathaus, Gymnasium, Albertina und Börse ausgearbeitet, W A S D, Verknüpfung** | **geliefert** (teilweise) |

Offen: Gewölbearten im Dom und Brückenmaße (beides braucht belegte Maße), Euler-Spaziergang, Eisgang, Klang.

## Phase 1 bis 3: Gelände, Licht, Wasser

![Übersicht am Mittag](docs/bilder/p1_uebersicht_mittag.jpg)
![Abend über der Stadt](docs/bilder/p1_abend.jpg)

Die Szene läuft in einem HDR-Puffer mit vorbelichteten Werten (RGBA16F) und umgekehrter Tiefe. Sonne und Mond stehen nach Datum und Uhrzeit am Himmel (21. Juni 1910, 10:30 MEZ ist der Start). Schatten kommen aus Kaskaden.

![Backstein im Streiflicht](docs/bilder/p2_backstein_nah.jpg)
![Kai mit Blick zum Dom](docs/bilder/p3_kai_dom.jpg)
![Laternen am Abend](docs/bilder/p3_laternen_abend.jpg)

## Phase 4: Dom und Stadt

![Dom, Südflanke](docs/bilder/p4_dom_flanke.jpg)
![Dom bei Nacht](docs/bilder/p4_dom_nacht.jpg)

Der Dom steht auf dem Kneiphof mit Strebepfeilern, Fenstern, Portal und Kupferdach. Die Bürgerhäuser stehen in einem Raster, das der Richtung der Inseln folgt: Giebelhäuser mit Treppengiebel, Traufhäuser, Backstein oder Putz in sechs Farben, rote, braune und Schieferdächer, Schornsteine.

## Phase 5: Brücken und Dom innen

![Schmiedebrücke mit hochgeklappten Klappen](docs/bilder/p5_klappen_offen.jpg)
![Orgel im Dom](docs/bilder/p5_innen_orgel.jpg)

Sieben Brücken (Krämer-, Schmiede-, Holz-, Grüne, Köttel-, Honig- und Hohe Brücke) mit zwei Klappen und Gegengewicht unter der Fahrbahn; sie öffnen in 20 Sekunden. Der Dom ist innen begehbar (Taste E, dann W A S D).

## Phase 6 und 9: Wasser, Schiffe, Leben

![Dampfer mit Kielwasser](docs/bilder/p6_dampfer.jpg)
![Brücke am Abend](docs/bilder/p6_bruecke_abend.jpg)
![Fußgänger auf der Krämerbrücke](docs/bilder/p9_leute_kraemer.jpg)

Acht Schiffe (Haffkähne und Dampfer) fahren auf dem Pregel, ziehen Bugwelle und Kielwasser und warten 13 m vor einer Brücke, bis die Klappen offen sind. Je Brücke gehen 14 Fußgänger, die vor den Klappen stehen bleiben.

## Phase 7 und 10: Dunst, Mond, Wetter

![Dichter Morgennebel](docs/bilder/p7_nebel_dicht.jpg)
![Mond über der Stadt](docs/bilder/p7_mond.jpg)
![Regen](docs/bilder/p10_regen.jpg)
![Regenbogen](docs/bilder/p10_bogen.jpg)
![Möwen](docs/bilder/p10_moewen.jpg)

Dunst über dem Pregel löst sich mit der Sonne, Lichtstrahlen fallen durch Dunst und Dom, Rauch steigt aus den Schornsteinen und von den Dampfern. Das Wetter kennt Regen, nasse Flächen und einen Regenbogen. Mit **F12** speichert das Programm ein Foto.

## Phase 8: Regie

![Kamerafahrt](docs/bilder/p8_fahrten.jpg)

Fünf Kamerafahrten führen die Kamera selbst, mit Kinobalken und Blenden. „Ein Tag in 90 Sekunden“ lässt die Uhr durchlaufen.

## Phase 12: Rathaus, Gymnasium, Albertina, Börse

![Börse mit Säulenhalle und Kuppel](docs/bilder/phase12_boerse.jpg)
![Albertina mit zwei Ecktürmen](docs/bilder/phase12_albertina.jpg)
![Gymnasium mit Schaugiebeln](docs/bilder/phase12_gymnasium.jpg)

Die vier großen Bauten sind keine Würfel mehr: das **Rathaus** mit Mittelrisalit, Gauben und Turm, das **Gymnasium** mit zwei Schaugiebeln und Dachreiter, die **Albertina** mit zwei Ecktürmen und Giebelportal, die **Börse** mit Säulenhalle und Kuppel. Ihre Gestalt ist eine begründete Annahme aus Bautyp und Zeit, kein Aufmaß. Die Kamera hebt sich über Dächer, statt durch sie zu fahren.

## Bedienung

| Eingabe | Wirkung |
|---------|---------|
| Maus links ziehen / rechts ziehen / Rad | drehen / verschieben / zoomen |
| **W A S D** | den Drehpunkt vor, zurück, zur Seite bewegen (Umschalt: schneller); im Dom gehen |
| Pfeiltasten, Bild auf/ab, + − | drehen und zoomen |
| Pos1 | Übersicht |
| E | Dom betreten und verlassen |
| B | zur nächsten Brücke |
| F | dem nächsten Schiff folgen |
| H | Pfeife des Dampfers |
| R | Rundflug |
| P | Prüfstand |
| V | aktuelle Ansicht mit Namen merken |
| F12 | Foto |

## Starten

1. In NetBeans *Clean and Build*, dann *Run*, oder `Kneiphof.bat` im Projektordner.
2. Wer das Programm oft startet: `Verknuepfung.bat` legt auf dem Desktop die Verknüpfung „Kneiphof 1910“ mit dem Programmsymbol an.
3. Bleibt das Bild schwarz: in `Kneiphof.bat` die Zeile mit `-Dkneiphof.gljpanel=true` aktivieren.

Java 21 und eine Grafikkarte mit OpenGL 4.x sind nötig. Das Projekt braucht `lib/FStyle.jar`, JOGL und `ojdbc11.jar`.

## Datenbank (optional)

Das Programm läuft auch ohne Datenbank; der Zustand liegt dann in `~/.kneiphof/`. Mit Oracle 21c (Schema `DEMO`) kommen die Tabellen `KNH_SESSION`, `KNH_STATE` und `KNH_EVENT` und das Paket `KNH_API` dazu:

1. `db/db.properties.example` nach `db/db.properties` kopieren und die URL prüfen.
2. Im Startbild das Passwort eingeben und *Verbinden* wählen. Stimmt es, trägt das Programm es in `db/db.properties` ein (Klartext; die Datei steht in `.gitignore` und kommt nie ins Git). Alternativ die Umgebungsvariable `KNEIPHOF_DB_PASSWORD` setzen.
3. *Einrichten …* legt fehlende Objekte an und endet mit „ERGEBNIS: alles in Ordnung“. Die Skripte stehen in `db/` und lassen sich beliebig oft ausführen.

Zustand (Kamera, Zeit, Wetter, alle Schalter) wird alle 60 Sekunden und beim Beenden gesichert und beim Start wiederhergestellt. Ansichten, Sitzungen und Ereignisse stehen in der Datenbank.

## Prüfwerkzeuge

`tools/glcheck.py` und `FrameDump` rendern die Szene mit denselben Shadern unter Mesa, wo kein JOGL läuft: `FrameDump <ordner>` schreibt Gelände, Stadt und Kamerawerte, `glcheck.py <shader> <ordner> <bilder> [Namen]` rendert daraus die Bilder. Die Bilder in diesem README stammen so.
