# Live-Gig-Player Pro — Projektkontext für Claude

## SESSION-START — ZUERST LESEN (PFLICHT)

**Diese CLAUDE.md ist die einzige Quelle der Wahrheit.** Kein GitHub-Search, keine Issue-Suche, kein Raten. Alle TODOs, der aktuelle Stand und die Branch-Regel stehen hier unten.

1. Branch prüfen: `git branch` → muss `* main` zeigen. Falls nicht: `git checkout main`
2. Letzten Stand lesen: Abschnitt "Letzter Stand" weiter unten.
3. Offene TODOs lesen: Abschnitt "Offene TODOs (nächste Session)" weiter unten.
4. CI-Status prüfen (GitHub Actions, letzter Build auf `main`).

## Branch-Regel (WICHTIG)

**Einziger erlaubter Branch: `main`**

Kein Feature-Branching. Alle Commits direkt auf `main`.
Es gibt KEINE anderen aktiven Branches. Alte `claude/`-Branches existieren nicht mehr.

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
│   ├── AudioEngine.kt        — ExoPlayer-Wrapper: load, play, pause, seekTo, loop, preload
│   ├── FolderImporter.kt     — SAF-Import: Modus A (WAV-Stems), Modus B (Legacy)
│   └── SongScanner.kt        — erkennt TrackMode aus DocumentFile-Struktur
├── data/
│   ├── Song.kt               — Room-Entity (id, title, artist, bpm, bpmExact, keySignature,
│   │                            genre, capoPosition, volDrums/Bass/Keys/Vocals/Click/Cue,
│   │                            autoStop, playlistId, audioFilePath, duration)
│   ├── SongDao.kt            — CRUD + resetAllMixerSettings
│   ├── Playlist.kt           — Room-Entity (id, name, isLiveLocked)
│   ├── PlaylistDao.kt        — getAllPlaylists
│   ├── GigEntity.kt          — Room-Entity (gigId, name)
│   ├── GigDao.kt             — getAllGigs, insert, delete
│   ├── SetEntity.kt          — Room-Entity (setId, gigOwnerId, name, position)
│   ├── SetSongCrossRef.kt    — (setId, songId, positionInSet, isCompleted, isSpontaneous, endAction)
│   ├── SetDao.kt             — abstract class: CRUD + moveSpontaneousNext/Later + reorderSongs
│   │                            (@Transaction, Cut&Paste / Batch-Write-Pattern)
│   ├── SongInSet.kt          — @Embedded Song + positionInSet + completedInSet + spontaneousInSet + endAction
│   ├── AppDatabase.kt        — RoomDatabase v14, Migrationen bis v14
│   └── TrackMode.kt          — sealed class: Legacy(filePath) | Multitrack(drums,bass,keys,vocals,click,cue)
├── ui/
│   ├── MainScreen.kt         — Compose-UI: zwei Tabs (Archiv / Gig-Sets), Mini-Player, Mixer,
│   │                            SongEditorSheet mit Lyrics-Textfeld, Lyrics-Button in GlobalPlayer
│   ├── PlayerViewModel.kt    — AndroidViewModel: StateFlow, Queue, Loop, AutoStop, isGigSetMode,
│   │                            loopHint (einmaliger Toast-Hinweis fürs LOOP-Verhalten),
│   │                            showLyrics/openLyrics/closeLyrics (Teleprompter-Trigger)
│   ├── GigViewModel.kt       — Gig/Set/Song CRUD, armSetIfIdle, loadSetAsQueue,
│   │                            insertSpontaneousNext/Later (Mutex-serialisiert),
│   │                            reorderSongsInSet/reorderSets, renameSet
│   ├── GigManagementScreen.kt — GigListView/GigRow, GigDetailView/SetCard/SetSongRow
│   │                             (Swipe-Handler), SetSongRowSortable + SetRowSortable
│   │                             (Drag-Handle-Sortiermodi), isLocked bis SetSongRow
│   │                             durchgereicht. KEIN Gig-weiter Edit-Sammel-Modus mehr
│   │                             (siehe Sprint 5.29) — Rename/Löschen/Reset laufen über
│   │                             ein "⋮"-DropdownMenu pro Set bzw. Icon pro Gig; der
│   │                             verbleibende per-Set "Bearbeiten"-Toggle steuert nur
│   │                             noch die Song-Zeilen-Controls (End-Aktion/Entfernen).
│   │                             "Songs hinzufügen" im "⋮"-Menü öffnet AddSongsToSetDialog
│   │                             (Suche + Checkbox-Liste aller Archiv-Songs, die noch
│   │                             nicht im Set sind)
│   └── LyricsOverlay.kt      — Vollbild-Teleprompter (nur Lyrics, keine Akkorde), Hochkant
│                                erzwungen solange sichtbar, Auto-Scroll an echte
│                                Wiedergabeposition gekoppelt, abschnittsweise konstante
│                                Geschwindigkeit über Mehrpunkt-Kalibrierung (Record-Button,
│                                lyricsSyncPoints), Live-Tap-to-Sync als Fallback,
│                                Struktur-Labels "[Chorus]" etc. als eigene Überschrift
│                                gerendert (siehe Gotcha 12)
├── ui/theme/
│   └── Theme.kt              — LiveGigPlayerTheme (dark)
├── LiveGigPlayerApp.kt       — Application-Klasse, DB-Singleton
└── MainActivity.kt           — Entry Point, Compose-Setup
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
| lyrics | String | Songtext ohne Akkorde, mit optionalen Struktur-Labels wie `[Chorus]` (v14) |
| lyricsStartMs | Long | Superseded durch lyricsSyncPoints (v16) — nur Schema-Kompatibilität, unbenutzt |
| lyricsSyncPoints | String | Teleprompter-Kalibrierungspunkte "lineIdx:ms,…", ein Tap pro Abschnitt (v16) |

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

1. **Room v16** — nächste Migration wäre 16→17. Migrationen NIE doppelt anlegen.
2. **ExoPlayer REPEAT_MODE_ONE** — Song loopt endlos, STATE_ENDED wird nie gefeuert. Auto-Stop via Rückwärtssprung-Erkennung im 200ms-Polling.
3. **Loop-Sync** — `tickLoop()` ruft `seekTo()` auf, das alle ExoPlayer in `tracks` iteriert → inhärent synchron.
4. **SAF-Pfadformat** — `"{treeUri}||{folderName}"`, aufgelöst via `DocumentFile.fromTreeUri`.
5. **versionCode** kommt aus der CI-Build-Nummer (`-PversionCode=${{ github.run_number }}`). Lokaler Build → 1.
6. **pointerInput Stale-Capture (KRITISCH)** — `detectHorizontalDragGestures` läuft in
   einem suspend-Block, der NUR bei Key-Änderung neu startet. Callbacks/State, die im
   `onDragEnd` benutzt werden, MÜSSEN über `rememberUpdatedState` geführt werden, sonst
   frieren sie auf die erste Komposition ein. War die Hauptursache für "Swipe wird
   random / trifft falschen Song". Siehe SetSongRow & ArchivSongRow.
7. **Compose-Listen-Identität** — Songs in `SetCard` per `key(songInSet.song.id)`
   gewrappt, damit lokaler State (`dragX`, Dialoge) an die Song-Identität gebunden ist,
   nicht an die Listenposition.
8. **SetDao Swipe-Mutationen** — `moveSpontaneousNext/Later` lesen alle CrossRefs
   einmal (`getRawCrossRefs`), sortieren in-memory um und schreiben EINMAL atomar
   (`updateRawCrossRefs`, `@Update` Batch). KEINE `forEachIndexed { updateSongPosition }`-
   Schleifen mehr (O(N) Queries → Index-Kollisionen).
9. **Sortier-Modus (Drag & Drop) — kein LazyColumn** — `SetSongRowSortable` in
   `SetCard` liegt in einer normalen `Box`, nicht in einer `LazyColumn`, weil
   `Modifier.animateItemPlacement()` nur dort existiert. Stattdessen: feste
   Zeilenhöhe (72dp) + `localOrder`-Liste (State) + `animateFloatAsState` pro
   Zeile für die Y-Position, manueller Offset fürs gezogene Element. Drag-Handle
   (`DragIndicator`-Icon) ist mit `songId` gekeyt (stabil über die ganze Geste),
   Callbacks laufen über `rememberUpdatedState` — sonst derselbe Stale-Capture-Bug
   wie in Gotcha 6. Persistiert wird atomar über `SetDao.reorderSongs`
   (gleiches Batch-Write-Pattern wie `moveSpontaneousNext/Later`).
10. **isLocked-Durchreichung (Performance-Lock)** — `isLocked` kommt aus `MainScreen`
    und wird bis in `SetSongRow`/`ArchivSongRow` durchgereicht (KEIN eigener Lock-State
    pro Screen). Gate-Pattern konsequent: `enabled = !isLocked` an IconButtons,
    `enabled = !isLocked || <bereitsAktiverModus>` beim Ein-/Ausstieg in einen Modus
    (damit man einen bereits offenen Sortier-/Bearbeiten-Modus auch bei nachträglich
    aktiviertem Lock noch sauber verlassen kann, statt eingesperrt zu sein). Bei neuen
    Screens/Actions im Gig-Set-Tab IMMER prüfen, ob sie durch `isLocked` gegatet werden
    müssen — der Tab wird live auf der Bühne benutzt (siehe Sprint 5.29, Befund 02).
11. **Pro-Set Doppel-Toggle (Sortieren ⇄ Bearbeiten)** — `SetCard` hat zwei
    unabhängige, aber gegenseitig exklusive Modi: `sortMode` (Drag-Handles) und
    `editSongsMode` (End-Aktion/Entfernen-Controls in `SetSongRow`). Einstieg in den
    einen setzt den anderen explizit auf `false` (kein `LaunchedEffect`, da beide
    gleichrangig sind, nicht wie früher Gig-weites `isEditing` vs. lokales `sortMode`).
12. **Lyrics-Teleprompter — Scroll an Wiedergabeposition, NICHT an BPM, KEIN
    ScrollState** — `LyricsOverlay.kt` scrollt proportional zu `positionMs /
    durationMs` (echte Player-Position), nicht über eine BPM-Rechnung. Dadurch
    ist der Song immer exakt zu Ende gescrollt, wenn er zu Ende gespielt ist —
    unabhängig davon, ob die in der DB hinterlegte BPM stimmt.
    **WICHTIG (Sprint 5.36, nach vier gescheiterten Fix-Versuchen mit
    Compose's `ScrollState`):** Der Text-Block wird NICHT über
    `Modifier.verticalScroll()`/`ScrollState` gescrollt, sondern über
    `Modifier.offset { IntOffset(0, -scrollOffsetPx.roundToInt()) }`
    (Lambda-Variante) innerhalb einer fest positionierten, `clipToBounds()`-
    begrenzten Viewport-`Box` verschoben. Grund: `ScrollState.scrollTo()`
    (Frame-Loop, jeden Frame) und `ScrollState.animateScrollTo()` (früher in
    `handleTap()`) konkurrieren um dieselbe interne `MutatorMutex` und
    unterbrechen sich gegenseitig — Hauptverdacht für anhaltendes Ruckeln über
    mehrere Fix-Versuche hinweg. **Bei künftigen Änderungen an diesem Screen:
    NIEMALS `ScrollState`/`verticalScroll`/`scrollTo`/`animateScrollTo` wieder
    einführen** — Viewport- und Content-Höhe werden stattdessen selbst per
    `onGloballyPositioned` gemessen (`viewportHeightPx`, `contentHeightPx`),
    `maxScrollPx = contentHeightPx - viewportHeightPx` direkt daraus berechnet.
    Es gibt nur EINEN Schreiber für `scrollOffsetPx` (die Frame-Loop);
    `handleTap()` setzt ausschließlich den Anker, nie direkt den Offset.
    Zwei Garantien, beide bewusst doppelt abgesichert:
    - **Nur abwärts, nie zurück:** `scrollOffsetPx` wird ausschließlich über
      `if (neuerWert > scrollOffsetPx) scrollOffsetPx = neuerWert` erhöht — sowohl
      im Frame-Loop als auch bei Tap-to-Sync (via Anker-Verschiebung). Ein
      kurzzeitiger Jitter in der Positionsschätzung (oder ein Rückwärts-Seek)
      kann den Scroll dadurch NIEMALS nach oben reißen, er bleibt höchstens stehen.
    - **Tap-to-Sync verschiebt den Anker, nicht die Zeile fix:** Tap sucht per
      `linePositions` (gemessen via `onGloballyPositioned`, siehe unten) die
      nächste noch nicht erreichte Zeile, setzt `anchorPositionMs`/`anchorScrollPx`
      auf (jetzt, diese Zeile) — die Frame-Loop holt sich den neuen Zielwert im
      nächsten Frame automatisch selbst ab (kein direkter Schreibzugriff aus
      `handleTap()`, siehe oben) und rechnet die Scroll-Rate für den Rest des
      Songs neu — kompensiert damit automatisch ungleichmäßige Zeilendichte
      (Strophe vs. Instrumental-Teil vs. Refrain).
    - **`allLinesMeasured`-Gate:** Die Frame-Loop tut buchstäblich nichts
      (`continue`), bevor `linePositions.size >= nonBlankLineCount` UND
      Viewport-/Content-Höhe beide > 0 sind — eliminiert die ganze Fehlerklasse
      "teilweise vermessenes Layout beim (Wieder-)Öffnen" strukturell, statt sie
      Fall für Fall mit Nullable-Checks abzufangen (so wie in Sprint 5.33
      versucht — hat nicht ausgereicht).
    - Frame-Loop (`withFrameNanos` in einer Endlosschleife) treibt
      kontinuierliches 60fps-Scrollen — keine separate Animation mehr, die mit
      der Loop um denselben Zustand konkurrieren könnte.
    - `linePositions[index]` wird NICHT analytisch aus Zeilenhöhe berechnet
      (bricht bei Zeilenumbruch auf schmalen Screens), sondern real gemessen —
      robust unabhängig von Fontgröße/Gerätebreite. `positionInParent()` ist
      relativ zur Text-Column selbst (nicht zum Screen) und bleibt dadurch
      stabil, auch während die Column per `Modifier.offset` bewegt wird.
    - Der Screen erzwingt Hochkant nur für sich selbst (`activity.requestedOrientation`
      in `DisposableEffect`, zurückgesetzt beim Schließen) — der Rest der App bleibt
      unangetastet, es gibt sonst nirgends eine Orientierungssperre.
    - Auto-Öffnen nur EINMAL pro frisch angewähltem Song (`lyricsAutoShownForSongId`
      in `PlayerViewModel`, zurückgesetzt in `selectSong`) — sonst würde jedes
      Pause/Play-Toggle den Screen erneut aufreißen.
    - **Mehrpunkt-Kalibrierung (`song.lyricsSyncPoints`, Record-Button im
      Header) — löst den einmaligen Start-Anker aus Sprint 5.31 ab:** eine
      live gespielte Version hat oft nicht nur eine andere Intro-Länge,
      sondern generell eine andere Zeilendichte pro Abschnitt als die
      BPM-Rechnung annimmt (Strophe/Chorus/Bridge unterschiedlich lang
      relativ zur Studio-Version). Eine einzelne globale Rate reicht dafür
      nicht. Stattdessen: Record-Button startet eine Aufnahme-Session, User
      tippt einmal pro Abschnittswechsel durch den kompletten Song — jeder
      Tap speichert `(Zeilen-Index, aktuelle Position)` in
      `calibrationPoints`. Beim Beenden wird die sortierte Liste als
      `"lineIdx:ms,lineIdx:ms,…"` persistiert (`SongDao.updateLyricsSyncPoints`).
      Danach läuft der Scroll bei jedem künftigen Play automatisch
      abschnittsweise mit der aus den gespeicherten Punkten interpolierten
      Rate — kein Live-Tippen mehr nötig. Ohne Kalibrierungspunkte (leerer
      String) verhält sich alles exakt wie die ursprüngliche reine
      Positions-Proportion (Fallback bleibt erhalten, kein Sonderfall nötig).
      **Umsetzung im Frame-Loop:** `anchorPositionMs`/`anchorScrollPx`
      schalten pro Frame automatisch auf den nächsten bereits erreichten
      Kalibrierungspunkt weiter (`while`-Schleife über den sortierten
      Breakpoints, verglichen mit der aktuellen Position) — die Rate wird
      dann nur für das AKTUELLE Segment (bis zum nächsten Punkt bzw.
      Songende) berechnet, nicht mehr global für den ganzen Song. Live-
      Tap-to-Sync (außerhalb der Kalibrierung) nutzt denselben Anker
      (`handleTap()`), überschreibt ihn aber nur ephemer für die laufende
      Wiedergabe, ohne etwas zu persistieren — komponiert automatisch
      korrekt mit der Kalibrierungs-Logik durch dieselbe
      `scrollOffsetPx`-Monoton-Klemmung (siehe oben), ohne Sonderfall-Code.
    - **Struktur-Labels ohne Akkorde:** Zeilen im Format `[Chorus]`/`[Verse 1]`
      im Lyrics-Text werden per `sectionTagRegex` erkannt und als eigene,
      Volt-farbene Überschrift gerendert (nicht als normale weiße Lyric-Zeile)
      — bewusst NICHT aus dem Text entfernt wie Akkord-Zeilen, weil es reine
      Songstruktur ist, kein Akkord.

## Letzter Stand

**Datum:** 2026-07-19
**CI Build:** noch nicht gepusht — lokal implementiert, kein Gradle-Build in dieser Session möglich (siehe Sprint 5.37)
**Branch:** `main` (einziger Branch; alle claude/-Branches bereinigt, main = Default)
**Commit:** Fix zwei Nebenwirkungen des 5.36-Neuentwurfs — kein Sofort-Feedback beim Tap, Touch-Durchfall zur MainScreen-TopBar

### Sprint 5.37 DONE (ungetestet): Fix — Tap-Sofortsprung fehlte + Touch fiel zur TopBar durch

User-Report nach Live-Test von Sprint 5.36 (Commit 279490d, CI grün): Kalibrierungspunkte
werden jetzt endlich korrekt gespeichert (5.36 hat den 0-Punkte-Bug also tatsächlich
behoben!), ABER zwei neue Symptome: (1) der Text bewegt sich während der Kalibrierung
selbst überhaupt nicht sichtbar; (2) Stop drücken, dann X (Schließen) drücken öffnet
stattdessen das "Alle Songs löschen"-Menü der normalen App-Titelleiste.

**Ursache 1 — kein Sofort-Feedback bei Tap:** In 5.36 wurde `handleTap()` bewusst
so umgebaut, dass NUR der Anker gesetzt wird ("die Frame-Loop holt sich den neuen
Wert automatisch"). Rechnerisch stimmt das — aber während einer laufenden
Kalibrierung existieren noch keine Kalibrierungspunkte, also rechnet die Loop bei
jedem Tap weiterhin mit "Rest des GANZEN Songs bis zum Ende" als Zielspanne. Zwischen
zwei Taps (typischerweise wenige Sekunden) bewegt sich der Text bei dieser Rate nur
um Bruchteile eines Pixels — praktisch nicht wahrnehmbar. **Fix:** `handleTap()`
springt jetzt zusätzlich sofort sichtbar zur getippten Zeile (`scrollOffsetPx =
entry.value`, monoton geklemmt). Das ist hier sicher (anders als in der alten
ScrollState-Version) — kein `animateScrollTo()`, keine konkurrierende Coroutine,
nur eine einfache Zuweisung, exakt wie in der Frame-Loop selbst.

**Ursache 2 — Touch-Durchfall zur TopBar:** Mein Schließen-Button (X) sitzt oben
rechts — an fast derselben Bildschirmposition wie der "⋮"-Menü-Button der
MainScreen-`TopBar` dahinter (beide direkt unter dem Statusbalken, ganz rechts).
Die äußerste `Box` des Teleprompters deckt den Screen zwar optisch komplett ab,
hatte aber selbst KEINEN Touch-Handler — nur einzelne Kind-Elemente (Buttons,
Tap-to-Sync-Viewport). Ein Tap, der die Buttons knapp verfehlt (z.B. beim
schnellen Antippen von Stop direkt gefolgt von X), fiel dadurch zur
dahinterliegenden `TopBar` durch und traf dort das Menü. **Fix:** leerer
`detectTapGestures {}`-Handler auf der äußersten Box fängt jeden nicht
anderweitig konsumierten Tap ab — Compose testet Kind-Elemente (Buttons,
Viewport) zuerst, die Handler der Buttons bleiben also unangetastet.

- **Nicht verifiziert:** Wie 5.30–5.36 kein Gradle-Build in dieser Session
  möglich. **Positiv:** Der User-Report bestätigt zum ersten Mal, dass der
  Kern-Bug (0 Kalibrierungspunkte / Race-to-bottom) durch den 5.36-Neuentwurf
  tatsächlich behoben ist — die beiden 5.37-Fixes sind reine Nebenwirkungen
  desselben Umbaus, keine neue Baustelle. **Nächste Session: gezielt prüfen**,
  ob der Text jetzt bei jedem Kalibrierungs-Tap sofort sichtbar springt, und
  ob Stop→X den Screen jetzt sauber schließt statt das Lösch-Menü zu öffnen.

### Sprint 5.36 DONE: Kompletter Architektur-Neuentwurf — ScrollState + Animation komplett entfernt (Commit 279490d, CI grün — Kern-Bug laut User-Report BEHOBEN, zwei Nebenwirkungen in 5.37 gefixt)

User-Report nach Live-Test von Sprint 5.35 (Commit ce8d574, CI grün): Bug besteht
ERNEUT UNVERÄNDERT ("während der Kalibrierung wird ganz schnell wieder immer noch
nach unten gescrollt und sehr ruckelig", Kalibrierungspunkte werden wieder nicht
übernommen). Das ist der VIERTE erfolglose Fix-Versuch in Folge (5.33, 5.34, 5.35).
User-Anweisung danach explizit: nicht weiter Symptome flicken, sondern die
Funktion komplett neu von Grund auf durchdenken und sauber implementieren.

**Neubewertung:** Alle bisherigen Theorien (Layout-Race, dur/pos-Plausibilität,
Session-Reset, Click-Track-Duration) betrafen ausschließlich die
RATE-BERECHNUNG. Da der Hauptplayer (Fortschrittsbalken/Countdown, dieselben
`positionMs`/`durationMs`-Werte aus `PlayerViewModel`) nie als fehlerhaft
gemeldet wurde, war die Datenquelle vermutlich nie das eigentliche Problem —
der Fehler lag höchstwahrscheinlich in der Scroll-MECHANIK selbst:

**Root Cause (neue Theorie, mit deutlich höherer Zuversicht):** `handleTap()`
rief `scrollState.animateScrollTo()` auf, während die Frame-Loop GLEICHZEITIG
jeden Frame `scrollState.scrollTo()` aufrief — beide konkurrieren um dieselbe
interne Compose-Sperre (`MutatorMutex` in `ScrollableState.scroll()`). Ein neuer
`scroll()`-Aufruf mit Standard-Priorität unterbricht automatisch einen noch
laufenden — die Frame-Loop (alle ~16ms) hat damit `animateScrollTo()`
faktisch permanent abgewürgt, lange bevor die Animation nennenswert
fortschreiten konnte. Zusätzlich hing die gesamte Rate-Berechnung von
`ScrollState.maxValue` ab — dessen genaues Verhalten in Kombination mit
`enabled = false` und einer gleichzeitig per Frame-Loop manipulierten
Scroll-Position nicht mit Sicherheit vorhersagbar war.

**Fix — komplette Neuarchitektur, keine Patches mehr:**
- `ScrollState`/`verticalScroll`/`scrollTo`/`animateScrollTo` vollständig
  entfernt. Ersetzt durch `Modifier.offset { IntOffset(0, -scrollOffsetPx...) }`
  (Lambda-Variante — läuft nur in der Platzierungsphase, kein
  Recomposition-Overhead) auf einer Text-Column innerhalb eines fest
  positionierten, `clipToBounds()`-begrenzten Viewport-Box.
- **Nur noch EIN Schreiber für die Scroll-Position:** die Frame-Loop.
  `handleTap()` setzt ausschließlich den Anker (`anchorPositionMs`/
  `anchorScrollPx`) — die Loop übernimmt den neuen Zielwert automatisch im
  nächsten Frame. Keine zweite, konkurrierende Animation mehr möglich.
- **Viewport- und Content-Höhe selbst gemessen** (`onGloballyPositioned` auf
  Viewport-Box und Text-Column), `maxScrollPx = contentHeightPx -
  viewportHeightPx` direkt daraus berechnet — keine Abhängigkeit mehr von
  internem `ScrollState`-Verhalten.
- **Explizites `allLinesMeasured`-Gate** (`linePositions.size >=
  nonBlankLineCount`) VOR jeder Berechnung — die Frame-Loop tut buchstäblich
  nichts, bevor nicht wirklich jede Zeile vermessen ist. Eliminiert die ganze
  Fehlerklasse "teilweise vermessenes Layout" strukturell, statt sie wie in
  5.33–5.35 Fall für Fall abzufangen.
- Die dur/pos-Plausibilitätsprüfung (5.34) und die zusätzliche
  `song.duration`-Absicherung (5.35) bleiben zusätzlich bestehen (schaden
  nicht, auch wenn sie vermutlich nie die eigentliche Ursache waren).
- Diagnose-Logging (5.35) bleibt für den Notfall bestehen, an neue
  Variablennamen angepasst.

- **Nicht verifiziert:** Wie 5.30–5.35 kein Gradle-Build in dieser Session
  möglich. Dies ist aber die erste Fix-Runde, die die STRUKTUR des Problems
  angeht statt eine weitere Theorie über die Werte zu patchen — entsprechend
  höhere Zuversicht, aber ohne Gerätetest nicht mit Sicherheit zu behaupten.
  **Nächste Session, falls der Bug immer noch auftritt:** `adb logcat -s
  LyricsOverlay` mitschneiden (sollte jetzt aussagekräftiger sein, da die
  Kandidatenliste möglicher Ursachen strukturell kleiner ist) und zusätzlich
  gezielt prüfen, ob das Ruckeln JETZT verschwunden ist (das war unabhängig
  von der Ziel-Berechnung durch die ScrollState/Animation-Kollision erklärbar
  und sollte durch die neue Architektur unabhängig vom Rest behoben sein).

### Sprint 5.35 DONE: Fix — vermutlich echte Root Cause (falsche durationMs-Quelle) + Diagnose-Logging als Sicherheitsnetz (Commit ce8d574, CI grün — hat laut User-Report NICHT ausgereicht, siehe Sprint 5.36)

User-Report nach Live-Test von Sprint 5.34 (Commit 1600120, CI grün): Bug besteht
UNVERÄNDERT ("scrollt während der Kalibrierung immer noch schnell und ruckelig
nach unten"). Das ist jetzt der DRITTE erfolglose Fix-Versuch in Folge — Grund
zur Sorge, dass die bisherigen Theorien (Layout-Race, dur/pos-Plausibilität,
Session-Reset) zwar echte Verbesserungen waren, aber nicht die eigentliche
Ursache getroffen haben.

**Neue Spur gefunden:** `FolderImporter.kt` Zeile 70 hat einen Kommentar
`// Bug-Fix 2: case-insensitive, auch "klick" (deutsch)` und berechnet
`song.duration` bewusst aus einem `nonClick`-Stem — dokumentierter Beleg, dass
der Click-Track in diesem Projekt schon mal falsche/kurze Audiolängen geliefert
hat. Genau diese Vorsicht fehlt in `AudioEngine.durationMs`
(`tracks.firstOrNull()?.duration`), das für die LIVE-Wiedergabe (und damit für
den Teleprompter) verwendet wird — bei Multitrack-Songs ohne Click-Ausschluss.
Eine zu kurze `durationMs` lässt im Frame-Loop die Scroll-Rate explodieren
(`rate = max/dur` mit kleinem `dur`) → Text rast in den ersten paar Sekunden
ans Ende, noch bevor ein Kalibrierungs-Tap greifen kann (erklärt "schnell +
ruckelig" UND "0 Punkte gespeichert" in einem).

**Fix:** `LyricsOverlay.kt` nutzt jetzt `max(engine.durationMs, song.duration
als ms geparst)` statt nur der live gemeldeten `durationMs` — `song.duration`
wurde beim Import einmalig über `MediaMetadataRetriever` aus einem
Nicht-Click-Stem gemessen und ist damit die robustere Quelle. `AudioEngine`
selbst wurde bewusst NICHT angefasst (würde Loop-Punkte/Auto-Stop/Progress-Bar
für ALLE Songs betreffen, zu riskant ohne Testmöglichkeit in dieser Session)
— der Fix ist bewusst auf den Teleprompter beschränkt.

**Zusätzlich: Diagnose-Logging als Sicherheitsnetz** (`Log.d`/`Log.w`, Tag
`LyricsOverlay`), falls diese Theorie IMMER NOCH nicht die volle Antwort ist:
- Beim Start jeder Session: `song.duration`, live `durationMs`, Breakpoints.
- Einmalig falls der `dur >= pos`-Guard blockiert (Sprint 5.34) — zeigt, ob
  die durationMs-Quelle selbst nach dem Fix noch zu klein ist.
- Die ersten 10 "großen Sprünge" (>30px in einem Frame) mit allen Werten
  (pos, dur, anchor, segEnd, rate, max) — das war bisher der Kern des Problems
  und ist jetzt direkt sichtbar statt erraten. Per `adb logcat -s
  LyricsOverlay` auslesbar.

- **Nicht verifiziert:** Wie 5.30–5.34 kein Gradle-Build in dieser Session
  möglich. **Nächste Session, falls der Bug immer noch auftritt:** zuerst
  `adb logcat -s LyricsOverlay` während eines Kalibrierungsversuchs
  mitschneiden und die Werte auswerten, statt eine weitere Theorie zu raten.

### Sprint 5.34 DONE: Fix — Sprint-5.33-Fix hat nicht gereicht, echter Root Cause gefunden (Commit 1600120, CI grün — hat laut User-Report NICHT ausgereicht, siehe Sprint 5.35)

User-Report nach Live-Test von Sprint 5.33 (Commit 614aca3, CI grün): Bug besteht
weiter UNVERÄNDERT ("scrollt immer noch mit Rucklern schnell nach unten"),
ZUSÄTZLICH: trotz 2 gesetzter Kalibrierungspunkte zeigt die App beide Male
"0 Kalibrierungspunkte gespeichert" an.

**Warum 5.33 nicht reichte:** Der 0-Punkte-Symptom bewies, dass `song.lyricsSyncPoints`
zu diesem Zeitpunkt noch LEER war (kein einziger Tap wurde je erfolgreich
gespeichert) — der 5.33-Fix betraf aber ausschließlich den Pfad MIT bereits
vorhandenen Kalibrierungspunkten (`nextBreak != null`). Der eigentliche Bug lag
also im BASIS-Pfad (keine Kalibrierung, reine Positions-Proportion), den 5.33
gar nicht angefasst hatte.

**Root Cause 1 (der eigentliche Auslöser):** Die Rate-Formel im Frame-Loop
prüfte nirgends, ob `durationMs` plausibel zur aktuellen `positionMs` passt.
`durationMs` kann beim Songwechsel (A/B-Crossfade-Preload in `AudioEngine`,
siehe Gotcha 3) für einen Frame noch einen veralteten, zu kleinen Wert liefern,
während `positionMs` schon weiterläuft. Sobald `pos > dur` gilt, schießt die
berechnete Rate ins Unermessliche, `raw` wird sofort auf `max` geklemmt — und
bleibt dort wegen der Monoton-Klemmung hängen, noch bevor überhaupt ein
Kalibrierungs-Tap ankommen konnte (`handleTap()` findet keine tiefere Zeile
mehr → 0 Punkte gespeichert, erklärt beide Symptome auf einen Schlag).
**Fix:** zusätzliche Plausibilitätsprüfung `dur >= pos` im Frame-Loop-Guard.

**Root Cause 2 (Robustheit gegen alte, schon "verkorkste" Sessions):**
`targetScrollPx` & Co. waren nur mit `song.id` gekeyt, nicht mit einem
Öffnen-Zähler — ein erneutes Schließen/Öffnen desselben Songs (ohne
Song-Wechsel) konnte daher einen bereits durch Root Cause 1 kaputten Zustand
aus einer früheren Session weiterschleppen, selbst nachdem Root Cause 1
gefixt war. **Fix:** neuer `openSession`-Zähler (`LyricsOverlay`, erhöht sich
bei jedem `visible = true`), zusätzlicher `remember()`-Key für den kompletten
Scroll-Zustand inkl. `ScrollState` selbst (vorher `rememberScrollState()`,
jetzt `remember(song.id, openSession) { ScrollState(0) }`) — jedes Öffnen
startet dadurch garantiert komplett frisch.

**Sprint 5.33 bleibt zusätzlich bestehen** (der Fix für den Kalibrierungspfad
war für sich genommen korrekt, hat nur das eigentliche Problem nicht erreicht,
weil `lyricsSyncPoints` in diesem Fall noch leer war).

- **Nicht verifiziert:** Wie 5.30–5.33 kein Gradle-Build in dieser Session
  möglich — nur manuell gegengelesen. **Nächste Session: live gegentesten**,
  insbesondere ob jetzt tatsächlich Kalibrierungspunkte gespeichert werden
  und der Scroll in der kalibrierten Geschwindigkeit läuft, statt sofort
  bis ans Ende zu springen.

### Sprint 5.33 DONE: Fix — Scroll sprang schnell/ruckartig bis ans Songende (Race-Bug, Commit 614aca3, CI grün — hat laut User-Report NICHT ausgereicht, siehe Sprint 5.34)

User-Report nach Live-Test von Sprint 5.32: Text scrollt "ziemlich abgehackt ... in hoher
Geschwindigkeit" von oben nach unten statt in der kalibrierten Geschwindigkeit.

**Root Cause:** Im Frame-Loop (`LyricsOverlay.kt`) fiel `segEndPx` (Scroll-Ziel des
nächsten Kalibrierungspunkts) auf `max.toFloat()` (= volle Scroll-Länge) zurück,
wann immer `linePositions[lineIdx]` noch `null` war — nicht nur wenn es KEINEN
nächsten Breakpoint mehr gab (beabsichtigt), sondern fälschlich AUCH, wenn ein
Zwischen-Breakpoint schlicht noch nicht vermessen war (Compose-Layout-Timing-Race
beim (Wieder-)Öffnen des Screens, v.a. wenn mitten im Song geöffnet — `positionMs`
dann schon groß). Ergebnis: ein einzelner Zwischenpunkt wurde für einen Frame wie
das Songende behandelt → Rate schießt hoch → durch die Monoton-Klemmung
(`targetScrollPx` darf nie sinken) blieb dieser falsche, viel zu hohe Wert für den
Rest der Wiedergabe hängen.

**Fix:** `segEndPx` ist jetzt `Float?` — `null` nur beim echten Songende
(kein weiterer Breakpoint), sonst bei fehlender Messung schlicht kein Update in
diesem Frame (nächster Frame versucht's erneut), statt eines falschen Ausweich-
werts. Gleiches Pattern beim Anker-Vorschalten: `break` statt stillschweigendem
Überspringen, falls eine Zeile noch nicht vermessen ist — kein Kalibrierungspunkt
geht mehr verloren.

- **Nicht verifiziert:** Wie 5.30–5.32 kein Gradle-Build in dieser Session
  möglich — nur manuell gegengelesen. **Nächste Session: live gegentesten**,
  v.a. den ursprünglich gemeldeten Fall (Screen mitten im Song öffnen/erneut
  öffnen) gezielt reproduzieren.

### Sprint 5.32 DONE (ungetestet): Teleprompter — Mehrpunkt-Kalibrierung statt Start-Anker (Room v15→16)

User-Wunsch nach Sprint 5.31 (Push, Commit ed4bc76, CI-Status noch nicht gegengecheckt):
der einmalige Start-Anker reicht nicht — eine live gespielte Version hat nicht nur
eine andere Intro-Länge, sondern generell abweichende Zeilendichte pro Abschnitt.
Gewünschter Ablauf: einmal komplett durch den Song tippen (ein Tap pro
Abschnittswechsel: Intro → Vers → Chorus → …), das wird gespeichert, und ab dann
läuft der Song in exakt dieser abschnittsweise konstanten Geschwindigkeit durch.
Explizit wiederholt: NIEMALS rückwärts springen (Erfahrung aus anderen Apps),
konstante Vorlaufgeschwindigkeit, smooth.

- **DB:** `Song.lyricsStartMs` bleibt nur für Schema-Kompatibilität erhalten
  (unbenutzt, nicht mehr beschrieben — kein DROP COLUMN, Konvention dieses
  Projekts ist ADD-only, siehe bisherige Migrationshistorie).
  Neu: `Song.lyricsSyncPoints: String = ""`, Migration `MIGRATION_15_16`
  (`ALTER TABLE songs ADD COLUMN lyricsSyncPoints TEXT NOT NULL DEFAULT ''`),
  `SongDao.updateLyricsSyncPoints()`, `PlayerViewModel.updateLyricsSyncPoints()`.
  `SongDao.updateLyricsStartMs()` (Sprint 5.31) wieder entfernt — war nur für
  eine Session im Einsatz, keine anderen Aufrufer.
- **LyricsOverlay — Kalibrierung statt Start-Anker:** Flag-Button ersetzt durch
  Record/Stop-Toggle (`FiberManualRecord`/`Stop`, rot während Aufnahme, plus
  Statuszeile mit Live-Zähler). Während aktiv zeichnet jeder Tap
  `(Zeilen-Index, Position)` auf; "Fertig" persistiert die sortierte Liste.
  Kompletter Umbau des Frame-Loops: `anchorPositionMs`/`anchorScrollPx`
  schalten pro Frame automatisch durch bereits erreichte Kalibrierungspunkte
  weiter, Rate wird nur noch pro aktuellem Segment berechnet (nicht mehr
  global für den ganzen Song) — siehe Gotcha 12 für Details. Live-Tap-to-Sync
  (außerhalb Kalibrierung) und die Monoton-Klemmung (nie rückwärts) bleiben
  unverändert bestehen und komponieren automatisch korrekt mit der neuen
  Segment-Logik, ohne Sonderfall-Code.
- **Aktualisierte Lyrics-Datei nicht nötig** — Struktur-Labels aus Sprint 5.31
  bleiben unverändert, dienen jetzt zusätzlich als natürliche Tap-Ziele beim
  Kalibrieren (User tippt direkt auf "[Chorus]" etc.).
- **Nicht verifiziert:** Wie 5.30/5.31 kein Gradle-Build in dieser Session
  möglich — nur manuell gegengelesen. **Nächste Session: CI-Status prüfen,
  dann Kalibrierung an "Bed Of Roses" live durchtesten** (einmal durchtippen,
  App schließen/neu öffnen, prüfen ob die Geschwindigkeit ohne erneutes
  Tippen stimmt).

### Sprint 5.31 DONE: Teleprompter — Start-Anker gegen Intro-Drift + Struktur-Labels (Room v14→15) — Commit ed4bc76, superseded durch Sprint 5.32

User-Feedback nach erstem Live-Test von Sprint 5.30: Lyrics laufen korrekt von oben nach
unten (funktioniert!), aber zwei Probleme: (1) Songstruktur (Vers/Chorus/Bridge) ist
optisch nicht erkennbar, weil beim PDF-Cleanup alle `[...]`-Marker mit rausgefiltert
wurden; (2) die Live-Version hat eine andere (längere) Intro als die BPM-Rechnung
zugrunde legt, dadurch läuft der Text während der Intro schon los, statt zu warten.

- **DB:** `Song.lyricsStartMs: Long = 0L`, Migration `MIGRATION_14_15` (`ALTER TABLE
  songs ADD COLUMN lyricsStartMs INTEGER NOT NULL DEFAULT 0`),
  `SongDao.updateLyricsStartMs()`, `PlayerViewModel.updateLyricsStartMs()`.
- **LyricsOverlay — Start-Anker:** neuer Flag-Icon-Button im Header. Tippen setzt
  `anchorPositionMs`/`anchorScrollPx` auf (aktuelle Position, 0) und persistiert das
  einmalig pro Song — ab dann läuft der Scroll bei jedem künftigen Play automatisch
  erst ab diesem Zeitpunkt los (z.B. nach einer langen Intro), ganz ohne live
  Tippen. Tap-to-Sync bleibt als Korrektur-Fallback bestehen (Empfehlung aus
  3 Optionen, die dem User vorgelegt wurden — "einmalig setzen, keine Aufmerksamkeit
  während des Auftritts nötig" hat gegen "durchgehend mittippen" gewonnen).
- **LyricsOverlay — Struktur-Labels:** `sectionTagRegex` erkennt Zeilen wie
  `[Chorus]`/`[Verse 1]` im Lyrics-Text und rendert sie als eigene Volt-farbene
  Überschrift statt als normale weiße Lyric-Zeile — bewusst NICHT entfernt wie
  Akkorde, weil reine Songstruktur kein Akkord ist.
- **PDF-Cleanup-Skript angepasst:** behält jetzt `[...]`-Marker (vorher überall
  rausgefiltert), aktualisierte Lyrics-Datei an den User geschickt zum erneuten
  Einfügen ins Lyrics-Feld.
- **Nicht verifiziert:** Wie Sprint 5.30 kein Gradle-Build in dieser Session
  möglich — nur manuell gegengelesen.

### Sprint 5.30 DONE: Lyrics-Teleprompter mit Auto-Scroll + Tap-to-Sync (Room v13→14) — Commit 05a4c00, CI grün, Live-getestet

User-Wunsch: Reinen Songtext (keine Akkorde) im Hochkant-Vollbild anzeigen, der
automatisch aufgeht, sobald ein Song mit hinterlegten Lyrics gestartet wird, und
der so durchscrollt, dass am Songende der komplette Text durchgelaufen ist —
"smooth", mit "Tap to Sync", und explizit NIE rückwärts scrollend.

- **DB:** `Song.lyrics: String = ""`, Migration `MIGRATION_13_14` (`ALTER TABLE
  songs ADD COLUMN lyrics TEXT NOT NULL DEFAULT ''`), `SongDao.updateLyrics()`.
- **PlayerViewModel:** `showLyrics`/`openLyrics()`/`closeLyrics()`,
  `updateLyrics()`. `togglePlayPause()` öffnet den Teleprompter automatisch beim
  ERSTEN Play eines frisch angewählten Songs mit nicht-leeren Lyrics
  (`lyricsAutoShownForSongId`, zurückgesetzt in `selectSong`) — spätere
  Pause/Play-Toggles reißen den Screen nicht erneut auf.
- **LyricsOverlay.kt (neu):** Vollbild-Overlay analog zu `MixerOverlay`. Details
  zur Scroll-Logik (Frame-Loop statt BPM-Rechnung, Monoton-Klemmung, Tap-to-Sync-
  Ankerverschiebung, gemessene statt berechnete Zeilenpositionen, Hochkant-Sperre
  nur lokal) siehe Gotcha 12.
- **MainScreen.kt:** `SongEditorSheet` hat ein neues mehrzeiliges Lyrics-Textfeld
  (Placeholder weist explizit auf "ohne Akkorde" hin); `GlobalPlayer` zeigt einen
  Lyrics-Button (Icons.Filled.Article), sobald der aktuelle Song Lyrics hat —
  zum manuellen (Wieder-)Öffnen, falls der Screen geschlossen wurde.
- **Bewusst NICHT BPM-basiert:** Auto-Scroll nutzt die echte Wiedergabeposition
  (`positionMs`/`durationMs`) statt einer BPM-Berechnung — robust auch wenn die
  in der DB hinterlegte BPM ungenau ist (die exakte BPM von "Bed of Roses" war
  z.B. nicht zweifelsfrei zu ermitteln, ~80–84 je nach Half-/Full-Time-Zählung).
- **Nicht verifiziert:** Kein Gradle-Build in dieser Session möglich (Google-
  Maven `dl.google.com` durch die Sandbox-Netzwerk-Policy blockiert, 403 — siehe
  `/root/.ccr/README.md`). Nur manuell gegen den bestehenden Code gegengelesen
  (Imports, Signaturen, Aufrufstellen). **Nächste Session: zuerst CI-Build-Status
  prüfen und auf echtem Gerät testen**, bevor an dieser Datei weitergearbeitet wird.

### Sprint 5.29 DONE: Vollständiger UX-Audit + Umsetzung aller 13 Befunde (Commit b5732f5)

User-Wunsch: "gesamten Code durchlesen, Analyse machen, alles finden was die App
daran hindert intuitiv zu sein, Optionen liefern" — danach: "setz bitte alles
perfekt um". Voller Audit über ~3.400 Zeilen (alle UI-Screens, beide ViewModels,
Import-Flow), Report als Artifact geliefert (13 Befunde, 2 kritisch/5 mittel/6
gering, je mit Code-Referenz + Optionen + Empfehlung), danach alle 13 direkt
umgesetzt. Größter Umbau der App bisher (4 Dateien, ~360 Zeilen Diff).

**Kritisch:**
- **Bestätigungsdialoge für Gig-/Set-Löschung:** `GigRow` und `SetCard` hatten
  vorher direkten `onClick → onDelete` ohne Rückfrage (Cascade-Delete über
  Foreign Keys — ein Fehltipp zerstörte den ganzen Gig samt alter Setlisten).
  Jetzt gleiches `AlertDialog`-Pattern wie beim Song-Löschen im Archiv, mit
  Namen in der Bestätigungsfrage.
- **Performance-Lock (`isLocked`) auf Gig-Set-Tab ausgeweitet:** existierte
  vorher NUR im Archiv-Tab — der Tab, der tatsächlich live auf der Bühne
  benutzt wird (Gig-Sets), hatte gar keinen Schutz vor Fehltipps. Jetzt
  durchgereicht `MainScreen → GigManagementScreen → GigDetailView → SetCard
  → SetSongRow` (siehe Gotcha 10), gatet Play/Swipe/Sortieren/Bearbeiten/
  Löschen/Umbenennen/Reset.

**Mittel:**
- **Gig-weiter "Bearbeiten"-Sammel-Modus entfernt** (Befund: ein Tap löste
  vier unabhängige UI-Änderungen gleichzeitig aus). `GigListView`/
  `GigDetailView` haben kein `isEditing`/`onToggleEdit` mehr. Gig-Löschen
  ist jetzt ein immer sichtbares Icon in `GigRow`. Set-Umbenennen/-Löschen/
  Completed-Reset laufen über ein immer sichtbares `DropdownMenu` ("⋮") im
  `SetCard`-Header. Übrig gebliebener per-Set `editSongsMode`-Toggle
  (weiterhin Edit-Icon ⇄ Check) steuert NUR noch die Song-Zeilen-Controls
  (End-Aktion-Button, Entfernen-X) — siehe Gotcha 11 für die Exklusivität
  mit `sortMode`.
- **Statuszeile bei aktivem Sortier-/Bearbeiten-Modus** — kleiner GigVolt-
  Hinweistext unter dem Header ("Sets werden sortiert …" / "Songs werden
  sortiert …" / "Song-Bearbeitung aktiv …"), weil sowohl Sortieren als auch
  Bearbeiten optisch nur als Checkmark erkennbar waren (nicht unterscheidbar
  auf den ersten Blick).
- **Wisch-Hinweis-Chevrons:** dezente `ChevronLeft`/`ChevronRight` (12dp,
  40% Alpha) an den Zeilenrändern von `ArchivSongRow` und `SetSongRow`, nur
  sichtbar wenn die Wisch-Geste tatsächlich aktiv nutzbar ist (`interactive`-
  Flag). Macht das komplett unsichtbare Swipe-to-Queue-Feature entdeckbar.
- **Verstecktes Doppel-Tap-Verhalten entfernt:** `ArchivSongRow` hatte einen
  eigenen `combinedClickable` NUR auf dem Titel-Text, der bei bereits
  ausgewähltem Song Inline-Edit statt Play auslöste — unsichtbar, da visuell
  nicht vom Rest der Zeile unterscheidbar. Titel-Editing läuft jetzt
  ausschließlich über die bestehende `SongEditorSheet` (hat bereits ein
  "Titel"-Feld). Toter Code entfernt: `PlayerViewModel._editingSongId` /
  `editingSongId` / `startEditing()` / `stopEditing()`, `ArchivSongRow`-
  Parameter `isEditing`/`onTitleSave`/`onEditStart`, `FocusRequester` für
  Inline-Edit, ungenutzte Imports (`BasicTextField`, `SolidColor`,
  `KeyboardOptions`, `KeyboardActions`, `ImeAction`).
- **LOOP-Button-Hinweise:** `PlayerViewModel._loopHint` (einmaliger Toast,
  wird von `MainScreen` per `LaunchedEffect(loopHint)` angezeigt und danach
  geleert). Feuert bei Punkt-A-Setzen ("nochmal LOOP tippen für Punkt B"),
  beim Loop-Start ("nochmal tippen zum Beenden") und im Set-Modus wenn kein
  Loop gespeichert ist (vorher: stiller No-Op, kein Feedback).
- **Import-Fehlermeldung erklärt jetzt Modus A/B:** `importFolder()` in
  `PlayerViewModel` gibt bei 0 gefundenen Songs die zwei unterstützten
  Ordner-Layouts im Text aus, statt nur "Keine Songs gefunden".

**Gering:**
- Tab-Label-`contentDescription` "Playlist" → "Sets" (Terminologie-Konsistenz
  mit dem Rest der App).
- Empty-State fürs leere Archiv (Icon + Text), unterscheidet "noch nie
  importiert" vs. "Suche ohne Treffer" — vorher zeigte ein leeres Archiv ohne
  vorherigen Import-Versuch gar nichts an.
- `ArchivSongRow` Bearbeiten/Löschen-Icons jetzt echte `IconButton` (32dp)
  statt nacktem `Modifier.size(18.dp).clickable{}` — größeres Touch-Target.
- Bestätigungsdialog beim Entfernen eines Songs aus einem Set (`SetSongRow`),
  analog zu den anderen Lösch-Bestätigungen.
- `GenreBar` hat jetzt einen fünften Button ("+", `AddCircleOutline`) für ein
  freies Custom-Genre (kleiner Dialog mit Textfeld, ruft dieselbe `onGenre`
  wie die vier festen Chips).

**Reset-Verhalten (separater User-Wunsch, gleicher Commit):**
- `SetDao.resetCompletedForSet` setzt jetzt zusätzlich `isSpontaneous = 0`
  zurück (vorher nur `isCompleted`). `positionInSet` bleibt unangetastet —
  Songs bleiben exakt an der Stelle, an der sie gerade stehen, nur die
  "bereits gespielt"- UND "★ Wunsch"-Markierungen verschwinden.

### Sprint 5.28 DONE: Fix Completed-Reset traf immer nur das erste Set (Commit 661ed75)

`GigDetailView` hatte den Reset-Button (↻) im GIG-Header, der Code dahinter
resettete aber via `sets.firstOrNull()?.setId` IMMER nur Set 1 — bei Gigs mit
mehreren Sets wurde beim Betrachten von Set 2/3 lautlos das falsche Set
zurückgesetzt. Fix: Button in den Header von `SetCard` verschoben (später in
Sprint 5.29 ins "⋮"-Menü überführt), resettet jetzt gezielt nur das Set, bei
dem er steht.

### Sprint 5.27 DONE: Set-Umbenennen + Sortier-Modus für Sets im Gig (Commit 9278dc4)

Zwei User-Wünsche im selben Rutsch: Sets sollten umbenennbar sein, und die
Reihenfolge der Sets innerhalb eines Gigs sollte änderbar sein (nicht nur
Songs innerhalb eines Sets, siehe Sprint 5.26).

- **SetDao:** `renameSet(setId, name)`, `reorderSets(gigId, orderedSetIds)`
  (liest alle Sets eines Gigs einmal, schreibt EINMAL atomar — identisches
  Batch-Write-Pattern wie `reorderSongs`, siehe Gotcha 8/9).
- **GigViewModel:** `renameSet()`, `reorderSets()` — dünne Wrapper.
- **GigManagementScreen:** `CreateNameDialog` um `initialValue`/`confirmLabel`
  erweitert (gleicher Dialog für "Neues Set" UND "Set umbenennen", nur
  vorausgefüllt). Sets-Sortier-Modus im `GigDetailView`-Header (`SwapVert`-
  Toggle) verwendet exakt dasselbe Drag-Offset-Muster wie der Songs-
  Sortier-Modus aus Sprint 5.26, nur eine Ebene höher (`SetRowSortable`
  statt `SetSongRowSortable`, kompakte 64dp-Zeilen statt volle `SetCard`s
  während des Sortierens).
- Vom User live bestätigt ("Das funktioniert schon mal super").

### Sprint 5.26 DONE: Sortier-Modus für Songs im Set (Commit 332f52c)

User-Wunsch: Songs innerhalb eines Sets manuell umsortieren können (nicht nur
Spontan-Einfügung). Nach gemeinsamer Options-Diskussion (Drag-Handle vs.
Auf/Ab-Pfeile vs. Verschieben-Dialog) auf Drag-Handle mit dediziertem
Sortier-Modus entschieden — vom User live an der APK bestätigt: "funktioniert
schon mal super".

- **SetDao:** neue `reorderSongs(setId, orderedSongIds)` — liest alle CrossRefs
  einmal, mappt sie in der übergebenen Reihenfolge neu durch, schreibt EINMAL
  atomar über `updateRawCrossRefs` (identisches Pattern zu
  `moveSpontaneousNext/Later`, siehe Gotcha 8).
- **GigViewModel:** `reorderSongsInSet()` — Mutex-geschützt wie die anderen
  Queue-Mutationen, reloaded die Player-Queue wenn das aktive Set betroffen ist.
- **GigManagementScreen — SetCard:** neuer lokaler `sortMode`-State pro Set.
  Toggle-Button im Set-Header: `SwapVert`-Icon ("Sortieren") ⇄ `Check`-Icon
  ("Fertig", GigVolt-getönt) — Ein/Ausstieg immer eindeutig sichtbar.
  Schließt sich mit dem Gig-weiten Edit-Mode gegenseitig aus
  (`LaunchedEffect(isEditing)`). `BackHandler` beendet den Modus zusätzlich
  über die System-Zurück-Taste/-Geste, damit man nicht aus Versehen den
  ganzen Screen verlässt.
- **SetSongRowSortable (neu, separat von SetSongRow):** zeigt bei aktivem
  Sortier-Modus an ALLEN Zeilen gleichzeitig einen `DragIndicator`-Handle
  rechts. Ziehen verschiebt den Song live (animierte Nachbar-Zeilen, siehe
  Gotcha 9), Loslassen persistiert über `reorderSongsInSet`. Bewusst als
  eigene Composable gehalten statt in `SetSongRow` integriert, um den
  bestehenden (fragilen, siehe Gotcha 6) Swipe-Code nicht anzufassen.
- **Design-Entscheidungen aus der Diskussion:** Auf/Ab-Pfeile und
  Verschieben-Dialog wurden verworfen (zu viele Taps bzw. zu wenig "live"
  fürs Bühnen-Gefühl); 4-Buttons-Variante (▲▼⏫⏬) wegen Platzproblem auf
  72dp-Zeilen verworfen zugunsten des Drag-Handles.

### Sprint 5.25 DONE: PlayerInfoBar-Lesbarkeit + endAction-Reaktivität + Queue-Fix (Commit b237093)

Drei unabhängige Bugfixes aus APK-Test-Feedback:

**1. Schriftgrößen PlayerInfoBar (MainScreen.kt):**
- Zeitanzeige (Countdown): 20→14sp (war zu groß, hat Platz gestohlen)
- Songtitel (aktueller Song): 18→22sp (jetzt dominant, auf der Bühne gut lesbar)
- Nächster Song: 13→15sp (dezent größer)

**2. endAction-Button sofort reaktiv (MainScreen.kt + GigManagementScreen.kt):**
- PlayerInfoBar `onCycleEndAction`-Lambda setzt jetzt AUCH `vm.activeEndAction.value`
  direkt (neben DB-Update via `gigVm.cycleEndAction()`) → sofortige UI-Reaktion,
  kein Round-Trip über DB nötig
- SetSongRow Edit-Mode `cycleEndAction`-Button: setzt AUCH `playerVm.activeEndAction.value`
  wenn der geänderte Song der aktuell spielende ist → Edit-Modus und PlayerInfoBar bleiben synchron
- `addSongsToSet`/`deleteSongFromSet` übergeben jetzt `playerVm` → `reloadQueueFromSet()`
  wird aufgerufen → "Nächster Song"-Anzeige aktualisiert sich nach Playlist-Änderungen

**3. armSetIfIdle Queue-Fix — ROOT CAUSE CUE-Modus (GigViewModel.kt):**
- `armSetIfIdle` befüllte `_queue` NIE: nach `selectSong(first)` war die Queue leer.
  Wenn der erste Song endete, war `nextSong == null` → ExoPlayer blieb in REPEAT_MODE_ONE
  hängen und spielte endlos → CUE/STOP/AUTOPLAY wirkten alle gleich (kein Übergang).
- Fix: nach `selectSong(first.song)` werden alle verbleibenden ungespielten Songs
  per `playerVm.addToQueueEnd()` eingefügt — identisch zu `loadSetAsQueue`.
  Jetzt funktionieren alle drei Modi korrekt.

**Branch-Hygiene:**
- Alle alten `claude/`-Branches gelöscht (lokal + remote)
- `.claude/active-branch` = `main`, session-start-hook verifiziert
- Einzige Remote-Branches: `origin/main` und `origin/apk-dist`

### Sprint 5.24 DONE: Compact PlayerInfoBar + endAction Live-Button (Commit 1fa2af4)

Ziel: maximale Setlist-Sichtbarkeit auf der Bühne bei gleichzeitig perfekter
Bedienbarkeit der Info-Leiste.

**Änderungen in `GlobalPlayer` (MainScreen.kt):**
- Info Bar `padding vertical` 8→4dp (−8dp Gesamthöhe, mehr Platz für Setliste)
- `Spacer(4dp)` zwischen Titel und Nächste-Song-Zeile entfernt
- Countdown-Zeit: `fontFeatureSettings = "tnum"` — Breite flackert beim Ticken nicht
  mehr (tabular numerals)
- Nächste-Song-Icon: 14dp/Gray (war 16dp/White, dezenter)
- **Neuer endAction-Live-Button** (nur im GigSetMode, rechts in der Info Bar):
  - `Box(48dp)` Touch-Target nach Material-Mindestmaß, Icon 24dp zentriert
  - CUE (0) = hellblau ⏸, STOP (1) = rot ⏹, AUTOPLAY (2) = Volt-gelb ▶
  - Tippen → `gigVm.cycleEndAction(activeSetId, currentSong.id, activeEndAction)`
  - `activeEndAction` + `onCycleEndAction` neu in `GlobalPlayer`-Signatur

### Sprint 5.23 DONE: Q-List ENDGÜLTIG gefixt (Commit 6de5488)

Die Q-List (Wunschsong-Swipes) hatte über mehrere Builds hinweg das Symptom:
"funktioniert anfangs, wird mit Wiederholung random, trifft den falschen Song".
Alle vorherigen Fixes haben an der DB gearbeitet — der eigentliche Bug saß aber
in der Compose-Gesten-Ebene. Live vom User bestätigt: jetzt perfekt.

**ROOT CAUSE — Stale Lambda Capture in `pointerInput` (Commit 6de5488):**
- `detectHorizontalDragGestures` läuft in einem suspend-Block, der nur bei
  Key-Änderung (`isEditing` / `selectionMode`) neu startet — beim Swipen NIE.
- Dadurch waren `onQueueNext`/`onQueueEnd` + `songInSet` auf die ERSTE Komposition
  eingefroren. Nach jedem Umsortieren feuerte die Geste an Position X die Aktion
  für den ursprünglich dort gewesenen Song → zunehmend "random".
- In `ArchivSongRow` erklärt das eingefrorene `activeSetId` die früheren
  "landet im falschen Set"-Reports.
- **Fix:** `onQueueNext/onQueueEnd/onPlay` + completed-Flag in SetSongRow &
  ArchivSongRow über `rememberUpdatedState` → Geste nutzt immer Live-Werte.
- **Fix:** `SetCard` wrappt jede `SetSongRow` in `key(songInSet.song.id)`.

**Vorbereitende DB-Layer-Fixes (waren echte latente Bugs, nicht umsonst):**
- **Commit a5a212e:** Nested `@Transaction` eliminiert — `getSongsInSetOncePlain`
  (ohne `@Transaction`) für Aufrufe innerhalb anderer `@Transaction`-Methoden.
- **Commit 20311d3:** `PlayerViewModel.updateQueueAtomic()` ersetzt
  clearQueue+addToQueueEnd-Schleife (kein ExoPlayer-Buffer-Flush mehr);
  `trueCurrentSongId` wird innerhalb `queueMutex` aus `playerVm.currentSong.value`
  gelesen, nicht mehr aus dem (lagging) UI-State; UI-Aufrufe ohne currentSongId-Param.
- **Commit b824ac1 (Meisterstück):** `moveSpontaneousNext/Later` lesen alle
  CrossRefs einmal (`getRawCrossRefs`), sortieren in-memory um, schreiben EINMAL
  atomar (`updateRawCrossRefs` = `@Update` Batch). Links-Swipe fügt an der
  Q-Zonen-Grenze ein (nach Spontan-Songs, vor erstem regulären ungespielten Song),
  nicht mehr blind ans Set-Ende.

**Bonus-Fix:** FolderImporter behandelt WAV-only-Ordner (keine Unterordner)
korrekt als Einzel-Songs (Modus B), nicht mehr als einen Modus-A-Song (Commit 90f2dce).

### Sprint 5.22 DONE: Track-End Actions + Cockpit-UI + Q-List-Fix (CI #219)

- **DB v12→13:** `endAction INTEGER NOT NULL DEFAULT 0` in `set_song_cross_ref`
- **SongInSet:** `val endAction: Int` (0=CUE/arm, 1=STOP, 2=AUTOPLAY)
- **SetSongCrossRef:** 6. Feld `val endAction: Int = 0`
- **PlayerViewModel:** `val activeEndAction = MutableStateFlow(0)`;
  200ms-Poll reagiert mit `when(activeEndAction.value)`: CUE=arm, STOP=pause, AUTOPLAY=play
- **GigViewModel:** `cycleEndAction()` dreht (0→1→2→0); `armSetIfIdle` + `loadSetAsQueue`
  setzen `playerVm.activeEndAction` beim Laden; `onSongCompleted`-Callback aktualisiert
  `activeEndAction` für den nächsten Song
- **Cockpit-UI:** Aktiver Song in SetSongRow hellblau (GigVolt 18%) hinterlegt;
  abgespielte Songs auf alpha=0.30; Edit-Mode zeigt endAction-Button (⏸/⏹/▶▶)
- **Q-List-Fix:** Cut & Paste-Strategie in SetDao — Song wird zuerst gelöscht,
  dann Liste neu geladen, dann shift + re-insert → keine Selbstkollision mehr
- **_activeSetId-Bug:** `armSetIfIdle` setzt `_activeSetId` jetzt IMMER (vor dem
  Early-Return) — war Hauptursache für "Swipe funktioniert nur 1-2 Mal"
- **Branch-Hygiene:** Alle veralteten `claude/`-Branches gelöscht; `main` ist
  jetzt Default-Branch auf GitHub

### Master-Patch DONE: Mutex & firstRegular-Fix (CI #213)

- GigViewModel: `private val queueMutex = Mutex()` — serialisiert Swipe-Coroutinen;
  `insertSpontaneousNext/Later` komplett mit `queueMutex.withLock { }` umschlossen
- SetDao.`moveSpontaneousLater`: `firstRegular`-Filter schließt jetzt auch `songId`
  (den zu bewegenden Song selbst) aus — verhindert falsche Einfügeposition beim
  Set-internen Links-Swipe

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

- ✅ **Lyrics-Teleprompter Grundfunktion (ERLEDIGT):** Sprint 5.30 vom User live
  getestet — Auto-Scroll von oben nach unten funktioniert.
- ✅ **Struktur-Labels (ERLEDIGT):** Sprint 5.31 — `[Chorus]` etc. werden als
  eigene Volt-Überschrift gerendert. Nicht mehr offen.
- ✅ **Kern-Bug Kalibrierung (ERLEDIGT):** Der komplette Architektur-Neuentwurf
  in Sprint 5.36 (ScrollState/animateScrollTo entfernt) hat laut User-Report
  tatsächlich funktioniert — Kalibrierungspunkte werden jetzt korrekt
  gespeichert, kein Race-to-bottom mehr. Nicht mehr offen.
- ⚠️ **Sprint 5.37 (zwei Nebenwirkungen von 5.36) auf echtem Gerät testen
  (PRIO 1):** (1) Tap während Kalibrierung springt jetzt sofort sichtbar zur
  getippten Zeile (vorher kein Feedback, siehe dort). (2) Äußerste Box
  konsumiert jetzt jeden Tap, der keinen Button trifft — verhindert
  Durchfallen zur MainScreen-TopBar (löste vorher fälschlich deren
  "Alle Songs löschen"-Menü aus). Prüfen: Tap-Feedback während Kalibrierung
  sichtbar sofort, Stop→X schließt sauber ohne fremdes Menü zu öffnen,
  DB-Migration 15→16 weiterhin sauber, niemals Rückwärtssprung (kritisch
  laut User), Live-Tap-to-Sync funktioniert weiterhin.
- ✅ **Q-List (ERLEDIGT):** Swipes funktionieren jetzt zuverlässig — vom User live
  bestätigt ("es funktioniert perfekt!"). Root Cause war Stale Lambda Capture in
  `pointerInput` (Commit 6de5488). Nicht mehr offen.
- ✅ **armSetIfIdle Set-Lag (ERLEDIGT, Commit 7e92dff):** `_activeSetId` und
  `onSongCompleted`-Callback werden jetzt immer gesetzt (nicht erst nach dem
  Early-Return). Callback liest `_activeSetId.value` zur Laufzeit statt
  eingefrorenem `setId`. Gleiches Muster konsistent in `loadSetAsQueue`.
- ❌ **Follow-Me Gear (VERWORFEN):** User braucht das nicht mehr — bewusst nicht
  bauen, nicht wieder vorschlagen.
- ✅ **endAction Live-Button (ERLEDIGT):** In der PlayerInfoBar rechts (GigSetMode),
  48dp Touch-Target, Farb-kodiert, tippar zum Durchschalten (Commit 1fa2af4)
- ✅ **endAction Reaktivität (ERLEDIGT):** Button in PlayerInfoBar + Edit-Mode setzt
  `vm.activeEndAction.value` sofort (nicht nur DB) → keine Verzögerung mehr (Commit 2929298)
- ✅ **CUE-Modus / armSetIfIdle Queue-Fix (ERLEDIGT):** `armSetIfIdle` befüllt jetzt
  korrekt die Queue → alle drei Modi (CUE/STOP/AUTOPLAY) funktionieren (Commit b237093)
- ✅ **Songs im Set umsortieren (ERLEDIGT):** Sortier-Modus mit Drag-Handle,
  vom User live an der APK bestätigt ("funktioniert schon mal super",
  Commit 332f52c). Nicht mehr offen.
- ✅ **Set-Umbenennen (ERLEDIGT):** Über "⋮"-Menü im SetCard-Header
  (Commit 9278dc4, UI später in Sprint 5.29 ins Dropdown überführt).
- ✅ **Sets umsortieren (ERLEDIGT):** Gleicher Drag-Handle-Mechanismus wie
  bei Songs, eine Ebene höher (Commit 9278dc4).
- ✅ **UX-Audit + alle 13 Befunde (ERLEDIGT):** Vollständiger Review aller
  UI-Screens, Report als Artifact, danach komplett umgesetzt (Commit b5732f5,
  siehe Sprint 5.29). Details siehe dort — inkl. Lösch-Bestätigungen,
  Performance-Lock im Gig-Set-Tab, Wisch-Hinweise, LOOP-Toasts u.a.
- ✅ **Song-zu-Set direkt im UI (ERLEDIGT):** Neuer Menüpunkt "Songs hinzufügen"
  im "⋮"-Menü der `SetCard` öffnet `AddSongsToSetDialog` (Suchfeld + Checkbox-Liste
  aller Archiv-Songs, die noch nicht im Set sind), ruft `gigVm.addSongsToSet()`.
  Bisheriger Weg über den Archiv-Batch-Dialog bleibt zusätzlich bestehen.
- **Aufräumen toter Code:** `SetDao.updateSongPosition`, `sanitizeSetPositionsInternal`
  und Reste der alten Shift-Strategie prüfen, ob noch gebraucht (Batch-Write hat sie
  größtenteils ersetzt). Nicht löschen ohne Nutzungs-Check (z.B. addSongsToSet,
  deleteSongFromSet rufen sanitize noch auf). Kleiner Teilerfolg in Sprint 5.29:
  `PlayerViewModel.editingSongId`/`startEditing`/`stopEditing` (unbenutzter
  Inline-Edit-Mechanismus) bereits entfernt.

### Loop-Editor — Archiviert

Der visuelle Waveform-Loop-Editor (LoopEditorScreen / LoopEditorViewModel) ist endgültig
verworfen — das Konzept wird nicht neu aufgegriffen. Die bestehende Lösung
(A/B-Tippen im Player + LoopPanel mit Nudge/Save) ist die finale Implementierung.
