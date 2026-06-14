# MiniTraxx — Projektkontext für Claude

## ⛔ APK-AUSLIEFERUNG — HARTE REGEL (zuerst lesen, niemals überspringen)

**Diese Regel wurde eingeführt, weil eine veraltete APK ausgeliefert wurde
und der User stundenlang den falschen Build installiert hatte. Das darf NIE
wieder passieren.**

**Bevor du dem User jemals eine APK schickst, MUSST du ALLE drei Schritte
ausführen:**

```bash
git fetch origin apk-dist -q
git log origin/apk-dist -1 --format='%s'   # muss "build <SHA>" enthalten
git rev-parse HEAD                          # dein letzter gepushter Commit
```

Die `<SHA>` in der apk-dist-Commit-Message MUSS mit `git rev-parse HEAD`
**exakt übereinstimmen**. Stimmt sie nicht → **NICHT senden**.
Warte mit Monitor auf CI und prüfe erneut:

```kotlin
// Monitor-Pattern für CI-Warten:
Monitor(command = """
MY_SHA=$(git rev-parse HEAD)
until git fetch origin apk-dist -q && git log origin/apk-dist -1 --format='%s' | grep -q "$MY_SHA"; do sleep 30; done
echo "BEREIT: $(git log origin/apk-dist -1 --format='%s')"
""", timeout_ms = 600000)
```

Danach APK holen und senden:
```bash
git show origin/apk-dist:MiniTraxx-debug.apk > /tmp/MiniTraxx.apk
```

Der CI-Workflow (`.github/workflows/android-build.yml`) pusht die APK von
**JEDEM Branch** (außer apk-dist selbst) auf `apk-dist`. NICHT auf einzelne
Branch-Namen beschränken — diese Bedingung wurde bereits falsch konfiguriert
und war Ursache des Problems.

---

## Kontext-Monitoring (WICHTIG — immer beachten)

- **< 40.000 Tokens übrig:** User aktiv warnen: "Kontext läuft voll —
  bitte neue Session starten. CLAUDE.md enthält alles."
- **< 20.000 Tokens übrig:** Sofort stoppen, committen & pushen,
  User auffordern neue Session zu starten.

**Nahtloser Session-Übergang:**
1. Alle Änderungen committen & pushen
2. User sagt in neuer Session: *"Lies CLAUDE.md und mach weiter."*
3. Neue Claude-Instanz hat vollen Kontext — kein Warmup nötig.

---

## Überblick

Android-App für Musiker: spielt mehrkanalige Backing-Tracks (Stems) ab,
zeigt ChordPro-Lyrics mit automatischem Scroll (Tap-Once-Sync), verwaltet
Setlisten und Songs mit Room-Datenbank.

**Repo:** `charmeundmelone-lab/Backing-Tracks`
**Aktiver Branch:** `claude/current-apk-disto-y2wzvi`
**APK-Dist Branch:** `apk-dist` (wird per CI bei jedem Push gebaut)
**Letzter Commit:** `de8279d`

## Architektur

```
app/src/main/java/de/minitraxx/app/
├── audio/
│   ├── NativeEngine.kt        — Kotlin-Singleton, JNI-Brücke zum C++-Audio-Engine
│   ├── PlaybackController.kt  — Setlist-Queue, Songwechsel-Logik, State (StateFlow)
│   └── PlaybackService.kt     — Foreground-Service für Hintergrundwiedergabe
├── data/
│   ├── AppDatabase.kt         — Room DB (Version 5)
│   ├── SettingsStore.kt       — DataStore: mainGain, cueGain, swapSides, lyricsFontSp, syncOffsetMs
│   ├── SongRepository.kt      — Zugriff auf Songs, Stems, Setlisten
│   └── Slots.kt               — Stem-Slot-Konstanten (TOTAL = 8)
├── ui/screens/
│   ├── LiveScreen.kt          — Live-Ansicht (LyricsPane, Scroll, SyncButton)
│   ├── SongEditorScreen.kt    — Song-Editor mit "Akkorde ← →" Button
│   ├── ChordEditorDialog.kt   — Chord-Tap-Editor (Akkorde per Tap verschieben)
│   └── ...
└── util/
    ├── ChordPro.kt            — Parser (ChordPro + UG-Plain-Text Autoerkennung)
    ├── PdfChordImporter.kt    — PDF-Import mit koordinatengenauer Akkord-Platzierung
    └── formatFrames.kt        — Zeitformatierung
```

## NativeEngine API

```kotlin
object NativeEngine {
    fun positionFrames(): Long   // aktuelle Position in Audio-Frames (48 kHz)
    fun isPlaying(): Boolean
    fun isFinished(): Boolean
    fun clearFinished()
    fun hadStreamError(): Boolean
    fun play(); fun pause(); fun stop()
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
    val setlistId: Long, val setlistName: String,
    val queue: List<QueueSong>, val currentIndex: Int,
    val positionFrames: Long,   // alle 100ms per Tick aktualisiert
    val durationFrames: Long,
    val isPlaying: Boolean, val isLoading: Boolean, val error: String?,
)
data class QueueSong(
    val songId: Long, val title: String, val artist: String,
    val durationFrames: Long, val endAction: Int, val notes: String,
    val chordPro: String,
    val syncData: String,  // leerzeichen-getrennte ms-Timestamps; "" = kein Sync
)
```

PlaybackController ist Singleton (`PlaybackController.get(context)`).

## LyricsPane — Scroll-Implementierung (LiveScreen.kt ~688)

**Einheitliche Schleife** — ein `LaunchedEffect` deckt beide Modi ab:

```kotlin
LaunchedEffect(syncTimestamps, syncOffsetMs, sectionToItemIndex, isSyncMode, durationFrames) {
    if (durationFrames <= 0) return@LaunchedEffect
    val durationMs = durationFrames * 1000L / 48_000L
    while (true) {
        if (NativeEngine.isPlaying()) {
            withFrameNanos { }  // VSync-sync (60/90/120 Hz)
            val posMs = NativeEngine.positionFrames() * 1000L / 48_000L
            val exactPosition: Double = if (syncTimestamps.isEmpty() || isSyncMode) {
                (posMs.toDouble() / durationMs) * lines.size
            } else {
                // sektionsbasiert mit Interpolation innerhalb der Sektion
                ...
            }
            val targetIndex = exactPosition.toInt().coerceIn(0, lines.size)
            val fraction = exactPosition - targetIndex
            // Exakte Item-Größe verwenden wenn sichtbar (kein avg-Sprung):
            val visInfo = lazyState.layoutInfo.visibleItemsInfo
            val itemPx = visInfo.firstOrNull { it.index == targetIndex }?.size
                ?: visInfo.filter { it.index in 1..lines.size }
                    .map { it.size }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            lazyState.scrollToItem(targetIndex, (fraction * itemPx).toInt())
        } else {
            delay(100)
        }
    }
}
```

**Warum `isPlaying` KEIN Key ist:** Race-Condition im PlaybackController-Tick
kann kurz `false` melden → Effect-Restart → `lastTargetItem`-Reset →
Schleife hängt bei Sektion 0. Deshalb `NativeEngine.isPlaying()` direkt pollen.

## ChordPro-Parser — wichtige Details (ChordPro.kt)

**Zwei Formate** werden automatisch erkannt:
- **ChordPro** (`looksLikeChordPro` = true): `{section:}`, `{c:}`, `[Akkord]text`
- **UG-Plain-Text**: eigene Akkordzeile über Textzeile, `[Verse 1]`-Header

**`parse()`** ruft nach dem Parsen `mergeChordOnlyLines()` auf:
- Akkord-Only-Zeilen (text leer, chords gesetzt) + folgende Text-Only-Zeile
  werden zu EINER Zeile fusioniert.
- Verhindert visuelle Trennung von Akkorden und Lyrics im Renderer.

**`toWords(line)`** konvertiert eine LYRIC-Zeile in `List<List<Piece>>`:
- Range-Matching: Akkord innerhalb W(start,end) → direkt zugewiesen
- Gap-Akkorde: nearest-word-by-distance (nicht mehr "next word after position")
  → robuster bei eingerückten Textzeilen

**`Kind.SECTION`** (neu, neben LYRIC/COMMENT/EMPTY):
- Erzeugt den Section-Divider mit horizontaler Linie im Live-Screen
- `{section: Verse 1}` → Kind.SECTION

## LyricLine-Renderer (LiveScreen.kt ~795)

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLine(line: ChordPro.Line, fontSp: Float) {
    val words = remember(line) { ChordPro.toWords(line) }
    if (words.isEmpty()) { Spacer(Modifier.height((fontSp * 0.5f).dp)); return }
    val hasChord = remember(words) { words.any { w -> w.any { it.chord != null } } }
    FlowRow(Modifier.fillMaxWidth().padding(vertical = (fontSp * 0.14f).dp)) {
        for (word in words) {
            Row(Modifier.padding(end = (fontSp * 0.30f).dp)) {
                for (piece in word) {
                    Column(horizontalAlignment = Alignment.Start) {
                        if (hasChord) {
                            Text(chord_or_space, fontWeight = Bold, color = primary, ...)
                        }
                        // WICHTIG: Kein Text(" ") für leere Pieces!
                        // Standalone-Akkorde (piece.text.isEmpty) rendern keine Textzeile.
                        if (piece.text.isNotEmpty()) {
                            Text(piece.text, ...)
                        }
                    }
                }
            }
        }
    }
}
```

**Warum kein `Text(" ")` für leere Pieces:** Standalone-Akkorde (Intro-Muster
ohne Liedtext) erzeugten sonst eine volle Leerzeile — Akkorde wirkten visuell
von den Lyrics getrennt.

## PdfChordImporter — Architektur

Koordinaten-genauer Import aus PDFs mit echtem Textlayer (UG-Style):
- `collectGlyphs`: liest X/Y-Koordinaten jedes Zeichens per PDFBox
- `groupLines`: gruppiert Glyphen mit ähnlichem Y zu Zeilen
- `tokenize`: zerlegt Zeile in Tokens anhand X-Lücken
- `isChordTokens` / `isSectionHeader` / `isTabLine`: Zeilen-Klassifikation
- `mergeChordLyric`: **Wort-Grenz-Snapping** — jeder Akkord geht zum nächsten
  Wortanfang (minimaler |lyric[i].x - chord.startX|). Erzeugt Inline-ChordPro.
- `chordOnly`: für Akkord-Zeilen ohne Lyrics (Intro-Pattern)
- Ausgabe: Inline-ChordPro mit `[Akkord]text`-Markern + `{section:}`-Direktiven

**Hinweis für exakte Akkordplatzierung:** Wenn Akkorde verschoben wirken,
hilft ein Neu-Import der PDF. Der Importer platziert Akkorde koordinatengenau
über dem nächsten Wortanfang im Original-PDF.

## ChordEditorDialog (ui/screens/ChordEditorDialog.kt)

Tap-to-Move-Editor: User kann Akkorde per Tap auswählen und mit ← → ein Wort
nach links/rechts verschieben. Öffnet über "Akkorde ← →" Button im SongEditor.

## Tap-Once-Sync

- User tippt im Sync-Modus einmal pro Sektion
- Timestamps als `syncData = "1234 5678 ..."` in DB gespeichert
- Scroll: sektionsbasiert + innerhalb Sektion interpoliert

**SyncButton-Verhalten:**
- Kurz-Tap ohne Sync-Daten → Sync-Modus starten
- Kurz-Tap mit Sync-Daten → nichts (Schutz vor versehentlichem Re-Sync)
- Lang-Tap ohne Sync-Modus → Re-Sync (Daten überschreiben)
- Lang-Tap im Sync-Modus → Offset-Sheet (syncOffsetMs)
- Kurz-Tap im Sync-Modus → Sync abbrechen
- **syncOffsetMs** (Standard 200 ms): kompensiert Tipp-Reaktionszeit

## Wichtige Gotchas

1. **`delay` importieren:** `import kotlinx.coroutines.delay`
2. **LazyColumn-Item-Indizes:** Item 0 = Spacer, Items 1..N = Lines, N+1 = Spacer.
   `sectionToItemIndex[s] = lineIndex + 1` (wegen Spacer-Offset).
3. **`sectionToItemIndex`** ist `Map<Int, Int>` — Zugriff gibt `null` zurück → `?: fallback`
4. **Room DB Version 5** — Migration erforderlich bei Schema-Änderungen
5. **Stems-Slots:** `Slots.TOTAL = 8`
6. **`withFrameNanos`** aus `androidx.compose.runtime` importieren
7. **FlowRow** braucht `@OptIn(ExperimentalLayoutApi::class)`

## Aktueller Stand (Session 2026-06-14)

**Branch:** `claude/current-apk-disto-y2wzvi`
**Letzter Commit:** `de8279d`

### In dieser Session behoben:

1. **Akkord-Zeilen-Fusion** (`ChordPro.mergeChordOnlyLines`): Akkord-Only +
   Text-Only-Zeilen werden nach dem Parsen automatisch fusioniert. Vorher saßen
   Akkorde auf einer eigenen Zeile, Liedtext darunter — jetzt korrekt übereinander.

2. **Gap-Akkord-Zuweisung** (`ChordPro.toWords`): Statt "nächstes Wort nach
   der Akkord-Position" jetzt "nächstes Wort nach absolutem Abstand" — robuster
   bei eingerückten Textzeilen und Spaltenversätzen.

3. **Standalone-Chord-Rendering** (`LiveScreen.LyricLine`): Leere Piece-Texte
   rendern keine Leerzeile mehr — verhinderte visuelle Trennung Akkord/Lyrics.

4. **Scroll-Präzision** (`LiveScreen.LyricsPane`): Exakte Item-Größe statt
   Durchschnitt für Pixel-Offset → keine Mikro-Sprünge beim Itemwechsel.

5. **Smooth-Scroll** (frühere Session): `withFrameNanos` + VSync-Sync +
   `scrollToItem(index, pixelOffset)` → butterweicher Scroll bei 60/90/120 Hz.

6. **CI-Deploy-Fix**: APK wird von JEDEM Branch (außer apk-dist) gebaut.

### Noch offen / geplant:

- Scroll-Geschwindigkeit: leichte Variation (etwas schneller/langsamer) — User
  hat es als "sehr smooth" beschrieben, aber noch nicht perfekt gleichmäßig.
  Mögliche Ursache: `NativeEngine.positionFrames()` nicht thread-safe oder
  kleine Jitter im Audio-Timing.
- Akkord-Zuordnung: Für bereits importierte Songs mit verschobenen Akkorden
  hilft Neu-Import der PDF. Kein automatischer Fix für bestehende DB-Daten.
- ChordEditorDialog: Gebaut, aber UX noch nicht vom User getestet/bewertet.
- Offene Features: Freeform-Sync, Quick-Add-Section, Zoom während Sync
