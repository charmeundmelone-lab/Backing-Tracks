# PLAN — Show-Automatik: Pausen, Sprachnotizen, Frei spielen

Ergebnis eines GrillMe-Interviews (2026-09-12), Ziel des Users: eine
möglichst **hands-free** Show (im Rahmen — "ein-, zwei-, dreimal antippen
während der ganzen Show ist okay"). **Zwischenstand, NICHT final** — Teil 3
listet offene Themen, die in einer künftigen Session noch gegrillt werden.
Bisher **kein Code geschrieben**, reine Konzeption.

## Teil 1 — Pause zwischen Songs + Sprachnotizen (Vorlauf/Nachlauf)

### Grundidee
Bei Auto-Advance (`endAction = AUTOPLAY`) soll die Pause bis zum nächsten
Song konfigurierbar sein — entweder durch eine aufgenommene Sprachnotiz
("Regieanweisung", nur für den Musiker hörbar) oder durch einen festen
Sekundenwert.

### Entscheidungen
1. **Gekoppelt:** Ein Feature, nicht zwei. Pause-Länge = Länge der
   Sprachnotiz, falls vorhanden — sonst fester/manueller Sekundenwert.
2. **Anbindung:** Pro Song **global** (wie `lyrics`), NICHT pro
   Set-Position/`SetSongCrossRef`. Bewusst gegen die Empfehlung entschieden
   (Empfehlung war: pro Set-Position, analog zu `endAction`) — User-Wunsch.
3. **Zwei unabhängige, optionale Slots pro Song:**
   - **Vorlauf-Notiz** — läuft, bevor dieser Song startet (Ankündigung/Prep).
   - **Nachlauf-Notiz** — läuft, nachdem dieser Song endet.
4. **Beide an einem Übergang vorhanden** (Nachlauf von Song A + Vorlauf von
   Song B): beide spielen **nacheinander** ab (Nachlauf(A) → Vorlauf(B)),
   Pause = Summe beider Längen.
5. **Auslöser:** Ausschließlich bei Auto-Advance (`endAction = AUTOPLAY`).
   Manuelles Antippen/Skip eines Songs löst NIE eine Ansage aus.
6. **Scope:** Nur der aktuelle Stereo-Pfad — Ansage wird **hart rechts**
   gepannt abgespielt, genau wie das bestehende Click+Cue-Signal (Modus B).
   USB-Multitrack-Integration (eigene Rolle) ist explizit NICHT Teil dieser
   Iteration, sondern ein späteres, eigenes Thema.
7. **Aufnahme:** Direkt in der App, Mikrofon-Button im `SongEditorSheet`
   (aufnehmen → anhören → ggf. neu aufnehmen → speichern). Kein
   Datei-Import (SAF) für Notizen.
8. **Manuelle Pause ohne Notiz:** Eigenes Sekundenfeld **pro Song**
   (Default 0), kein globaler App-weiter Wert.
9. **Kombination Notiz + manuelle Sekunden:** Manuelle Sekunden gelten
   **nur als Fallback**, wenn an dieser Stelle GAR KEINE Notiz existiert.
   Keine zusätzliche Stille wird auf eine vorhandene Notiz draufgerechnet.
10. **Live-Abbruch:** Ein Tap auf den Player während der laufenden
    Pause/Ansage überspringt sie sofort, der nächste Song startet.
11. **Anzeige:** Großer, Volt-farbener Countdown in der bestehenden
    `PlayerInfoBar` (nicht nur eine kleine Zahl — deutlich sichtbar von der
    Bühne aus, ähnliche Größenordnung wie der bestehende 22sp-Songtitel).
12. **Performance-Lock:** Der Abbrechen-Tap (Punkt 10) bleibt **immer**
    verfügbar, auch wenn `isLocked` aktiv ist — wie Play/Pause, keine
    Kern-Wiedergabefunktion darf durch den Fehltipp-Schutz blockiert werden.

### Datenmodell (Room v19→v20, Vorschlag)
`data/Song.kt`, neue Felder:
```kotlin
val introNoteFilePath: String = ""   // Vorlauf-Sprachnotiz, App-internes Storage
val introNoteDurationMs: Long = 0L
val outroNoteFilePath: String = ""   // Nachlauf-Sprachnotiz
val outroNoteDurationMs: Long = 0L
val manualPauseSeconds: Int = 0      // Fallback, nur wenn keine der beiden Notizen existiert
```
Migration `MIGRATION_19_20` (ADD-only, wie alle bisherigen Migrationen).

### Offene technische Details (kein User-Entscheid nötig, bei Umsetzung klären)
- Speicherort der Aufnahmen: App-internes Storage (kein SAF, da in-App per
  Mikrofon aufgenommen), Dateiname z.B. `<songId>_intro.m4a` /
  `<songId>_outro.m4a`.
- Panning auf "hart rechts" für eine mono aufgenommene Datei: eigener
  Wiedergabe-Pfad (nicht Teil von `AudioEngine.tracks`), Kanal-Lautstärke
  oder ExoPlayer-`setVolume` pro Kanal — Ansatz erst bei der Umsetzung fest
  entscheiden.
- Fehlerverhalten bei kaputter/fehlender Notiz-Datei: siehe Teil 3, Punkt 1
  (noch offen).

## Teil 2 — "Frei spielen" (Freeze-Modus für tempofreie Songs)

### Grundidee
User spielt gelegentlich, spontan und nicht an eine feste Setlist-Position
gebunden, rein akustische Songs "aus dem Gefühl" ohne festes Tempo. Dafür
ein globaler Umschalter, der die komplette Automatik anhält, solange er
aktiv ist.

### Entscheidungen
1. **Ein Button, global**, kein per-Song-Setting (User spielt diese Songs
   nicht an einer festen Stelle, sondern nach Gefühl — ein CUE-artiges
   per-Song-Flag würde nicht reichen).
2. **Separater Button**, nicht dasselbe Element wie der "Pause
   überspringen"-Tap (Teil 1, Punkt 10) — zwei unterschiedliche Zwecke,
   bewusst nicht als verstecktes Doppel-Tap-Verhalten gebaut (siehe
   Sprint 5.29, "Verstecktes Doppel-Tap-Verhalten entfernt").
3. **Nutzbar nur in der Pause** zwischen zwei automatisierten Songs. Ein
   gerade laufender automatisierter Song wird NIE durch diesen Button
   unterbrochen/abgeschnitten.
4. **Aktivierung während laufender Ansage:** Ansage-Wiedergabe wird sofort
   stummgeschaltet, Freeze greift ohne Verzögerung.
5. **Wiedereinstieg** (Button erneut gedrückt): Springt **sofort** zum
   nächsten automatisierten Song — keine Rest-Pause/Ansage wird nachgeholt,
   der freie Song hat die Pausenfunktion bereits erfüllt.
6. **Anzeige/Name:** "Frei spielen" — sowohl als Button-Beschriftung als
   auch als Statustext in der `PlayerInfoBar` anstelle des Countdowns
   (Volt-Optik, analog zu Teil 1 Punkt 11).
7. *(mitgedacht, konsistent zu Teil 1 — nicht erneut gegrillt):* Button
   ist außerhalb der Pause grau/inaktiv; bleibt immer verfügbar trotz
   `isLocked` (wie Punkt 12 in Teil 1); braucht kein DB-Feld, reiner
   Laufzeit-Zustand in `PlayerViewModel` — dadurch auch gut geeignet für
   den später geplanten Bluetooth-Fußschalter (einfacher Ein/Aus-Toggle).

## Teil 3 — Offene Themen für die nächste GrillMe-Session

Vom User in Auftrag gegeben ("in der nächsten Session grillen wir die
Punkte, die du angesprochen hast"). Noch NICHT durchgegrillt:

1. **Fehler-Fallback:** Was passiert, wenn eine Vorlauf-/Nachlauf-Datei
   fehlt oder kaputt ist? Vorschlag zur Diskussion: automatisch wie "keine
   Notiz vorhanden" behandeln (Fallback auf manuelle Sekunden bzw. 0),
   NICHT hängen bleiben. Besonders relevant, weil `AudioEngine` aktuell
   keinen Fehler-Listener hat (bekannter Gotcha, bisher bewusst nicht
   gefixt) — bei einer vollautomatischen Show ohne Eingriffsmöglichkeit
   wird ein stiller Fehler riskanter als bisher.
2. **Diagnose-Tool "Automatik-Check" vor dem Gig:** Analog zu
   `WavFormatCheck`/`SongLinkCheck` — pro Set auflisten, welche
   Auto-Advance-Übergänge 0 Sekunden/keine Notiz haben, um Lücken vor der
   Bühne zu erkennen.
3. **Hands-free Show-Start:** Song 1 eines Sets braucht weiterhin einen
   manuellen Tap. Bezug zum bestehenden, zurückgestellten TODO
   "Bluetooth-Fußschalter" (Page-Turner-Pedal) — beides zusammen ergibt
   erst eine wirklich handfreie Show.
4. **Sichtbarkeit in der Setlist:** Kleines Icon in `SetSongRow`, das
   zeigt, ob ein Song eine Vorlauf-/Nachlauf-Notiz hat (analog zum
   bestehenden ★-Spontan-Marker).
5. **Show-Ende-Verhalten:** Letzter Song im letzten Set, `autoAdvanceSets`
   aktiv — was passiert danach? Aktuell vermutlich "nichts". Eventuell
   Abschluss-Anzeige/-Ansage, kann aber auch bewusst außerhalb des Scopes
   bleiben.

## Status

- Kein Gradle-Build nötig gewesen (reine Konzeption, kein Code).
- Nächster Schritt: Teil 3 in einer eigenen GrillMe-Session klären, erst
  danach Umsetzung von Teil 1 + Teil 2 beginnen.
