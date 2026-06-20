# CHANGELOG — Live-Gig-Player Pro

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
