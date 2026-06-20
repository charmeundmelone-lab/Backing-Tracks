# CHANGELOG — Live-Gig-Player Pro

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
