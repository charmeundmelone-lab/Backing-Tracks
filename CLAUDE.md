# Live-Gig-Player Pro — Projektkontext für Claude

## Branch-Regel (WICHTIG)

**Einziger erlaubter Branch: `main`**

Kein Feature-Branching. Alle Commits direkt auf `main`.

Vor dem Start immer prüfen:
```bash
git branch   # muss "* main" zeigen
```

Falls falscher Branch: `git checkout main`

## CI-Check — PFLICHT vor jedem Weitermachen

Vor neuem Code immer den letzten GitHub-Actions-Build prüfen.
Grüner Build → weiter. Roter Build → zuerst fixen.

## Kontext-Monitoring

- **< 40.000 Tokens übrig:** User informieren, neue Session starten
- **< 20.000 Tokens übrig:** Sofort stoppen, alles committen & pushen

## Überblick

Android-App für Live-Musiker: spielt Backing-Tracks ab, verwaltet Songs
und Playlists mit Room-Datenbank. Smartphone-First, dunkles UI für die Bühne.

**Repo:** `charmeundmelone-lab/Backing-Tracks`
**Branch:** `main`
**Package:** `de.livegigplayer.pro`

## Architektur

```
app/src/main/java/de/livegigplayer/pro/
├── audio/
│   ├── AudioEngine.kt    — ExoPlayer-Wrapper: load, play, pause, seekTo, loop, preload
│   ├── FolderImporter.kt — SAF-Import: Modus A (WAV-Stems), Modus B (Legacy)
│   └── SongScanner.kt    — erkennt TrackMode aus DocumentFile-Struktur
├── data/
│   ├── Song.kt           — Room-Entity v8 (id, title, artist, bpm, bpmExact, keySignature,
│   │                        genre, capoPosition, volDrums/Bass/Keys/Vocals/Click/Cue,
│   │                        autoStop, playlistId, audioFilePath, duration)
│   ├── SongDao.kt        — CRUD + resetAllMixerSettings
│   ├── Playlist.kt       — Room-Entity (id, name, isLiveLocked)
│   ├── PlaylistDao.kt    — getAllPlaylists
│   ├── AppDatabase.kt    — RoomDatabase v8, Migrationen 1→2, 5→6, 6→7, 7→8
│   └── TrackMode.kt      — sealed class: Legacy(filePath) | Multitrack(drums,bass,keys,vocals,click,cue)
├── ui/
│   ├── MainScreen.kt     — Compose-UI: zwei Tabs (Archiv / Playlist), Mini-Player, Mixer
│   └── PlayerViewModel.kt — AndroidViewModel: StateFlow, Queue, Loop, AutoStop
├── ui/theme/
│   └── Theme.kt          — LiveGigPlayerTheme (dark)
├── LiveGigPlayerApp.kt   — Application-Klasse, DB-Singleton
└── MainActivity.kt       — Entry Point, Compose-Setup
```

## Datenmodell Song (Room v8)

| Feld | Typ | Bedeutung |
|---|---|---|
| id | Long (PK) | Auto-generiert |
| title | String | Songtitel |
| artist | String | Künstler |
| bpm | Int | Tempo (ganzzahlig) |
| bpmExact | Float | Tempo (präzise, 0 = nicht gesetzt) |
| keySignature | String | Tonart |
| genre | String | Genre |
| capoPosition | Int | Kapo 0–11 |
| volDrums/Bass/Keys/Vocals/Click/Cue | Float | Mixer-Lautstärke in dB |
| autoStop | Boolean | Song stoppt automatisch am Ende |
| playlistId | Long | Zugehöriges Set (0 = keins) |
| audioFilePath | String | SAF-Pfad (treeUri||folderName) |
| duration | String | Anzeigedauer (z.B. "3:42") |

## Build-Setup

```bash
./build_apk.sh          # Debug-APK bauen
# APK liegt dann unter: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

CI baut automatisch bei jedem Push auf `main` und legt APK auf `apk-dist`:
```bash
git fetch origin apk-dist
git show origin/apk-dist:LiveGigPlayer-debug.apk > /tmp/LiveGigPlayer.apk
```

## Wichtige Gotchas

1. **Room v8** — nächste Migration wäre 8→9. Migrationen NIE doppelt anlegen.
2. **ExoPlayer REPEAT_MODE_ONE** — Song loopt endlos, STATE_ENDED wird nie gefeuert. Auto-Stop via Rückwärtssprung-Erkennung im 200ms-Polling.
3. **Loop-Sync** — `tickLoop()` ruft `seekTo()` auf, das alle ExoPlayer in `tracks` iteriert → inhärent synchron.
4. **SAF-Pfadformat** — `"{treeUri}||{folderName}"`, aufgelöst via `DocumentFile.fromTreeUri`.
5. **versionCode** kommt aus der CI-Build-Nummer (`-PversionCode=${{ github.run_number }}`). Lokaler Build → 1.

## Letzter Stand

**Datum:** 2026-06-21
**CI Build:** #163 — grün
**Commit:** `f16eae1` — Sprint 5.11 Loop-Editor UX/Performance-Update

### Abgeschlossene Sprints

- **Sprint 5.11 DONE:** Loop-Editor vollständig überarbeitet: AuditionPlayer im ViewModel, Waveform-Cache (CacheDir), Initialzoom ~12s, Tap-to-Create State-Machine, Scaffold+Snackbar "Loop gespeichert", neue Signatur `LoopEditorScreen(song, vm, onClose)`
- **Sprint 5.10 DONE:** Loop-Editor Interaktion: scale/offsetX als MutableState, Single-Canvas-Ansatz (kein graphicsLayer), UX-Regel A (Kollisionsvermeidung 100ms), UX-Regel B (Slip-Editing), Fine-Tune ±10ms, X=Abbrechen / ✓=Speichern
- **Sprint 5.9 DONE:** WaveformAnalyzer dataChunkSize-Fix (schwarze Waveform behoben), AuditionPlayer-Klasse, VORHÖR/STOP-Button, LOOP-Button 3-Zustände (aktiv/armed/disabled), safeDrawingPadding
- **Sprint 5.3 DONE:** A/B-Loop (snap-to-beat, 8 Takte, alle Stems synchron), Auto-Stop (DB v8, Switch im Editor), LOOP-Button leuchtet Volt wenn aktiv
- **Sprint 5.2 DONE:** Zwei-Tab-Layout (Archiv/Playlist), Mini-Player 96dp, Set-Akkordeon, StageTraxx-Queue, Import-Bugfixes (Modus A Einzel-Eintrag, Click case-insensitiv), Tab-B-Sicherheits-Audit
- **Sprint 5.1 DONE:** ArchivSongRow (combinedClickable, Kapo-Stepper, Inline-Edit, Batch-Modus, GenreBar)
- **Sprint 5 DONE:** ExoPlayer 1.3.1, Multitrack-Support, Mixer, Preload

### Wichtige Architektur-Details (Sprint 5.9–5.11)

- **AuditionPlayer** (`audio/AuditionPlayer.kt`) — separater ExoPlayer für Loop-Vorschau, 64× ClippingConfiguration + REPEAT_MODE_ALL
- **AuditionPlayer im ViewModel** — `PlayerViewModel.toggleAudition()`, `refreshAuditionLoop()`, `stopAudition()`, `isAuditioning: StateFlow<Boolean>`
- **WaveformAnalyzer Cache** — `saveCache(ctx, songId, data)` / `loadCache(ctx, songId)` → `cacheDir/waveform_{id}.bin`
- **LoopEditorScreen** — Tap-to-Create: `firstTapMs: Long = -1` State-Machine; wenn kein gültiger Loop → Tap 1 = Start, Tap 2 = Ende
- **graphicsLayer** — NICHT verfügbar als Import in dieser Compose-Version → Single-Canvas mit `msToScreen()` Koordinatentransformation

### Offene TODOs (nächste Session)

- Set-Verwaltung UI: Sets anlegen / umbenennen
- Song-zu-Set-Zuweisung im UI (aktuell nur per Import)
- Playlist-Tab: Queue-Swipe (Swipe rechts/links auf Stage-Songs)
- Loop-Editor in der App testen (Tap-to-Create, Vorhör, Snackbar)
