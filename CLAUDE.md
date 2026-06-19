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
├── data/
│   ├── Song.kt           — Room-Entity (id, title, bpm, timeSignature, playlistId, isCompleted, audioFilePath)
│   ├── SongDao.kt        — CRUD: insert, update, delete, getAllSongs, getSongById, getSongsByPlaylist
│   └── AppDatabase.kt    — RoomDatabase v1, Singleton
├── ui/theme/
│   └── Theme.kt          — LiveGigPlayerTheme (dark/light)
├── LiveGigPlayerApp.kt   — Application-Klasse, DB-Singleton
└── MainActivity.kt       — Entry Point, Compose-Setup
```

## Datenmodell Song

| Feld | Typ | Bedeutung |
|---|---|---|
| id | Long (PK) | Auto-generiert |
| title | String | Songtitel |
| bpm | Int | Tempo |
| timeSignature | String | "4/4" oder "6/8" |
| playlistId | Long | Zugehörige Playlist |
| isCompleted | Boolean | Abgehakt (Default: false) |
| audioFilePath | String | Pfad zur Audio-Datei |

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

1. **Room-Version 1** — nächste Migration wäre 1→2. Migrationen NIE doppelt anlegen.
2. **timeSignature** ist ein String ("4/4"/"6/8"), kein Enum — Flexibilität für spätere Werte.
3. **versionCode** kommt aus der CI-Build-Nummer (`-PversionCode=${{ github.run_number }}`). Lokaler Build → 1.

## Offene / geplante Features (Roadmap)

- **Sprint 1 DONE:** Room DB, Gradle-Setup, build_apk.sh
- **Sprint 2:** Playlist-Entity + DAO, Playlist-Verwaltungs-UI
- **Sprint 3:** Song-Editor-UI (Felder eingeben, Audio-Datei wählen)
- **Sprint 4:** Player-Screen (Audio abspielen, Fortschritt, BPM-Anzeige)
- **Sprint 5:** Live-Ansicht (Bühnen-optimiertes Dark UI, blind bedienbar)

## Letzter Stand

**Sprint 1 abgeschlossen** — Room DB, Gradle, build_apk.sh, CI eingerichtet.
**Branch:** `main`
