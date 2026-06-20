# CHANGELOG — Live-Gig-Player Pro

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
