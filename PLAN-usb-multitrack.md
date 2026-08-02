# PLAN: USB-Multitrack ans Allen & Heath CQ20B (GrillMe-Interview, 2026-07-29)

**Status:** Interview **ABGESCHLOSSEN** (2026-07-29, zweite Hälfte). Kein Code angefasst.
Die vier zuletzt offenen Punkte (a)–(d) sind geklärt und stehen unten als eigener Abschnitt.
Damit ist der Weg frei für den technischen Plan (Feedback-Sync EP 0x81).

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

## Entschieden in der zweiten Interview-Hälfte (Punkte a–d)

### (a) Gig-Modus-Abfrage

- Der Modus (Stereo / Multitrack) ist eine **Eigenschaft des Gigs** und wird dort gespeichert.
- **Beim Öffnen des Gigs** erscheint eine kurze Bestätigung ("Multitrack — übernehmen /
  auf Stereo umstellen"). Nicht nur beim Anlegen, nicht bei jedem App-Start.
- **Ist kein Pult erkannt, schlägt der Dialog Stereo vor** — aber: **nur eine aktive
  Umstellung durch den User schreibt in den Gig.** Ein bloßes Bestätigen des
  Vorschlags ändert den gespeicherten Soll-Modus NICHT. (Sonst würde der
  Auto-Vorschlag den Multitrack-Wunsch löschen und die Regel unten unmöglich machen.)
- **Taucht das Pult später auf** (Techniker schaltet ein, Kabel hing schon) und der Gig
  will Multitrack, während gerade Stereo aktiv ist → **die App meldet sich aktiv**
  ("Pult erkannt — auf Multitrack wechseln?"). Kein stilles Weiterlaufen in Stereo.
  Begründung des Users: sonst geht der Stereo-Ausgang (Musik L / Click R) in den
  2-Kanal-Stream-Eingang des Pults → Click potenziell in der PA.
- **Reine Automatik (Modus allein aus VID/PID) wurde geprüft und verworfen.** Die
  Erkennung selbst ist zuverlässig, aber "Pult steckt" ≠ "ich will übers Pult spielen".
  Ohne gespeicherten Soll-Modus lassen sich zwei bereits gesetzte Regeln nicht
  formulieren: "Multitrack gewollt, Pult fehlt → blockieren" und "Abbruch → anhalten,
  kein stiller Rückfall auf Stereo".
- **"Kein Gig / Nur Archiv / Üben"** bleibt fest Stereo (unverändert aus Teil 1).

### (b) USB-Abbruch mitten im Song

- Ton weg → **sofort Stopp**. Bildschirm bleibt wie er ist (Setlist, aktueller Song an
  Ort und Stelle), oben ein **roter Balken** "Pult getrennt — Wiedergabe gestoppt".
  **Kein Dialog, keine Vollbild-Warnung** — nichts, was weggetippt werden muss.
- **Der abgebrochene Song bleibt gewählt und steht auf Position 0** — ein Tap auf Play
  startet ihn von vorn. Setlist-Position bleibt, der Song gilt **nicht als gespielt**.
  (Kein Fortsetzen an der Abbruchstelle, kein Weiterspringen zum nächsten Song.)
- **Wiederanlauf sichtbar machen:** Zwischen "Kabel steckt wieder" und "wirklich
  spielbereit" liegt der native Neuaufbau (Detach → Claim → SetAlt, ~1–2 s, ohne
  Garantie). Solange bleibt der Balken stehen (neutral: "wird vorbereitet…") und
  **Play bleibt grau**. Erst wenn beides weg ist, ist es echt bereit — damit bedeutet
  ein toter Play-Tap immer einen echten Fehler und nie eine Wartezeit.
  (Der User wollte zunächst "gar nichts sehen", hat es nach Durchspielen des Falls
  "Play gedrückt, nichts kommt" selbst verworfen.)

### (c) Routing-UI

- **Denkrichtung: von der Rolle aus** — Liste der Stem-Rollen, hinter jeder die
  Pultkanal-Nummer ("Drums → Kanal 1"). NICHT die Pult-Sicht ("Kanal 1 → Drums"):
  6 Zeilen statt 24, und die schon gesetzte Regel "weniger Stems → Lücken, nichts
  verrutscht" erfüllt sich von selbst, weil jede Rolle ihre Nummer bei sich trägt.
  L/R-Seite und Mute sind Eigenschaften der Rolle → dieselbe Zeile.
- **Doppelbelegung ist erlaubt, wird aber deutlich markiert** (beide Zeilen warnen),
  plus eine kleine Kanalübersicht, die zeigt, was tatsächlich auf welchem Kanal liegt.
  Kein hartes Verbieten.
- **Genau EIN globales Routing**, kein Verwalten benannter Patches. Beim Fremdpult
  stellt der User es einmal um und danach wieder zurück — bewusst gewählt, das Risiko
  "Zurückstellen vergessen" ist ihm bekannt und akzeptiert.
- **Mutes: eigener Multitrack-Mixer hinter dem bestehenden Mixer-Button.** Im
  Multitrack-Modus zeigt er **nur Stumm-Schalter pro Rolle, mit der Kanalnummer
  daneben — KEINE Regler.** Grund (vom User selbst hergeleitet): im Stereo-Modus sind
  die Regler der einzige Ort, an dem das Stem-Verhältnis entsteht; im Multitrack macht
  die Pegel das Pult. Keine Regler = keine Verwechslung mit den **pro Song**
  gespeicherten Stereo-Werten (`volDrums` …), die global-gültige Mutes gegenüberstehen.
  Nebenbefund: der User stellt die Stereo-Regler "einmal ein, danach kaum" — sie
  bleiben trotzdem unangetastet (Kill-Kriterium).
  Mute muss in der App sitzen und nicht am Pult, weil das Pult beim Techniker steht.
- **Stereo-Stems (echtes Stereo, z.B. Keys mit Hall) sind real** und dürfen **zwei
  Kanäle belegen** (L auf n, R auf n+1). Weil bei einem vorgepannten Stem rechts aber
  der **Click** liegt, ist das **keine Umschaltung in der Routing-Zeile**, sondern eine
  **eigene Rollenart "Stereo", die beim Import bewusst vergeben und einmal ausdrücklich
  bestätigt wird**. Sonst läge der Click auf einem Nachbarkanal, den niemand als Click
  erwartet.

### (d) Kanäle und Rollen

- **Der Rollen-Satz bleibt bei den heutigen sechs:** Drums, Bass, Keys, Vocals, Click, Cue.
  Kein Ausbau, keine frei aus Stem-Namen wachsenden Rollen. Ein Extra-Stem wird beim
  Import einer bestehenden Rolle zugeordnet oder bleibt draußen.
- **Vorbelegung ab Werk: der Reihe nach ab Kanal 1** — Drums→1, Bass→2, Keys→3,
  Vocals→4, Click→5, Cue→6. Der User patcht das Pult entsprechend.
- Also **6 belegte Kanäle** von 24 (mehr nur, wenn eine Stereo-Rolle zwei belegt).

## Entschieden am 2026-08-02: Altbestand und Mixer-Ansicht

Anlass: Formatprüfung der Bibliothek (`WavFormatCheck`, Menü "⋮ → Song-Formate prüfen")
ergab **38 Songs, allesamt Einzeldatei-Stereo** (Musik hart links, Click **und Cue
gemeinsam** hart rechts), davon 25 in 44,1 kHz/16 bit und 13 in 48 kHz/24 bit. Dazu
der entscheidende neue Fakt: **die Studio-One-Projekte dazu existieren nicht mehr** —
diese 38 Songs lassen sich nicht nachträglich als Stems exportieren.

- **Stereo-Songs laufen im Multitrack-Modus als Zwei-Kanal-Song.** Ein Song ohne Stems
  ist dort schlicht ein Song mit zwei Rollen: linker Kanal → Rolle "Musik", rechter
  Kanal → Rolle "Click + Cue". Kein Sonderfall, keine Sperre, keine Rückschaltung auf
  Stereo. Stem-Songs und Stereo-Songs dürfen im selben Set direkt hintereinander laufen.
- **Damit profitiert der Altbestand sofort**, ohne einen einzigen neuen Export: der
  Click liegt auf einem eigenen Pultkanal statt mit der Musik in einem Stereo-Kanalzug
  und kann nicht mehr versehentlich in die PA. Das ist genau die Sicherheitsregel oben.
- **Click und Cue werden NICHT getrennt.** Sie liegen im Bestand gemischt auf dem
  rechten Kanal; sie auseinanderzurechnen wäre der schwerste Teil überhaupt und bringt
  nichts, solange beide ohnehin zusammen ins In-Ear gehen. Die sechs Rollen aus (d)
  bleiben für Stem-Songs; für Stereo-Songs gelten die zwei oben.
- **Mixer-Ansicht richtet sich nach dem geladenen Song, Positionen bleiben aber fest.**
  Nicht vorhandene Rollen werden weiterhin ausgegraut mitangezeigt (heutiges Verhalten
  von `MixerOverlay`), damit "Drums stumm" auf der Bühne bei jedem Song derselbe Knopf
  an derselben Stelle ist — Muscle Memory schlägt aufgeräumte Liste.
- **Kein Resampling in der App.** Die 44,1-kHz-Dateien sind fertige Stereo-Mixe und
  werden im Multitrack-Modus nur als Zwei-Kanal-Song gebraucht; neue Stem-Exporte macht
  der User ohnehin in 48 kHz/24 bit (sein Studio-One-Template). Regel statt Code.
- **Offen (kleiner Folgepunkt):** `SongScanner` erkennt Stems bisher nur bei exakt
  `drums/bass/keys/vocals/click/cue.wav`. Studio One exportiert mit Song- oder
  Spurnamen davor. Erkennung muss den Rollennamen **im** Dateinamen finden, sonst
  müsste der User jede Datei von Hand umbenennen (ausdrücklich unerwünscht).

## Nebenbefund: Behringer FLOW 8 (geprüft, kein Multitrack-Kandidat)

Der User besitzt zusätzlich einen Behringer FLOW 8 und wollte wissen, was damit ginge.
Recherche-Ergebnis (Quellen: Sweetwater, DcSoundOp Firmware v11749, behringer.com):

- Als USB-Interface ist der FLOW 8 **10-in / 2-out**; die 10 Kanäle gehen in die
  Aufnahme-Richtung und nützen fürs Playback nichts.
- **Neuere Firmware kann 4 Kanäle Wiedergabe** (USB OUT 1/2 + 3/4, umschaltbarer
  USB-Modus 2×4 / 10×4), die auf den Kanalzügen **5/6 und 7/8** landen.
- Damit **muss der Click nicht in die PA**: Musik auf 5/6, Click+Cue auf 7/8 → eigener
  Kanalzug, aus der Summe nehmbar. Im reinen 2-Kanal-Modus dagegen schon, weil Musik L
  und Click R in EINEM Stereo-Kanalzug liegen und am Pult nicht mehr trennbar sind.
- **Echtes Multitrack bleibt es nicht:** 4 Signale statt 6 getrennter Rollen. Auch die
  4 Kanäle bekäme Android nur über denselben nativen usbfs-Weg raus (der normale
  Ausgang mischt auf Stereo herunter).
- **Entscheidung des Users: nur als Nebenbefund notieren.** Der Plan bleibt auf das
  CQ20B fokussiert, der FLOW 8 wird NICHT mitgebaut. Falls die Frage wieder aufkommt:
  am eigenen Gerät den `UsbDescriptorScanner` laufen lassen und `bNrChannels` am
  OUT-Endpoint ablesen — gemessen statt geglaubt.

## Noch zu klären, BEVOR Code entsteht (Stand 2026-07-29, Ende der Interview-Session)

Das Interview hat das *Verhalten* geklärt, nicht die *Mechanik*. Diese sechs Punkte sind
offen und sollten in der nächsten Session zuerst besprochen werden:

1. **Wer liefert die Samples?** Heute dekodiert ExoPlayer pro Stem. Der native
   usbfs-Weg braucht rohes PCM aus bis zu 6 Dateien, sample-genau synchron, in einen
   isochronen Puffer. Eigener WAV-Reader oder ExoPlayer-Extraktion? Damit hängt der
   komplette Transport (Start, Position, Ende) am Multitrack-Weg neu — "nur Play/Stop"
   ist deutlich mehr Arbeit, als es klingt.
2. **Welche App-Funktionen gelten im Multitrack-Modus?** Countdown/Fortschritt, Seek,
   Loop, Auto-Stop und vor allem der **Lyrics-Teleprompter** hängen an `positionMs` aus
   `AudioEngine`. Im Multitrack kommt die Position aus der nativen Engine. Was läuft
   weiter, was wird bewusst gesperrt? (Das ist Pre-mortem-Risiko (a) des Users:
   "App-Funktionen gehen im Multitrack-Modus verloren".)
3. **Ist die Klinke im Multitrack wirklich still?** Entschieden ist "das Handy gibt
   nichts mehr über Klinke aus". Offen: wird `AudioEngine` dafür hart stillgelegt?
   Solange sie mitläuft, existiert ein zweiter Click-Pfad — genau das, was nie
   passieren darf.
4. **Stem → Rolle beim Import.** Wie wird ein Dateiname einer der sechs Rollen
   zugeordnet, und wo wird die Antwort auf "unbekannter Stem" bzw. die Rollenart
   "Stereo" gespeichert (pro Song)? Braucht neue DB-Felder.
5. **Room-Migration v19→v20 einmal komplett planen:** Modus am Gig, globales Routing
   (Rolle → Kanal, L/R, Mute), Stem-Rollen pro Song. Lieber einmal sauber als zweimal
   migrieren.
6. **USB-Berechtigung auf der Bühne.** Android fragt beim Anstecken um Erlaubnis für
   das Gerät. Ohne `USB_DEVICE_ATTACHED`-Intent-Filter / "immer für dieses Gerät
   verwenden" steht mitten im Soundcheck ein Systemdialog im Weg.

## Nächster Schritt (erst jetzt technisch)

1. Diagnose-Code von Branch `claude/read-current-md-file-7fy90m` nach `main` holen
   (`cpp/usb_tone.c`, `cpp/usb_detach.c`, `UsbIsoToneTester.kt`, `UsbDetachTester.kt`).
2. **Feedback-Sync über EP 0x81** bauen (Q16.16 = Samples/Mikroframe, Paketgröße
   dynamisch nachführen statt fix 6 Samples). Prüf-Vehikel bleibt der Sinuston auf
   Kanal 9; Ziel: 60 s ohne dazukommende Töne, sauberer Anlauf.
3. Danach: echte Stems statt Sinus, Routing nach dem oben beschriebenen Modell,
   Gig-Modus + Abbruch-Verhalten wie in (a)/(b).

## Arbeitsweise-Hinweis für die nächste Session

Der User arbeitet ausdrücklich am liebsten mit **GrillMe** (`/grillme`) — eine Frage
nach der anderen, keine vorweggenommenen Lösungen. Er achtet darauf, dass die
Reihenfolge der Fragen eingehalten wird: in dieser Session wurde ein Punkt
übersprungen und das ist sofort aufgefallen ("sei genau!"). Also: Liste der offenen
Punkte abarbeiten, nicht springen.
