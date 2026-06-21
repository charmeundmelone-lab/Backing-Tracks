# CHANGELOG — Live-Gig-Player Pro

---

## [Sprint 5.9 — Loop-Editor Fixes + Live-Player Logik]

**Datum:** 2026-06-21
**Branch:** main

### WaveformAnalyzer — dataChunkSize-Fix
- `"data" -> { dataChunkSize = chunkLen; break@outer }` — Chunk-Größe wird jetzt gespeichert
- `framesPerWindow` berechnet sich als `totalFrames / maxSamples` → 1.000 Samples verteilen sich über den gesamten Song (nicht nur erste 50 Sek)
- Fallback bei Streaming-WAV (0xFFFFFFFF): 10 Minuten angenommen
- `winBufSize` bis zu 512 KB (vorher 64 KB cap) für lange Songs

### AuditionPlayer (neu — `audio/AuditionPlayer.kt`)
- Separater ExoPlayer ausschließlich für Vorhör im Loop-Editor
- `startLoop(uri, startMs, endMs)` setzt 64× ClippingConfiguration + REPEAT_MODE_ALL
- `stop()` / `release()` für sauberes Lifecycle-Management

### LoopEditorScreen — vollständige Überarbeitung
- `Modifier.safeDrawingPadding()` am Root-Layout: Status-Bar und Nav-Bar bleiben frei
- `auditionUri` State: URI aus TrackMode-Scan wird gespeichert und an AuditionPlayer übergeben
- `DisposableEffect(Unit)` → `auditionPlayer.release()` bei Composable-Verlassen
- Vorhör-Button "▶ VORHÖR" / "◼ STOP" zwischen Waveform und Fine-Tune-Panel
- `LaunchedEffect(isAuditioning, loopStartMs, loopEndMs)` mit 200 ms Debounce → Audition-Player startet neu wenn Handles verschoben werden
- Pan-Formel korrigiert: `viewStartFraction -= df / zoomLevel` → kein Überscroll am Ende
- Loop-Overlay Clip korrigiert: `(ex - ox).coerceAtMost(canvasW - ox)` bleibt immer im Canvas
- Close/Save: `auditionPlayer.stop()` wird vor Callback aufgerufen (kein Hintergrundrauschen)

### MainScreen — LOOP-Button 3 Zustände
- `PlayerBtn`: neuer `enabled: Boolean = true` Parameter; wenn `false` → kein `clickable`
- `loopArmed = song != null && song.loopStartMs > 0L && song.loopEndMs > song.loopStartMs`
- **Aktiv** (`loopActive`): Tint = Volt, BG = `#1A1A00`
- **Armed** (`loopArmed`): Tint = VoltDim (50% Volt), BG = `#141400`
- **Disabled**: Tint = Gray, BG = BgCard, nicht klickbar

---

## [Sprint 5.8 — Visueller Loop-Editor (Koala-Style)]

**Datum:** 2026-06-21
**Branch:** main

### WaveformAnalyzer (neu)
- Liest WAV-Dateien über SAF-ContentResolver (RIFF-Chunk-Iteration: `fmt ` + `data`)
- Downsampling auf 50ms-Fenster → normalisiertes RMS-Array für Canvas
- Onset-Erkennung: Energie-Anstieg >2,5× geglättetes Baseline → magnetische Snap-Punkte
- Multitrack: bevorzugt Click- bzw. Drums-Spur für Analyse

### LoopEditorScreen (neu)
- Vollbild-Overlay: Canvas-Wellenform (RMS-Balken), Onset-Marker (Volt), Koala-Overlay (halbtransparent grün)
- Start-Handle (grün) + End-Handle (rot): Drag-to-move; snap-to-nearest-onset beim Loslassen
- Pinch-to-Zoom (zwei Finger): Zoom 1×–200×, Anker-Punkt bleibt bei Centroid
- Pan: ein Finger scrollt Ansicht
- Fein-Tuning: `← Start` / `Start →` / `← Ende` / `Ende →` — je 1ms
- Zeitanzeige: START / LÄNGE / ENDE in Monospace-Format

### Datenbank v8 → v9
- `Song.kt`: `loopStartMs: Long = 0L`, `loopEndMs: Long = 0L`
- `MIGRATION_8_9`: zwei neue Spalten (ALTER TABLE)
- `SongDao`: `updateLoopPoints(id, startMs, endMs)`

### AudioEngine — `activateLoopDirect(startMs, endMs)`
- DB-Werte direkt einsetzen, kein BPM-Berechnen
- Identische gapless-Technik: 64× ClippingConfiguration + REPEAT_MODE_ALL
- Alle Stems (Multitrack) synchron

### PlayerViewModel + MainScreen
- `toggleLoop()` nutzt `activateLoopDirect()` wenn DB-Punkte vorhanden, sonst BPM-Snap
- `updateLoopPoints()` persistiert Punkte in DB
- SongEditorSheet: Button "Loop visuell bearbeiten" öffnet LoopEditorScreen als Vollbild-Overlay

---

## [Sprint 5.7 — Player Layout: Countdown links, Songs rechts]

**Datum:** 2026-06-20
**Branch:** main

### GlobalPlayer Layout-Überarbeitung
- Countdown links: 38sp, Volt, Monospace, FontWeight.Bold — dominantes Element
- Songtitel rechts: 18sp, FontWeight.Bold, White — klar lesbar
- Nächster Song: 14sp, FontWeight.SemiBold, White (statt Gray 12sp) — deutlich lesbarer
- SkipNext-Icon neben "Nächster Song"-Text in Weiß
- Buttons (PLAY/PAUSE, STOP, LOOP) unverändert: 72dp, breite Touch-Flächen

---

## [Sprint 5.6 — Globaler Player: Einheitlicher Bottom-Player in beiden Tabs]

**Datum:** 2026-06-20
**Branch:** main

### Globaler Player (GlobalPlayer)
- `MiniPlayer` (96dp) → `GlobalPlayer` ersetzt; in BEIDEN Tabs fest am unteren Rand
- Songtitel: 22sp, `FontWeight.Bold` — Headline-Style für Bühnensichtbarkeit
- Countdown: 15sp Volt Monospace direkt unter dem Titel
- Nächster Song: Icon + Titel + optionales Capo in derselben Zeile wie Countdown
- Fortschrittsbalken: 3dp Volt am oberen Rand des Players

### Transport-Buttons (3 statt 4)
- `PLAY/PAUSE` (Toggle, weight=2f): Hintergrund grün bei Play, Icon/Label wechseln
- `STOP`: roter Icon (RedStop), stoppt + seekTo(0)
- `LOOP`: leuchtet Volt wenn aktiv, dunkler Hintergrund zur Bestätigung
- ZURÜCK + WEITER vollständig entfernt
- Touch-Targets: 72dp Höhe, breite Fläche (weight statt fixer Breite)

### Code-Cleanup
- `StageTransport`-Composable entfernt
- `TransportButton`-Composable entfernt
- `PlaylistTab`: `loopActive`-Parameter entfernt, `isPlaying`/`positionMs`/`durationMs` State entfernt
- `PlaylistTab`: äußere `Column` entfernt, direkt `LazyColumn` zurückgegeben
- Import `SkipPrevious` → `Stop` ausgetauscht

---

## [Sprint 5.5 — Bildschirmoptimierung: Kompakte TopBar]

**Datum:** 2026-06-20
**Branch:** main

### TopBar-Umbau
- App-Titel "Live-Gig-Player Pro" entfernt — spart ca. 40dp vertikale Höhe
- Untere Tab-Leiste (64dp) vollständig entfernt
- Tab-Navigation als zwei große Icons (30dp) in die TopBar integriert:
  - `LibraryMusic` → Tab A (Archiv), leuchtet Volt wenn aktiv
  - `QueueMusic` → Tab B (Playlist), leuchtet Volt wenn aktiv
- Tab-Icons stehen linksbündig, Aktions-Icons (Import/Mixer/Lock/Menü) rechtsbündig
- Hamburger-Menü bleibt auf Tab A beschränkt (Sicherheitsregel eingehalten)
- `TabButton`-Composable und `TabActive`/`TabInactive`-Konstanten entfernt

### Gewinn
- 64dp Bildschirmraum freigegeben → Song-Liste zeigt mehr Einträge

---

## [Sprint 5.4 — Korrekturen: Capo, Mini-Player, Gapless Loop]

**Datum:** 2026-06-20
**Branch:** main

### UI-Korrektur Tab A (Capo)
- Capo-Stepper ("− 0 + Kapo") vollständig aus `ArchivSongRow` entfernt
- `onCapoChange`-Parameter aus `ArchivSongRow` und Aufruf-Stelle entfernt
- Capo-Stepper jetzt im `SongEditorSheet` (BottomSheet): "Capo" Label links, "−" / Wert / "+" rechts
- Tipp auf `−`/`+` ruft `vm.updateCapo()` auf — sofortige DB-Persistierung

### UI-Korrektur Mini-Player
- Mini-Player verschoben: liegt jetzt zwingend UNTERHALB der Tab-Navigation
- Reihenfolge in `MainScreen`: TopBar → Box(TabContent) → Row(TabBar) → MiniPlayer

### Loop-Bugfix: Gapless (AudioEngine.kt)
- `activateLoop()`: ClippingConfiguration + 64 geketttete MediaItems + `REPEAT_MODE_ALL`
- ExoPlayer puffert nächsten Playlist-Item während Wiedergabe → keine hörbaren Lücken
- BPM-Präzision: `beatMs = 60_000.0 / bpm` (Double), `floor(pos / beatMs).toLong()`, `roundToLong()` für Start/End
- `positionMs`: bei aktivem Loop wird `loopStartMs + raw` zurückgegeben (Mini-Player Countdown korrekt)
- `durationMs`: gibt `originalDurationMs` zurück wenn Loop aktiv
- `deactivateLoop()`: stellt Original-MediaItem wieder her, setzt `seekTo(loopStartMs + currentClipPos)`
- `tickLoop()`: no-op (Playlist übernimmt Übergänge)

### Auto-Stop Bugfix
- `PlayerViewModel`: Auto-Stop-Erkennung bekommt Guard `&& !_loopActive.value`
- Verhindert Fehlauslösung wenn Loop aktiv ist und Position bei jedem Clip-Wechsel zurückspringt

---

## [Sprint 5.4 — Archiv-Management]

**Datum:** 2026-06-20
**Branch:** main

### Row-Actions (Tab A)
- `ArchivSongRow`: Stift-Icon (Bearbeiten) + Mülleimer-Icon (Löschen) am rechten Rand jeder Zeile
- Icons werden im Batch-/Selektionsmodus ausgeblendet (kein Konflikt mit Mehrfachauswahl)
- Langer Druck aktiviert weiterhin den Batch-Modus (unverändert)

### Einzel-Löschen
- Klick auf Mülleimer öffnet `AlertDialog` ("Song wirklich löschen?")
- Bei Bestätigung: `SongDao.delete()` + reaktive DB-Aktualisierung via Flow
- Wenn gelöschter Song aktuell spielt: Wiedergabe stoppt automatisch

### Editor-Navigation
- `SongEditorSheet`: Vor/Zurück-Buttons (`ChevronLeft`/`ChevronRight`) im Header
- Positionsanzeige "X / N" (z.B. "3 / 12")
- Navigation innerhalb der gefilterten Song-Liste — keine ungespeicherten Änderungen werden übertragen
- Doppel-Long-Press zum Öffnen des Editors entfernt; Editor öffnet sich ausschließlich via Stift-Icon

### Hamburger-Menü (nur Tab A)
- Neues Hamburger-Icon (`Icons.Filled.Menu`) in der Kopfzeile
- **Sicherheitsregel eingehalten:** Icon nur sichtbar wenn `selectedTab == 0` (Tab A)
- In Tab B vollständig ausgeblendet — kein Code-Pfad erreichbar
- `DropdownMenu` mit Option "Alle Songs löschen"

### Alle Songs löschen
- `SongDao.deleteAll()` (neues Query)
- `PlayerViewModel.deleteAllSongs()`: DB leeren + Wiedergabe stoppen
- Strenger `AlertDialog`: "Wirklich ALLE Songs aus dem Archiv löschen? Diese Aktion kann nicht rückgängig gemacht werden."
- Nur über Hamburger-Menü → Tab A erreichbar

---

## [Sprint 5.3 — Loop + Auto-Stop]

**Datum:** 2026-06-20
**Branch:** main
**CI:** grün

### A/B Loop (taktsynchron)

- `AudioEngine.kt`: `activateLoop(bpmExact, bars=8)` berechnet `loopStartMs` via snap-to-beat (floor-Division), `loopEndMs = loopStart + beatMs*4*bars`
- `AudioEngine.kt`: `tickLoop()` wird alle 200ms aus dem ViewModel-Polling aufgerufen — bei Überschreiten von `loopEndMs` → `seekTo(loopStartMs)` auf ALLEN aktiven ExoPlayern synchron
- `AudioEngine.kt`: `deactivateLoop()` setzt `loopActive = false`

### Auto-Stop

- `Song.kt`: neues Feld `autoStop: Boolean = false`
- `AppDatabase.kt`: Version 7 → 8, `MIGRATION_7_8` (`ALTER TABLE songs ADD COLUMN autoStop INTEGER NOT NULL DEFAULT 0`)
- `PlayerViewModel.kt`: Polling-Loop erkennt Rückwärtssprung der Position (ExoPlayer REPEAT_MODE_ONE) → automatisches Pause + Seek-to-0 wenn `autoStop == true`

### UI

- `PlayerViewModel.kt`: `_loopActive: MutableStateFlow<Boolean>`, `toggleLoop()`, `updateAutoStop()`
- `MainScreen.kt`: LOOP-Button in `StageTransport` leuchtet Volt wenn `loopActive`, ansonsten Weiß
- `MainScreen.kt`: Auto-Stop Switch im `SongEditorSheet` (Switch mit Volt-Track, direkte Callback-Weitergabe)
- Loop wird bei `selectSong()` / `skipNext()` / Song-Ende automatisch deaktiviert

---

## [Sprint 5.2 — Security Audit Tab B] — Bühnen-Schutz verifiziert

**Datum:** 2026-06-20
**Branch:** main
**CI:** grün

### Sicherheits-Audit Tab B (Playlist) — Code-Verifikation

Alle 4 Sicherheits-Anforderungen wurden im Kotlin-Code verifiziert.
Kein Code-Eingriff nötig — Implementierung war korrekt.

#### 1. Swipe-Navigation vollständig deaktiviert
Nachweis: MainScreen.kt — Tab-Switching via `when (selectedTab)`.
Kein `HorizontalPager`, kein `ViewPager2`, keine `detectHorizontalDragGestures`
auf Tab-Ebene. Navigation nur über Tab-Buttons (64dp).

#### 2. Set-Akkordeon korrekt implementiert
Nachweis: `PlaylistTab()` mit `expandedId: Long?`-State.
`SetHeader()` mit `ExpandMore/ExpandLess`-Icon.
Nur ein Set gleichzeitig geöffnet (expand-one-Logik).

#### 3. 7-Song-Limit + 72dp Zeilenhöhe erzwungen
Nachweis (Zeile 569): `setSongs.take(7).forEachIndexed { ... }`
Nachweis (Zeile 588): `Modifier.fillMaxWidth().height(72.dp)`
Überzählige Songs: Hinweistext "+N weitere Songs" (kein Scroll).

#### 4. Strikter Edit-Schutz — keine Tastatur möglich
Nachweis `StageSongRow()`: ausschließlich `clickable {}` — kein
`BasicTextField`, kein `KeyboardOptions`, kein `ImeAction`, kein `FocusRequester`.
Kapo-Wert: statischer Text `"Capo $n"` bzw. `"Kein Capo"` (Zeile 601).
`BasicTextField` + `ImeAction` existieren NUR in `ArchivSongRow` (Tab A).

---

## [Sprint 5.2] — Stabiler Startpunkt (ExoPlayer 1.3.1)

**Datum:** 2026-06-20
**Branch:** main
**CI:** grün

### Stabiler Basis-Stack
- Media3 ExoPlayer **1.3.1** (einzige je verwendete Version)
- Room Database **Version 7**
- Jetpack Compose / Material3
- Android SDK 34, minSdk 26

---

### Import-Bugfixes (FolderImporter.kt)

#### Bug 1 — Schleifen-Fehler Modus A (6 Einträge statt 1)
**Problem:** Wenn der Nutzer einen Song-Ordner direkt auswählt (statt des
übergeordneten Ordners), enthält der Root keine Unterordner, aber 6 WAV-Stems.
Modus B lief dann für jede WAV-Datei einzeln → 6 separate DB-Einträge.

**Fix:** Erkennung: `folders.isEmpty() && wavs.size > 1` → Root wird als
einzelner Modus-A-Song behandelt. Zentraler Importer `importModeAWavs()`
schreibt exakt **1 DB-Eintrag pro Ordner**, unabhängig von der Stem-Anzahl.

Betroffene Datei: `app/src/main/java/de/livegigplayer/pro/audio/FolderImporter.kt`
Zeilen: 30–39 (Root-Erkennung), 66–85 (importModeAWavs)

#### Bug 2 — Click-Track-Erkennung case-sensitiv
**Problem:** `click.wav` wurde nicht erkannt wenn Großschreibung abwich
(z.B. `CLICK.WAV`, `Click.wav`). Außerdem fehlte das deutsche "klick".

**Fix:** `f.name?.lowercase()` + String-Containment:
`"click" in n || "klick" in n` — deckt alle Varianten ab.

Betroffene Datei: `app/src/main/java/de/livegigplayer/pro/audio/FolderImporter.kt`
Zeilen: 73–76

---

### Neue Features (Sprint 5.2)

- **Zwei-Tab-Layout:** Archiv (Sofa/Pflege) + Playlist (Bühne), kein Swipe
- **Mini-Player (96dp):** Countdown, Nächster-Song-Vorschau, roter Not-Aus-Button
- **Set-Akkordeon:** Playlists als aufklappbare Sets im Playlist-Tab
- **Stage-Schutz:** Kein Kapo-Edit, keine Tastatur im Playlist-Tab
- **StageTraxx-Queue:** Swipe rechts = Play Next, Swipe links = Play at End
- **Song-Editor BottomSheet:** Titel, Künstler, BPM manuell ändern
- **Batch-Genre-Stempel:** Long-Press → Mehrfachauswahl → Genre zuweisen

---

### Bekannte Lücken / TODO

- LOOP-Button ist aktuell Dummy (onClick = {})
- Set-Verwaltung UI fehlt (Sets können nicht angelegt/umbenannt werden)
- Song-zu-Set-Zuweisung im UI fehlt (playlistId nur per Import gesetzt)
- Auto-Stop vs. Continuous noch nicht implementiert
