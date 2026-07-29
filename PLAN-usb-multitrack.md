# PLAN: USB-Multitrack ans Allen & Heath CQ20B (GrillMe-Interview, 2026-07-29)

**Status:** Interview begonnen, NICHT abgeschlossen. Kein Code angefasst.
Vier Punkte sind noch offen (siehe ganz unten) — die nächste Session macht dort weiter,
bevor irgendetwas gebaut wird.

## Ausgangslage (Fakten, bereits vorher belegt)

- Heutiger Weg: `AudioEngine` (ExoPlayer, ein Player pro Stem) gibt über den normalen
  Android-Ausgang aus, der User hängt ein **USB-Klinken-Interface** dran. Musik liegt
  links, **Click + Cue rechts**. Wichtig: `AudioEngine` pant NICHT selbst —
  die Links/Rechts-Trennung steckt in den WAV-Dateien (siehe "vorgepannt" unten).
- CQ20B: **VID `0x22F0` / PID `0x0022`**, UAC2. IF 1 / alt 1 = 24 Kanäle Wiedergabe,
  24-bit, **Endpoint 0x01 (OUT, isochron)** + Feedback-EP **0x81 (IN)**.
- Kill-Kriterium 1 (Detach von `snd-usb-audio` ohne Root) und Kill-Kriterium 2
  (isochroner 440-Hz-Ton, diskret nur auf Kanal 9, 0,009 % Paketfehler) sind am echten
  Pult **bestanden**. Diagnose-Code liegt auf Branch
  `claude/read-current-md-file-7fy90m` (`cpp/usb_tone.c`, `cpp/usb_detach.c`,
  `UsbIsoToneTester.kt`, `UsbDetachTester.kt`) — **nicht auf `main`**.

## Entschieden im Interview (18 Antworten)

### Koexistenz beider Wege
- Beide Wege werden **dauerhaft gleichwertig** gebraucht (nicht "Stereo als Notfall").
- **Kill-Kriterium des Users:** Sobald der Stereo-Weg sich durch den Umbau auch nur
  einmal anders verhält als heute, ist das Projekt gestorben. → Umbau **rein additiv**,
  `AudioEngine` bleibt unangetastet, kein Refactoring "bei der Gelegenheit".
- Modus-Erkennung **nur** über die CQ20B-Kennung (VID/PID), NIE über "USB steckt" —
  das Klinken-Interface ist ja auch ein USB-Gerät und darf nichts auslösen.

### Modus-Wahl / App-Einstieg
- Der Modus wird **pro Gig** festgelegt.
- Daraus folgt ein neuer Pflicht-Einstieg: **beim App-Start muss erst ein Gig gewählt
  werden.** Zusätzlicher Eintrag **"Kein Gig / Nur Archiv / Üben"** → fest Stereo,
  damit zu Hause kein Pseudo-Gig angelegt werden muss.
- **Konflikt Gig=Multitrack, aber Pult nicht erkannt → Wiedergabe blockieren.**
  Deutliche Meldung, kein Ton, bis das Pult da ist oder der User bewusst auf Stereo
  umstellt. Kein Ton ist besser als Ton auf dem falschen Weg.

### Sicherheitsregel (der wunde Punkt)
- **Der Click darf niemals unbeabsichtigt in die PA.** Das ist die Begründung hinter
  fast allen Entscheidungen oben.
- USB-Abbruch mitten im Song → **anhalten**, KEIN stiller Rückfall auf Stereo.
- Im Multitrack-Betrieb geht der Click als **eigener Pultkanal** raus, der In-Ear-Mix
  entsteht am CQ20B. Das Handy gibt dann nichts mehr über Klinke aus.

### Routing
- **Eine globale Zuordnung Stem-Rolle → Pultkanal**, frei patchbar, ausdrücklich
  NICHT pro Song zu pflegen.
- Alle drei Ausnahmefälle sind real: (1) Song hat einen Extra-Stem, (2) Song hat
  weniger Stems → Lücken, aber **nichts darf verrutschen**, (3) anderes Pult/anderer
  Patch → einmal komplett anders, dann aber für den ganzen Abend.
- **Unbekannter Stem-Name → einmalige Nachfrage beim Import**, danach für diesen Song
  gespeichert.
- **Stem-Dateien sind vorgepannte Stereo-Dateien** (Musik hart links, Click/Cue hart
  rechts). Deshalb: **pro Rolle festlegen, welche Seite** auf den Pultkanal geht
  (Musik = links, Click/Cue = rechts als Standard). Kein Mono-Summieren, keine
  Auto-Erkennung.
- **Stummschalten pro Stem-Rolle, global gültig** (z.B. Drums aus, wenn ein echter
  Schlagzeuger dabei ist) — ausdrücklich nicht Song für Song.

### Scope
- **Minimalversion = ein Song sauber und stabil am Pult**, echte Stems statt Sinuston,
  feste Kanalzuordnung, Play/Stop. Alles andere danach.
- **Songwechsel im Multitrack-Betrieb: erstmal nur STOP.** Song endet → Wiedergabe
  stoppt → User startet den nächsten selbst. CUE/AUTOPLAY (lautloses Vorladen) kommen
  später — auf dem nativen Weg muss das Vorladen komplett neu gebaut werden.
- Pre-mortem: dem User fiel die Auswahl schwer, d.h. **alle vier Risiken sind real** —
  (a) App-Funktionen gehen im Multitrack-Modus verloren, (b) Timing nie bühnentauglich,
  (c) der Stereo-Weg leidet, (d) bleibt liegen. Nur (c) ist zum harten Kill-Kriterium
  erhoben worden.

## NOCH OFFEN — hier geht die nächste Session weiter

1. **(a) Gig-Modus-Abfrage konkret:** Wird der Modus am Gig gespeichert und nur beim
   Anlegen gesetzt, bei jedem Öffnen bestätigt, oder beim App-Start abgefragt?
2. **(b) USB-Abbruch mitten im Song:** Wiedergabe stoppt — aber was sieht der User,
   wie kommt er am schnellsten zurück ins Spiel, was passiert mit der Setlist-Position?
3. **(c) Routing-UI:** Wo und wie werden Kanal-Zuordnung, Seite (L/R) und die globalen
   Mutes eingestellt (Rollen-Liste / Pult-Ansicht / Kachel-Raster / Einstellungen)?
4. **(d) Kanalzahl:** Wie viele der 24 möglichen Kanäle werden wirklich belegt, welche
   Rollen gibt es fest?

Danach erst: technischer Plan (Feedback-Sync über EP 0x81, echte Stems statt Sinus,
Übernahme des Diagnose-Codes von `claude/read-current-md-file-7fy90m` nach `main`).

## Arbeitsweise-Hinweis für die nächste Session

Der User arbeitet ausdrücklich am liebsten mit **GrillMe** (`/grillme`) — eine Frage
nach der anderen, keine vorweggenommenen Lösungen. Er achtet darauf, dass die
Reihenfolge der Fragen eingehalten wird: in dieser Session wurde ein Punkt
übersprungen und das ist sofort aufgefallen ("sei genau!"). Also: Liste der offenen
Punkte abarbeiten, nicht springen.
