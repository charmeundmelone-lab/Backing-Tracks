# PLAN — Show-Automatik: Pausen, Sprachnotizen, Frei spielen

Ergebnis eines GrillMe-Interviews (2026-09-12), Ziel des Users: eine
möglichst **hands-free** Show (im Rahmen — "ein-, zwei-, dreimal antippen
während der ganzen Show ist okay"). **FINAL ABGESTIMMT** (Teil 1+2+3 komplett
durchgegrillt) — Umsetzung darf in der nächsten Session beginnen. Bisher
**kein Code geschrieben**, reine Konzeption.

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
7. **Aufnahme (Details nachgegrillt, 2026-09-12):** Direkt in der App,
   Mikrofon-Button im `SongEditorSheet`. Kein Datei-Import (SAF) für
   Notizen — die Datei liegt versteckt im App-internen Speicher (nicht im
   normalen Dateisystem sichtbar, wie ein Spielstand: geht bei
   Deinstallation der App mit verloren). **Bedienung:** einmal auf das
   Mikrofon-Icon tippen = Start, nochmal tippen = Stopp (kein
   Gedrückthalten wie bei einer WhatsApp-Sprachnachricht — bei längeren
   Ansagen unbequem). **Nach dem Stopp:** die App spielt die Aufnahme
   sofort vor, danach entscheidet der User: übernehmen oder neu
   aufnehmen (alte Aufnahme wird dabei verworfen).
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
13. **Lautstärke beim Abspielen (nachgegrillt, 2026-09-12):** Feste,
    fest einprogrammierte Lautstärke — kein eigener Regler, keine Kopplung
    an den bestehenden Cue-Regler (`volCue`). Einfachster Weg: immer gut
    hörbar, ohne dass der User etwas einstellen muss.

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
- Speicherort der Aufnahmen: App-internes Storage (kein SAF, siehe Punkt 7,
  vom User explizit bestätigt), Dateiname z.B. `<songId>_intro.m4a` /
  `<songId>_outro.m4a`. Aufnahme-Format: `MediaRecorder` (AAC/`.m4a`) —
  Android-Standard für Sprachaufnahmen, kein NDK nötig.
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

## Teil 3 — Ergebnis des zweiten GrillMe-Interviews (2026-09-12, abgeschlossen)

1. **Fehler-Fallback:** Fehlende/kaputte Vorlauf-/Nachlauf-Datei → wie
   "keine Notiz vorhanden" behandeln (Fallback auf manuelle Sekunden bzw.
   0), Show läuft **sofort weiter, kein Hänger**. Zusätzlich ein
   **sichtbarer Hinweis in der `PlayerInfoBar`**, damit der Fehler
   nachträglich auffällt und die Notiz neu aufgenommen werden kann.
   Relevant wegen des bekannten `AudioEngine`-Gotchas (kein
   Fehler-Listener) — der Hinweis kompensiert das für diesen Anwendungsfall,
   ohne `AudioEngine` selbst anzufassen.
2. **Diagnose-Tool "Automatik-Check":** **Wird gebaut.** Eigener Menüpunkt
   (gleiches Muster wie `WavFormatCheck`/`SongLinkCheck`), listet pro Set
   auf, welche Auto-Advance-Übergänge (`endAction = AUTOPLAY`) 0 Sekunden
   Pause **und** keine Notiz haben — Lücken-Erkennung vor dem Gig.
3. **Hands-free Show-Start:** **Bewusst offen gelassen.** Song 1 eines
   Sets braucht weiterhin einen manuellen Tap — im Rahmen des Users
   ("ein-, zwei-, dreimal antippen ist okay"). Der Bluetooth-Fußschalter
   bleibt ein eigenständiges, separat zurückgestelltes TODO (siehe
   CLAUDE.md), keine Kopplung in dieser Iteration.
4. **Sichtbarkeit in der Setlist:** **Ein** Mikrofon-Icon in `SetSongRow`
   (analog zum bestehenden ★-Spontan-Marker), sichtbar sobald Vorlauf
   ODER Nachlauf vorhanden ist — keine getrennte Kennzeichnung der beiden
   Slots.
5. **Show-Ende-Verhalten:** **Kein Sonderfall/keine neue Logik.** Die
   bestehende Nachlauf-Notiz-Mechanik aus Teil 1 deckt eine gewünschte
   Abschluss-Ansage am letzten Song bereits ab — nichts zusätzlich zu
   bauen.

## Teil 4 — Zeit-Picker "Manuelle Pause" + Warnton (GrillMe 2026-09-12, Nachtrag)

Ausgelöst durch einen Screenshot des Nothing-OS-Timers ("so soll die
Zeiteingabe aussehen"). Der ursprüngliche ±5s-Stepper für
`manualPauseSeconds` (Max. 60s) war eine willkürliche Implementierungs-
Entscheidung aus Schritt 1, keine Plan-Vorgabe.

### Entscheidungen
1. **UI:** Ziffernblock-Dialog (1-9, 00, 0, ⌫) statt Stepper — **Option 1**
   aus drei vorgeschlagenen (Alternative "reusable Komponente" und "voller
   Wheel-Nachbau mit Std/Min/Sek" bewusst verworfen, da nur EIN Einsatzort
   existiert und ein Stunden-Wheel bei max. 60 Minuten ohnehin leer wäre).
   Eingabe als 4-stelliger Schiebepuffer (MMSS), keine Sekunden-Validierung
   nötig — `Übernehmen` rechnet einfach Minuten×60+Sekunden.
2. **Max. Pause:** 60 Minuten (vorher 60 Sekunden).
3. **Warnton — Zweck:** Weck-/Ankündigungssignal NUR für die stille
   Fallback-Pause (`manualPauseSeconds`, keine Notiz vorhanden). Die
   bereits hörbaren Vorlauf-/Nachlauf-Sprachnotizen brauchen keinen
   zusätzlichen Ton.
4. **Vorlauf-Formel:** Pausenlänge ÷ 3, gedeckelt bei max. 5 Minuten — EINE
   Formel für alle Pausenlängen, kein Sonderfall für kurze/lange Pausen
   (60-Min-Pause → Ton 5 Min. vorher; 90s-Pause → 30s vorher; 9s-Pause →
   3s vorher). Load der beiden zuerst diskutierten Einzel-Ideen ("fix 5 Min
   vorher" / "immer letztes Drittel") in einer Formel.
5. **Nur ein Ton** — kein zweiter Ton exakt beim Wiederbeginn.
6. **Ton-Quelle:** fest in der App eingebaut (additive Glocken-Synthese,
   ~5s, aufsteigendes Arpeggio C5-E5-G5-C6 mit Abkling-Hüllkurve — siehe
   `WavSynth.writeWarningChime()`), NICHT pro Song aufnehmbar. Einmalig ins
   App-interne Storage geschrieben, danach wiederverwendet.
7. **Panning:** hart rechts, nur Cue-Kanal — wie Sprachnotizen und Click,
   Publikum bekommt nichts mit.

Keine der Entscheidungen ist schwer rückgängig zu machen (kein neuer
Migrationsschritt — `manualPauseSeconds` existiert als Int bereits, nur
UI-Grenze und ein neuer Laufzeit-Mechanismus für den Ton).

## Status — bereit zur Umsetzung

- Kein Gradle-Build nötig gewesen (reine Konzeption, kein Code).
- **Teil 1 + 2 + 3 final abgestimmt.** Umsetzungsreihenfolge für die
  nächste Session (siehe auch CLAUDE.md, TODO PRIO 1, Punkt 7):
  1. ✅ Room-Migration v19→v20 (`Song.kt`: `introNoteFilePath`/
     `introNoteDurationMs`/`outroNoteFilePath`/`outroNoteDurationMs`/
     `manualPauseSeconds`, `MIGRATION_19_20`, `SongDao`-Update-Methoden).
  2. ✅ Mikrofon-Aufnahme im `SongEditorSheet` (aufnehmen → anhören → neu
     aufnehmen → speichern, App-internes Storage, kein SAF).
  3. ✅ `AudioEngine`: separater Wiedergabe-Pfad für Notizen, hart rechts
     gepannt (`MediaPlayer.setVolume(0f, 1f)`).
  4. ✅ `PlayerViewModel`: Pause-/Ansage-Ablauf bei Auto-Advance (Nachlauf →
     Vorlauf, Fallback-Sekunden, Live-Abbruch per Tap, Fehler-Fallback mit
     sichtbarem Hinweis), großer Volt-Countdown in `PlayerInfoBar`.
  5. ✅ "Frei spielen"-Button (global, nur in der Pause aktiv, reiner
     Laufzeit-Zustand, kein DB-Feld) + Statustext in `PlayerInfoBar`.
  6. ✅ Mikrofon-Icon in `SetSongRow` (Sichtbarkeits-Marker aus Teil 3,
     Punkt 4).
  7. Diagnose-Tool "Automatik-Check" (Teil 3, Punkt 2), analog
     `WavFormatCheck`/`SongLinkCheck`.
  8. ✅ Performance-Lock beachten: Pause-Abbrechen-Tap und "Frei
     spielen"-Button bleiben **immer** aktiv, auch bei `isLocked`
     (Teil 1 Punkt 12 / Teil 2 Punkt 7) — GlobalPlayer war nie
     `isLocked`-gegatet.
  9. ✅ Teil 4: Ziffernblock-Zeit-Picker + Warnton (siehe oben).

**Einziger noch offener Schritt: Punkt 7, Diagnose-Tool "Automatik-Check".**
