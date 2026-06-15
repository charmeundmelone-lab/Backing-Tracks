# MiniTraxx — Projektkontext für Claude

## CLAUDE.md pflegen — PFLICHT bei jeder Session

**Wenn du „Letzter Stand" aktualisierst, MUSST du diese vier Felder synchron halten:**

| Feld | Wo | Aktueller Wert |
|---|---|---|
| Aktiver Branch | Überblick + Session-Übergang + **`.claude/active-branch`** | `claude/claude-md-review-052dfg` |
| DB-Version | Architektur-Kommentar + Gotcha #4 | Version 6 |
| Letzter Commit | Letzter Stand | `(folgt nach Push)` |
| Nächste Migration | Gotcha #4 | nächste wäre 6→7 |

**Hinweis:** `android-build.yml` ist seit 2026-06-15 branch-agnostisch (`if: github.ref != 'refs/heads/apk-dist'`).
Bei Branch-Wechseln muss der Workflow **nicht mehr** angepasst werden.

**Branch-Wechsel — was zu tun ist (NUR diese zwei Schritte):**
1. `.claude/active-branch` → neuen Branch-Namen eintragen und pushen
2. Diese Tabelle hier → Branch-Zeile aktualisieren

Das war's. Der SessionStart-Hook liest `.claude/active-branch` und checkt automatisch den richtigen Branch aus.

**Regel:** Vor dem Commit von CLAUDE.md immer alle vier Zeilen prüfen.
Diese Tabelle ist die einzige Quelle der Wahrheit — sie schlägt alle anderen Stellen.

## Branch-Verifikation — ALLERERSTER SCHRITT

**Bevor du irgendetwas tust: Führe `git branch` aus und prüfe den aktiven Branch.**

```
Erwarteter Branch: claude/claude-md-review-052dfg
```

- Stimmt der Branch? → Weiter mit CI-Check
- Falscher Branch? → `git checkout claude/claude-md-review-052dfg` ausführen, dann weiter
- Branch existiert nicht lokal? → `git fetch origin && git checkout claude/claude-md-review-052dfg`

**Niemals Code committen oder pushen ohne diesen Check. Niemals.**

## CI-Check — PFLICHT vor jedem Weitermachen

**Jede neue Session MUSS zuerst den letzten CI-Build prüfen, bevor Code geschrieben wird.**

Ablauf:
1. GitHub Actions für Branch `claude/claude-md-review-052dfg` abfragen
2. Ist der letzte Build **grün** → weiter wie geplant
3. Ist der letzte Build **rot** → zuerst den Fehler aus den Logs lesen, fixen, pushen, warten bis grün — erst dann das eigentliche Feature anfangen

Kein grünes Licht = kein neuer Code.

---

## Kontext-Monitoring (WICHTIG — immer beachten)

Du siehst in deinem System-Prompt wie viele Tokens noch übrig sind
(`totalTokensReminder: countdown`). Handle danach:

- **< 40.000 Tokens übrig:** Sag dem User aktiv: "Kontext läuft voll —
  bitte starte nach diesem Task eine neue Session. CLAUDE.md enthält alles
  was du brauchst."
- **< 20.000 Tokens übrig:** Sofort stoppen, nichts mehr implementieren.
  Stattdessen: aktuellen Stand committen & pushen, dann dem User sagen er
  soll jetzt eine neue Session starten.

**Nahtloser Session-Übergang:**
1. Alle Änderungen committen & auf `claude/claude-md-review-052dfg` pushen
2. User sagt Claude in neuer Session: *"Lies CLAUDE.md und mach weiter."*
3. Neue Claude-Instanz liest CLAUDE.md → hat vollen Kontext → kein Warmup nötig

---

## Überblick

Android-App für Musiker: spielt mehrkanalige Backing-Tracks (Stems) ab,
zeigt ChordPro-Lyrics mit automatischem Scroll (Tap-Once-Sync), verwaltet
Setlisten und Songs mit Room-Datenbank.

**Repo:** `charmeundmelone-lab/Backing-Tracks`
**Aktiver Branch:** `claude/claude-md-review-052dfg`
**APK-Dist Branch:** `apk-dist` (wird per CI bei jedem Push gebaut)

## Architektur

```
app/src/main/java/de/minitraxx/app/
├── audio/
│   ├── NativeEngine.kt        — Kotlin-Singleton, JNI-Brücke zum C++-Audio-Engine
│   ├── PlaybackController.kt  — Setlist-Queue, Songwechsel-Logik, State (StateFlow)
│   └── PlaybackService.kt     — Foreground-Service für Hintergrundwiedergabe
├── data/
│   ├── AppDatabase.kt         — Room DB (Version 6)
│   ├── GigRepository.kt       — Gig starten/beenden, Play aufzeichnen, Flows
│   ├── SettingsStore.kt       — DataStore: mainGain, cueGain, swapSides, lyricsFontSp, syncOffsetMs
│   ├── SongRepository.kt      — Zugriff auf Songs, Stems, Setlisten
│   └── Slots.kt               — Stem-Slot-Konstanten (TOTAL = 8)
├── ui/screens/
│   ├── LiveScreen.kt          — Live-Ansicht (der hauptsächlich bearbeitete Screen)
│   └── ...                    — weitere Screens (Setup, Song-Editor, etc.)
└── util/
    ├── ChordPro.kt            — Parser für ChordPro-Format ({section:}, {c:}, Lyrics)
    └── formatFrames.kt        — Zeitformatierung
```

## NativeEngine API (wichtig)

```kotlin
object NativeEngine {
    fun positionFrames(): Long   // aktuelle Position in Audio-Frames (48 kHz)
    fun isPlaying(): Boolean
    fun isFinished(): Boolean
    fun clearFinished()
    fun hadStreamError(): Boolean
    fun play()
    fun pause()
    fun stop()
    fun seek(frame: Long)
    fun start()                  // Stream (neu) öffnen
    fun loadSong(paths: Array<String?>, gains: FloatArray): Long  // → durationFrames
    fun unloadSong()
    fun setBusGains(main: Float, cue: Float)
    fun setSwapSides(swap: Boolean)
    const val SAMPLE_RATE = 48_000
}
```

## PlayerState / PlaybackController

```kotlin
data class PlayerState(
    val setlistId: Long,
    val setlistName: String,
    val queue: List<QueueSong>,
    val currentIndex: Int,
    val positionFrames: Long,   // aktualisiert alle 100ms per Tick
    val durationFrames: Long,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val error: String?,
)
data class QueueSong(
    val songId: Long, val title: String, val artist: String,
    val durationFrames: Long, val endAction: Int, val notes: String,
    val chordPro: String,
    val syncData: String,  // leerzeichen-getrennte ms-Timestamps; "" wenn kein Sync
    val genre: String,     // Genre / Motto-Preset-Tag; "" wenn nicht gesetzt
)
```

PlaybackController ist ein Singleton (`PlaybackController.get(context)`).
Der Tick-Job läuft alle 100 ms und aktualisiert `_state.positionFrames`.

## Tap-Once-Sync — Feature-Beschreibung

Der Nutzer tippt beim ersten Durchlauf des Songs einmal pro Sektion auf
einen Button. Die Timestamps (ms) werden als `syncData = "1234 5678 ..."` in
der DB gespeichert (Song-Feld).

Im Live-Betrieb scrollt die LyricsPane automatisch:
- **Mit Sync-Daten:** Sektionsbasiert + Innerhalb-Sektion interpoliert
- **Ohne Sync-Daten oder im Sync-Modus:** Lineare Position (Fallback)

## LyricsPane — aktueller Scroll-Code (LiveScreen.kt ~692)

```kotlin
// Lineare Positionskopplung (Fallback ohne Sync oder im Sync-Modus).
LaunchedEffect(positionFrames, durationFrames, isPlaying) {
    if (isPlaying && durationFrames > 0 && (syncTimestamps.isEmpty() || isSyncMode)) {
        val targetIdx = ((positionFrames.toDouble() / durationFrames) * lines.size)
            .toInt().coerceIn(0, lines.size)
        lazyState.scrollToItem(targetIdx)
    }
}

// Sektionsbasiertes Scrollen mit Innerhalb-Sektion-Interpolation.
// isPlaying ist KEIN Key — kurzes Flackern würde lastTargetItem resetten.
LaunchedEffect(syncTimestamps, syncOffsetMs, sectionToItemIndex, isSyncMode, durationFrames) {
    if (syncTimestamps.isEmpty() || isSyncMode || durationFrames <= 0) return@LaunchedEffect
    val durationMs = durationFrames * 1000L / 48_000L
    var lastTargetItem = -1
    while (true) {
        delay(100)
        val posMs = NativeEngine.positionFrames() * 1000L / 48_000L
        val sec = syncTimestamps.indexOfLast { ts -> posMs >= ts - syncOffsetMs }
        val targetItem = if (sec < 0) {
            0
        } else {
            val secStart = syncTimestamps[sec]
            val secEnd = syncTimestamps.getOrNull(sec + 1) ?: durationMs
            val firstItem = sectionToItemIndex[sec] ?: 0
            val nextFirst = sectionToItemIndex[sec + 1] ?: (lines.size + 1)
            val t = if (secEnd > secStart)
                ((posMs - secStart).toDouble() / (secEnd - secStart)).coerceIn(0.0, 1.0)
            else 0.0
            (firstItem + (nextFirst - firstItem) * t).toInt()
        }
        if (targetItem != lastTargetItem) {
            lastTargetItem = targetItem
            lazyState.scrollToItem(targetItem)
        }
    }
}
```

**Warum `isPlaying` KEIN Key ist:**
`NativeEngine.isPlaying()` kann im PlaybackController-Tick kurz `false` melden
(Race-Condition), was den LaunchedEffect neu startet, `lastTargetItem` resettet
und die Schleife immer bei Sektion 0 festhält. Deshalb pollen wir NativeEngine
direkt im `while(true)`-Loop ohne `isPlaying` als Key.

## SyncButton-Verhalten

- **Kurz-Tap ohne Sync-Daten:** Startet Sync-Modus
- **Kurz-Tap mit Sync-Daten:** Nichts (kein Re-Sync durch Versehen)
- **Lang-Tap ohne Sync-Modus:** Startet Re-Sync (Daten überschreiben)
- **Lang-Tap im Sync-Modus:** Öffnet Offset-Sheet (syncOffsetMs)
- **Im Sync-Modus Kurz-Tap:** Bricht Sync ab
- Sync-Modus endet automatisch beim Songwechsel

## CI / APK-Dist

GitHub Actions baut bei jedem Push (außer auf `apk-dist` selbst) eine
Debug-APK und pusht sie auf den Branch `apk-dist` als `MiniTraxx-debug.apk`.

So APK holen:
```bash
git fetch origin apk-dist
git show origin/apk-dist:MiniTraxx-debug.apk > /tmp/MiniTraxx.apk
```

## Wichtige Gotchas

1. **`delay` muss importiert werden:** `import kotlinx.coroutines.delay`
   (andernfalls Compile-Fehler; `kotlinx.coroutines.delay(...)` fully-qualified geht auch)

2. **LazyColumn-Item-Indizes:** Item 0 = führender Spacer, Items 1..N = Lines, Item N+1 = Spacer.
   `sectionToItemIndex[s] = lineIndex + 1` (wegen Spacer).

3. **`sectionToItemIndex`** ist `Map<Int, Int>` (sectionIndex → lazyColumnItemIndex).
   Zugriff mit `[key]` gibt `null` zurück wenn nicht vorhanden — immer `?: fallback` nutzen.

4. **Room DB Version 6** — nächste Migration wäre 6→7. Migrationen 1→2 bis 5→6 existieren bereits in `AppDatabase.kt` — nie doppelt anlegen.

5. **Stems-Slots:** `Slots.TOTAL = 8`. Stems werden per Slot-Index den Audio-Engine-Kanälen zugeordnet.

6. **syncOffsetMs** (Standard 200 ms): Reaktionszeit-Korrektur — Timestamps werden um diesen
   Wert nach vorne verschoben, damit der Scroll die gefühlte Tipp-Verzögerung ausgleicht.

7. **versionCode kommt aus der CI-Build-Nummer** (`-PversionCode=${{ github.run_number }}`).
   NIE wieder fest auf 1 setzen — sonst installiert sich eine neue APK auf dem Gerät NICHT
   als Update und der User sieht „keine neuen Features", obwohl sie gebaut sind.
   Lokaler Build ohne Property → versionCode 1.

8. **Flüssiges Auto-Scroll:** `smoothScrollTo()` nutzt `scrollToItem(index, offsetPx)` mit
   Sub-Item-Pixel-Offset (Nachkommaanteil der Item-Position → Pixel). NIE nur `.toInt()`
   auf den Item-Index — das lässt die Liste zeilenweise springen (ruckelig). Polling ~30 fps
   (`SCROLL_FRAME_MS = 32`), `isPlaying` im Loop pollen statt als LaunchedEffect-Key.

9. **APK vor dem Ausliefern verifizieren:** APK ist ein ZIP — Roh-`grep` auf der `.apk`
   findet Strings NICHT. Stattdessen `classes*.dex` entpacken und `strings classes*.dex
   | grep <Feature>`. So sicherstellen, dass das gewünschte Feature wirklich im Build ist.

## Offene / geplante Features

- **Freeform-Sync:** Sektions-Buttons in beliebiger Reihenfolge antippen während Sync
- **Quick-Add-Section:** Neue Sektion während Sync hinzufügen (nicht im ChordPro vorhanden)
- **Zoom während Sync-Wiedergabe:** Scroll-Geschwindigkeit anpassen
- **PDF-Import:** Akkorde über Text korrekt positionieren

## Letzter Stand (Session vom 2026-06-15)

Branch `claude/claude-md-review-052dfg` — Genre-Dropdown auf feste Liste umgebaut.

**Was neu ist:**
- `SongEditorScreen.kt`: Genre-Feld ist jetzt ein read-only `ExposedDropdownMenuBox`
  mit fester Liste: `["", "Rock", "Pop", "Jazz", "Schlager", "Latin"]`
- Leeres Element zeigt `"— kein Genre —"` und setzt das Feld zurück
- `observeAllGenres()` wird nicht mehr im UI genutzt (DAO-Methode bleibt erhalten)

**Aktiver Branch:** `claude/claude-md-review-052dfg`
**Letzter Commit:** `(folgt nach Push)`

## Nächste Aufgabe (für neue Session)

Keine konkrete Aufgabe offen — mit dem User besprechen was als nächstes kommt.

Davor (Session 2026-06-14):
- DB Version 6: `genre`-Feld an Songs, neue Tabellen `gigs` + `gig_plays`
- `GigRepository`: Gig starten/beenden, Play aufzeichnen, reaktive Flows
- `PlaybackController`: 60-Sek-akkumuliertes-Tracking per Songwechsel-reset
- `SongEditorScreen`: Genre-Feld mit Autocomplete
- `LiveScreen` Bottom Sheet: morphender Gig-Button (2s Long-Press beendet),
  Genre-FilterChips, ausgegraute Songs mit ✓/2×-Badge

Davor (Session 2026-06-13):
- Innerhalb-Sektion-Scroll (Commit `ca97612`)
- SyncButton Re-Sync-Schutz, Auto-Save Sync-Daten
