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

**Datum:** 2026-06-23
**CI Build:** #211 ✅ — grün
**Branch:** `main`
**Commit:** Fix Self-Move & Race Conditions in GigViewModel/SetDao

### Sprint 5.21 DONE: Fix Self-Move & Race Conditions (CI #211)

- SetDao: `interface` → `abstract class` für echte `@Transaction open suspend fun`
- `existsInSet()`: prüft ob Song bereits im Set ist (keine REPLACE-Nebenwirkung)
- `moveSongSpontaneous()`: updatet NUR `positionInSet` + `isSpontaneous=1`,
  `isCompleted` bleibt zwingend erhalten
- `moveSpontaneousNext()` / `moveSpontaneousLater()`: `@Transaction` — gesamte
  Kette (Shift, Move/Insert, Sanitize) läuft atomar in einer DB-Transaktion
- `applyMoveSpontaneous()`: Song selbst beim Shift ausgelassen (`id != songId`)
- GigViewModel: `insertSpontaneousNext/Later` delegieren komplett an DAO

### Sprint 5.20 DONE: Auto-Arm, Set-Tab-Swipes, Toast-Feedback (CI #208)

- `GigViewModel.armSetIfIdle()`: beim Öffnen einer Set-Karte wird der erste
  ungespielte Song automatisch in den Player geladen (kein Auto-Play, nur Armed)
  → `currentSong != null` → Archiv-Swipes funktionieren ohne manuelles Antippen
- `SetCard`: zweiter `LaunchedEffect(set.setId)` ruft `armSetIfIdle()` auf;
  `currentSong` per `collectAsState()` weitergereicht
- Set-Tab-Swipes: Songs INNERHALB des Sets können geswiped werden:
  - Rechts-Swipe → `insertSpontaneousNext` (sofort nächster, ★ goldgelb)
  - Links-Swipe → `insertSpontaneousLater` (hinter alle ★-Songs, vor erstem regulären)
  - Composite-PK mit REPLACE: verschiebt Song, kein Duplikat
- Toast-Feedback: "★ Titel → nächster" / "★ Titel → später" bei jedem Swipe
  (sowohl Archiv- als auch Set-Tab)

### Sprint 5.19 DONE: Dynamische Playlist mit Block-Insertion (CI #206)

- DB v11→12: `isSpontaneous` in `set_song_cross_ref`
- Rechts-Swipe Archiv-Song → `insertSpontaneousNext` (direkt nach aktuellem Song ins Set)
- Links-Swipe Archiv-Song → `insertSpontaneousLater` (vor erstem regulären Song)
- Queue wird nach DB-Schreibung automatisch neu geladen
- Spontane Songs: goldgelbe Positionsnummer + ⭐ im Set
- Completed Songs tab-übergreifend ausgegraut (40%); Dialog "Heute bereits gespielt?"

### Sprint 5.17 DONE: Positionsfix, Matching-UI, Loop-READY-State (CI #198)

- `sanitizeSetPositions(setId)` in GigViewModel: renummeriert beim Öffnen
  eines Sets alle Songs von 0 aufsteigend (kein 01,01,02,02 mehr)
- `SetSongRow` jetzt 1:1 wie `ArchivSongRow`: 72dp, 24sp Volt-Nummer (44dp),
  15sp Bold White Titel, 11sp Gray Subtitle "artist · bpm | duration"
- Loop-Auto-Aktivierung entfernt: Song lädt Punkte vor (loopStartMs/loopEndMs),
  aber `_loopState` bleibt INACTIVE — User muss LOOP drücken
- LOOP-Button zeigt "READY" (VoltDim) wenn Punkte vorgeladen, kein Loop aktiv
- LoopPanel erscheint auch bei INACTIVE wenn Punkte vorhanden
- Einmaliger LOOP-Druck bei READY → springt direkt zu LOOPING (kein A_SET)

### Sprint 5.18 DONE: Strikter Schreibschutz im Gig-Set-Modus (CI #199)

- `isGigSetMode: StateFlow<Boolean>` in PlayerViewModel
- `loadSetAsQueue` setzt `isGigSet = true` → bleibt durch skipNext/skipPrevious erhalten
- LOOP-Button im Set: reiner Ein/Aus-Schalter (kein A_SET, kein DB-Schreiben)
  - Kein gespeicherter Loop → Button grau/inaktiv, kein Effekt
  - Gespeicherter Loop vorhanden → READY → Tippen → LOOPING → Tippen → READY
- Guards in: `nudgeLoopStart`, `nudgeLoopEnd`, `setLoopRange`, `saveLoopPoints`,
  `executeHardDatabaseSave`, `clearLoop` — alle `return` im Set-Modus
- LoopPanel im Gig-Set-Modus komplett ausgeblendet

### Gig/Set-Architektur (DB Phase 2, abgeschlossen)

Neue Tabellen in AppDatabase (Migration 2→9, dann Bridge + fallbackToDestructive entfernt bei v10):
- `GigEntity` (gigId, name)
- `SetEntity` (setId, gigOwnerId, name, position)
- `SetSongCrossRef` (setId, songId, positionInSet, isCompleted)
- `SongInSet` = @Embedded Song + positionInSet + completedInSet

Neue Dateien:
- `data/GigEntity.kt`, `data/SetEntity.kt`, `data/SetWithSongs.kt`
- `data/GigDao.kt`, `data/SetDao.kt`
- `ui/GigViewModel.kt`
- `ui/GigManagementScreen.kt`

Einbindung: `GigManagementScreen` im Tab B von MainScreen (neben Archiv).

### Abgeschlossene frühere Sprints

- **Sprint 5.16 ROLLBACK:** Visueller Loop-Editor entfernt (UI-Freeze)
- **Sprint 5.3 DONE:** A/B-Loop, Auto-Stop, LOOP-Button
- **Sprint 5.2 DONE:** Zwei-Tab-Layout, Mini-Player, Set-Akkordeon, Queue
- **Sprint 5.1 DONE:** ArchivSongRow, Kapo-Stepper, Inline-Edit, Batch-Modus
- **Sprint 5 DONE:** ExoPlayer 1.3.1, Multitrack, Mixer, Preload

### Offene TODOs (nächste Session)

- **Set-Umbenennen:** Sets können noch nicht umbenannt werden
- **Song-zu-Set direkt im UI:** Aktuell nur per "Zum Set hinzufügen" Dialog aus Archiv
- **Playlist-Tab Queue-Swipe:** Swipe rechts/links auf Stage-Songs

### Loop-Editor — Archiviert

Der visuelle Waveform-Loop-Editor (LoopEditorScreen / LoopEditorViewModel) ist endgültig
verworfen — das Konzept wird nicht neu aufgegriffen. Die bestehende Lösung
(A/B-Tippen im Player + LoopPanel mit Nudge/Save) ist die finale Implementierung.
