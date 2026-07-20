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
| lyricsLeadMs | Long | (v17) Reserviert/unbenutzt — war Vorlauf-Regler (Sprint 5.45), abgelöst durch Abschnitts-Modell/Oben-Anker (5.46) |

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

1. **Room v17** — nächste Migration wäre 17→18. Migrationen NIE doppelt anlegen.
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
    ScrollState, EIGENES `Layout`** — `LyricsOverlay.kt` scrollt proportional zu
    `positionMs / durationMs` (echte Player-Position), nicht über eine
    BPM-Rechnung. Dadurch ist der Song immer exakt zu Ende gescrollt, wenn er
    zu Ende gespielt ist — unabhängig davon, ob die in der DB hinterlegte BPM
    stimmt.
    **WICHTIG (Sprint 5.36–5.38, nach fünf gescheiterten Fix-Versuchen):**
    Der Text-Block wird NICHT über `Modifier.verticalScroll()`/`ScrollState`
    UND NICHT über ein simples `Box(fillMaxSize) { Column(fillMaxWidth) }`
    gescrollt, sondern über ein eigenes `Layout`-Composable, das die
    Content-Column explizit mit `constraints.copy(maxHeight =
    Constraints.Infinity)` misst und selbst per `placeable.placeRelative(0,
    -scrollOffsetPx…)` platziert. **Bei künftigen Änderungen an diesem Screen
    NIEMALS folgendes wieder einführen:**
    - `ScrollState`/`verticalScroll`/`scrollTo`/`animateScrollTo` — Grund
      (Sprint 5.36): `ScrollState.scrollTo()` (Frame-Loop, jeden Frame) und
      `ScrollState.animateScrollTo()` (früher in `handleTap()`) konkurrieren
      um dieselbe interne `MutatorMutex` und unterbrechen sich gegenseitig.
    - Ein simples `Box(fillMaxSize) { Column(fillMaxWidth) }` als Viewport/
      Content-Struktur — Grund (Sprint 5.38): eine `Box` reicht ihre eigene,
      durch den Viewport begrenzte `maxHeight`-Constraint automatisch an ihre
      Kinder weiter. Die Content-Column konnte dadurch NIE höher gemessen
      werden als der sichtbare Ausschnitt selbst — `maxScrollPx` blieb
      strukturell ~0, die Frame-Loop wartete für immer auf Scroll-Bedarf, der
      nie kam. Kompletter Stillstand, unabhängig von Taps — kein Timing-Bug,
      sondern garantiert reproduzierbar bei jedem Öffnen. Das eigene `Layout`
      erfasst Viewport-Höhe (`constraints.maxHeight`) und Content-Höhe
      (`placeable.height`) direkt in der Messphase, `maxScrollPx` wird direkt
      daraus berechnet — kein Verlass mehr auf automatische
      Constraint-Weitergabe.
    Es gibt nur EINEN Schreiber für die kontinuierliche Vorwärtsbewegung von
    `scrollOffsetPx` (die Frame-Loop); `handleTap()` setzt den Anker UND
    springt zusätzlich sofort sichtbar dorthin (Sprint 5.37 — ohne den
    Sofort-Sprung bewegt sich während einer laufenden Kalibrierung ohne
    vorhandene Kalibrierungspunkte zwischen zwei Taps nur ein Bruchteil-Pixel,
    weil die Frame-Loop dann noch mit "Rest des ganzen Songs bis zum Ende" als
    Zielspanne rechnet). Das ist sicher — anders als bei der alten
    `ScrollState`-Version handelt es sich um eine simple, monoton geklemmte
    Zuweisung ohne Animate-Aufruf oder konkurrierende Coroutine, exakt wie in
    der Frame-Loop selbst. Zwei Garantien, beide bewusst doppelt abgesichert:
    - **Nur abwärts, nie zurück:** `scrollOffsetPx` wird ausschließlich über
      `if (neuerWert > scrollOffsetPx) scrollOffsetPx = neuerWert` erhöht — sowohl
      im Frame-Loop als auch bei Tap-to-Sync. Ein kurzzeitiger Jitter in der
      Positionsschätzung (oder ein Rückwärts-Seek) kann den Scroll dadurch
      NIEMALS nach oben reißen, er bleibt höchstens stehen.
    - **Tap-to-Sync verschiebt den Anker, nicht nur die Zeile fix:** Tap sucht
      per `linePositions` (gemessen via `onGloballyPositioned`, siehe unten) die
      nächste noch nicht erreichte Zeile, setzt `anchorPositionMs`/`anchorScrollPx`
      auf (jetzt, diese Zeile) — die Frame-Loop rechnet die Scroll-Rate für den
      Rest des Songs ab diesem neuen Anker neu — kompensiert damit automatisch
      ungleichmäßige Zeilendichte (Strophe vs. Instrumental-Teil vs. Refrain).
    - **`allLinesMeasured`-Gate:** Die Frame-Loop tut buchstäblich nichts
      (`continue`), bevor `linePositions.size >= nonBlankLineCount` UND
      Viewport-/Content-Höhe beide > 0 sind — eliminiert die Fehlerklasse
      "teilweise vermessenes Layout beim (Wieder-)Öffnen" strukturell, statt sie
      Fall für Fall mit Nullable-Checks abzufangen.
    - Frame-Loop (`withFrameNanos` in einer Endlosschleife) treibt
      kontinuierliches 60fps-Scrollen — keine separate Animation mehr, die mit
      der Loop um denselben Zustand konkurrieren könnte.
    - `linePositions[index]` wird NICHT analytisch aus Zeilenhöhe berechnet
      (bricht bei Zeilenumbruch auf schmalen Screens), sondern real gemessen —
      robust unabhängig von Fontgröße/Gerätebreite. `positionInParent()` ist
      relativ zur Text-Column selbst (nicht zum Screen) und bleibt dadurch
      stabil, auch während die Column per `placeRelative` bewegt wird.
    - **Touch-Durchfall zur MainScreen-TopBar (Sprint 5.37):** Der
      Schließen-Button (X) sitzt an fast derselben Bildschirmposition wie das
      "⋮"-Menü der `TopBar` dahinter (beide oben rechts, direkt unter dem
      Statusbalken). Die äußerste Box des Screens hat deshalb einen leeren
      `detectTapGestures {}`-Handler, der jeden nicht anderweitig konsumierten
      Tap abfängt, statt ihn zur TopBar durchfallen zu lassen — Compose testet
      Kind-Elemente (Buttons, Tap-to-Sync-Viewport) zuerst, deren Handler
      bleiben also unangetastet.
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
    - **LESE-UHR-MODELL — Zwei-Phasen-Scroll (Idee 1, Sprint 5.48, LÖST das
      Mitte-Driften):** Der Scroll läuft NICHT mehr linear über die Pixel,
      sondern nach einer "Lese-Uhr". Vorberechnet: `lineIsLyric[i]` (echte
      Textzeile = nicht leer UND kein `[Label]`) und `lineWeight[i]`
      (Zeichenzahl bei Gesang, sonst 0). Pro Segment (zwischen zwei Ankern,
      `anchorLineIdx`→`segEndLine`): `segW = weightBetween(...)`.
      **Phase 1 (Lesen):** solange `elapsed < readDuration` (=`segW ×
      naturalPace`, gekappt auf Segmentzeit) laufen die Gesangszeilen im
      Sing-Tempo — `readingPixel()` verbraucht Gewicht und bleibt auf jeder
      Zeile ∝ ihrer Länge stehen. **Phase 2 (Warten):** Rest der Segmentzeit
      (= Instrumental-Ausklang/Solo/Intro) gleitet der Scroll sanft von der
      Lese-End-Position zum nächsten Anker. Dadurch wird Instrumental-Zeit
      AUSGESESSEN statt über die Gesangszeilen geschmiert (= die Ursache des
      Mitte-Driftens aus 5.47). `naturalPace` (ms/Gewicht) wird EINMAL beim
      Loop-Start aus den dichtesten Segmenten gelernt (~20. Perzentil der
      time-per-weight über "volle" Segmente ≥40% des größten) — ein eher
      schnelles Tempo lässt Zeilen minimal zu FRÜH oben ankommen (gut zum
      Vorlesen). **Folge fürs Tappen:** nur noch ein Tap pro ABSCHNITTS-Anfang
      nötig; Intro/Solo werden von Phase 2 automatisch abgefangen (kein Tap).
      **Messung:** jetzt werden ALLE Zeilen inkl. Leerzeilen vermessen
      (`onGloballyPositioned` auch auf den Blank-Spacern) plus ein Sentinel bei
      Index `lines.size` (Oberkante des End-Platzhalters) — damit hat auch die
      letzte Zeile eine gemessene Unterkante für `readingPixel`. `handleTap()`
      verankert nur noch echte Gesangszeilen (`lineIsLyric`-Filter). Oben-Anker
      (5.46) und Monoton-Klemmung bleiben unverändert.
    - **Strukturelle Instrumental-Erkennung — löst falsche Wartezeiten (Sprint
      5.51):** Nach dem ersten vollständig auswertbaren Log (Bed of Roses) zeigte
      sich: ein GLOBALES `naturalPace` ist zu grob — 3 von 6 Segmenten hatten
      12–19s rechnerische "Wartezeit" (Phase 2), obwohl dort vermutlich kein
      echtes Instrumental liegt, nur langsamerer Gesang als im Referenz-Segment.
      Fix, bewusst OHNE Keyword-Abgleich ("Solo"/"Intro"/…, fehleranfällig/
      sprachabhängig): `isInstrumentalLabel[i]` markiert ein Label strukturell
      als instrumental, wenn bis zum nächsten Label (oder Songende) keine
      einzige Gesangszeile folgt — nutzt nur die bestehende Text-Konvention aus.
      `segmentHasInstrumental(from, to)` prüft ein Segment darauf. **Neue Regel:**
      nur Segmente MIT erkanntem Instrumental-Label bekommen weiterhin das
      Phase-1(Lesen)/Phase-2(Warten)-Modell mit dem globalen `naturalPace`;
      Segmente OHNE Instrumental-Label verteilen ihre volle Segmentzeit mit dem
      SEGMENT-EIGENEN lokalen Tempo (`segT/segW`) gleichmäßig auf die eigenen
      Zeilen — keine künstliche Wartezeit mehr. Die `naturalPace`-Lernschleife
      schließt Segmente mit Instrumental-Anteil ebenfalls aus (sonst würde deren
      Warte-Zeit-Anteil das gelernte Tempo verzerren). Diagnose-Log zeigt jetzt
      zusätzlich `instrumental=true/false/null` pro Segment.
    - **Recalibration-Isolation (Sprint 5.47):** Während `calibrating == true`
      treibt die ALTE gespeicherte Kalibrierung den Auto-Scroll NICHT
      (`useBreakpoints = !calibrating` → keine Anker-Weiterschaltung, kein
      nextBreak, nur langsamer Drift ab dem letzten Tap). Sonst kämpfen altes
      Auto-Scrolling und frische Taps gegeneinander und `handleTap()` zeichnet
      Zeilen relativ zur alten, falschen Scroll-Position auf. Beim Nicht-
      Kalibrieren identisches Verhalten wie zuvor.
    - **Kalibrierung übersteht Songwechsel während der Aufnahme (Sprint 5.49,
      DATENVERLUST-BUG):** `calibrating`/`calibrationPoints` sind
      `remember(song.id, openSession)`-gebunden — läuft der Song während einer
      laufenden Kalibrierung zu Ende und die App armt lautlos den nächsten Song
      (CUE-Modus, siehe Sprint 5.22/5.43), wechselt `song.id`, und der
      komplette Kalibrierungs-Zustand wird verworfen, BEVOR ein manueller
      Stop-Tap etwas speichern konnte — alle Taps sind ersatzlos weg, die App
      fällt still auf die alte gespeicherte Kalibrierung zurück (vom User live
      reproduziert: "ich habe neu kalibriert, aber es hat nicht funktioniert").
      Fix: `DisposableEffect(song.id) { onDispose { ... } }` — `onDispose`
      feuert exakt in dem Moment, in dem der ALTE `song.id`-Kontext (samt der
      darin gefangenen `calibrationPoints`-Closure) durch den neuen ersetzt
      wird; dort wird, falls `calibrating` noch aktiv war, sofort mit den noch
      vorhandenen alten Werten gespeichert — kein Datenverlust mehr, unabhängig
      davon, ob der User rechtzeitig Stop drückt.
    - **Struktur-Labels ohne Akkorde:** Zeilen im Format `[Chorus]`/`[Verse 1]`
      im Lyrics-Text werden per `sectionTagRegex` erkannt und als eigene,
      Volt-farbene Überschrift gerendert (nicht als normale weiße Lyric-Zeile)
      — bewusst NICHT aus dem Text entfernt wie Akkord-Zeilen, weil es reine
      Songstruktur ist, kein Akkord.
    - **Abschnitts-Modell / Oben-Anker (Sprint 5.46, ERSETZT den festen 30%-
      Lesepunkt aus 5.39):** Der Content-Placeable wird im `layout{}`-Block ab
      `-scrollOffsetPx` platziert — d.h. bei `scrollOffsetPx == Pixel eines
      Abschnitts-Anfangs` steht dieser exakt am OBEREN Bildschirmrand. Genau das
      peilt die Kalibrierung an: jeder Abschnitts-Anfang erreicht zu seinem
      kalibrierten Zeitpunkt den oberen Rand, dazwischen gleitet der Text mit der
      pro Segment berechneten Rate (jeder Abschnitt hat so seine eigene
      Geschwindigkeit). **Warum der 30%-Lesepunkt (5.39) WEG ist:** er rückte über
      die gerade gesungene Zeile permanent ~15 Zeilen (0.3 × Viewport / Zeilenhöhe)
      BEREITS GESUNGENEN Text — genau dorthin, wo man beim Singen instinktiv
      hinschaut. Das war die strukturelle Ursache des "hinkt hinterher"-Gefühls,
      unabhängig von Rate/Interpolation. Oben-Anker beseitigt das: oben steht das
      Aktuelle, darunter nur Kommendes. Reine Platzierungsänderung —
      `scrollOffsetPx` (Rate pro Segment, Monoton-Klemmung, `handleTap()`) bleibt
      unverändert. `ANCHOR_FRACTION` und die nicht-scrollende Indikator-Linie aus
      5.39 wieder entfernt; das `Layout` bleibt in der `Box(fillMaxSize())`
      gewrappt (harmlos, bekannt gut vermessend).
    - **Diagnose-Log lebt im ViewModel, nicht in `LyricsOverlay.kt` (Sprint 5.43):**
      `PlayerViewModel.lyricsDebugLog` (`MutableList<String>`) statt
      `remember(song.id, openSession)` in `LyricsContent` — Grund: CUE-Modus
      (`endAction=0`) arm't beim Auto-Advance den NÄCHSTEN Song bereits in
      `currentSong`/die Audio-Engine, OHNE ihn abzuspielen (`isPlaying=false`,
      siehe Sprint 5.22/5.43). Bleibt der Lyrics-Screen nach Songende offen,
      folgt er `currentSong` auf diesen ungehörten, nur georarmten Song — ein
      song-gebundener Log wäre in genau diesem Moment verloren gegangen, bevor
      der User ihn teilen konnte. Der ViewModel-Log überlebt jeden Songwechsel;
      jede Zeile trägt weiterhin den Songtitel, dadurch bleibt bei mehreren
      Songs im selben Log nachvollziehbar, welche Zeile zu welchem Song gehört.
    - **Positions-Hochrechnung zwischen 200ms-Polls (Sprint 5.44):** `positionMs`
      kommt aus `PlayerViewModel` und wird dort nur alle 200ms aktualisiert
      (Poll-Loop, `delay(200L)`), der Scroll-Frame-Loop läuft aber mit 60fps.
      Ohne Hochrechnung friert `pos` für ~12 von 12 Frames ein und springt dann
      sprunghaft nach — sichtbar als "Treppenstufen" statt smooth, wirkte für
      den User wie "läuft konstant und hinkt hinterher" (bestätigt per Live-
      Test: Text kam beim Mitsingen nicht hinterher, obwohl die Segment-Raten
      im Diagnose-Log nachweislich korrekt unterschiedlich waren). Fix:
      `estimatedPositionMs()` verankert den letzten ECHTEN 200ms-Messwert
      (`lastRawPositionMs`/`lastRawPositionAtNs`) und rechnet dazwischen per
      realer Systemzeit linear hoch (`System.nanoTime()`, gekappt auf max.
      400ms Overshoot) — der nächste echte 200ms-Wert korrigiert die
      Hochrechnung automatisch, kein Drift. Läuft nur während `isPlaying`;
      beim Pause→Play-Übergang wird der Anker sofort neu verankert (kein
      Overshoot durch einen alten, vor der Pause liegenden Zeitstempel). Auf
      Composable-Ebene gehalten (nicht lokal im `LaunchedEffect`), damit
      sowohl die Frame-Loop als auch `handleTap()` (Kalibrierungs-Aufnahme)
      dieselbe Schätzung verwenden — sonst wären Kalibrierungspunkte leicht
      verrauscht relativ zur Playback-Darstellung.
    - **Einstellbarer Vorlauf `song.lyricsLeadMs` (Sprint 5.45, Room v17) —
      ZURÜCKGENOMMEN in 5.46:** War ein −/+ Knopf im Header, der eine konstante
      Zeit-Verschiebung auf die Playback-Position addierte, um die beim
      Kalibrieren eingebackene Reaktionszeit zu kompensieren. Der User wollte
      keinen Regler-Kram, sondern das Abschnitts-Modell (Oben-Anker, siehe oben).
      UI + Anwendung wieder entfernt; die DB-Spalte `lyricsLeadMs` (v17) bleibt
      reserviert/unbenutzt bestehen (ADD-only-Konvention, Gerät ist bereits auf
      v17 — analog `lyricsStartMs`). Falls sich nach 5.46 doch noch ein winziger
      konstanter Versatz zeigt, ist das die naheliegende, sauber wieder
      aktivierbare Feinjustierung.

## Letzter Stand

**Datum:** 2026-07-20
**CI Build:** noch nicht gepusht — lokal implementiert
**Branch:** `main` (einziger Branch; alle claude/-Branches bereinigt, main = Default)
**Commit:** Lese-Uhr wartet nur noch in Segmenten mit echtem Instrumental-Label, sonst lokales statt globales Tempo

### Sprint 5.51 DONE (ungetestet): Strukturelle Instrumental-Erkennung — behebt falsche "Wartezeiten"

Mit dem jetzt vollständigen Diagnose-Log (Sprint 5.50-Fix hat funktioniert) konnte
das Lese-Uhr-Modell zum ersten Mal wirklich durchgerechnet werden. Ergebnis: die
Segment-Mathematik war korrekt — aber 3 von 6 Segmenten hatten rechnerisch 12–19
Sekunden "Wartezeit" (Phase 2), obwohl dort vermutlich KEIN echtes Instrumental
liegt (z.B. Vers-Ende → Chorus-Anfang). Ursache: das global gelernte `naturalPace`
(vom dichtesten Referenz-Segment) war für diese langsameren Segmente zu schnell —
der Text "liest" dort zu früh fertig und "parkt" dann künstlich, bis der nächste
Tap kommt. Nur 1 Segment (Bridge → Solo → Verse 4) hatte plausibel eine echte
Instrumental-Pause. User-Wunsch: keine zusätzlichen Taps, stattdessen eine
intelligentere Erkennung — explizit KEIN Keyword-Abgleich ("Solo"/"Intro"/…, zu
fehleranfällig/sprachabhängig), sondern strukturell.

**Umsetzung:**
- `isInstrumentalLabel: BooleanArray` (parse-zeit, pro Song einmalig): ein Label
  gilt als instrumental, wenn bis zum nächsten Label (oder Songende) keine
  einzige Gesangszeile folgt — nutzt nur die ohnehin geltende Text-Konvention
  (reine Instrumental-Teile als eigenes Label OHNE Textzeilen) aus, funktioniert
  unabhängig vom Wortlaut/der Sprache des Labels.
- `segmentHasInstrumental(fromLine, toLine)`: prüft, ob ein Segment ein solches
  Label enthält.
- **Neue Segment-Regel im Frame-Loop:** enthält das Segment kein Instrumental-
  Label, aber `segW > 0`, wird NICHT mehr Phase 1/Phase 2 mit dem globalen
  `naturalPace` gerechnet, sondern die volle Segmentzeit mit dem
  SEGMENT-EIGENEN (lokalen) Tempo (`segT/segW`) gleichmäßig auf die eigenen
  Zeilen verteilt — keine künstliche Wartezeit mehr. Nur bei erkanntem
  Instrumental-Label bleibt das bisherige Phase-1(Lesen)/Phase-2(Warten)-Modell
  bestehen (dort ist eine Wartezeit inhaltlich korrekt).
- **`naturalPace`-Lernschleife ebenfalls angepasst:** Segmente mit erkanntem
  Instrumental-Anteil werden von der Tempo-Schätzung ausgeschlossen (ihre Zeit
  enthält Warte-Anteile, die das gelernte Sing-Tempo sonst verzerren würden).
- Diagnose-Log zeigt jetzt zusätzlich `instrumental=true/false/null` pro Segment.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich. Statisch
  geprüft (Klammerbalance, Symbole) UND von Hand gegen die echten Log-Zahlen aus
  Bed of Roses durchgerechnet — die 3 auffälligen Segmente (19→29, 29→45, 45→58)
  sollten jetzt ohne künstliche Wartezeit laufen, das Bridge→Solo-Segment (58→65)
  sollte weiterhin korrekt warten. **Nächste Session: live testen** — bestehende
  Kalibrierung reicht (kein Neu-Tippen nötig), Song einmal durchlaufen lassen,
  fühlt es sich jetzt gleichmäßig an statt "hängenbleibend"? Log prüfen, ob
  `instrumental=true` nur beim Solo-Segment auftaucht.

### Sprint 5.50 DONE: Logging-Lücke behoben — "Segment:"-Zeile feuerte nur zufällig

### Sprint 5.50 DONE (Diagnose, kein Fix): Logging-Lücke behoben — "Segment:"-Zeile feuerte nur zufällig

Nach dem 5.49-Fix (Datenverlust) hat der User erfolgreich neu kalibriert — der Log
zeigte diesmal korrekt FRISCHE Breakpoints (7 Punkte statt der alten 11), der Fix
wirkt also. Trotzdem enthielt der geteilte Log wieder keine einzige "Segment:
..."-Zeile, obwohl der Song bis zum Ende durchgesungen wurde. Per Rückfrage
bestätigt: der Log wurde korrekt direkt über den Share-Button geteilt (kein
Abtippen/Kürzen durch den User).

**Root Cause (echte Logging-Lücke, im Code verifiziert):** Die `onLogDebug("Segment:
...")`-Zeile stand bisher NUR im Phase-1-Zweig (`if (elapsed < readDuration)`).
Direkt nach dem Speichern der Kalibrierung (Stop-Tap kurz vor Songende) startet die
Auswertung neu (`LaunchedEffect` neu gekeyt durch geändertes `song.lyricsSyncPoints`)
— und wenn `pos` zu diesem Zeitpunkt schon weit fortgeschritten ist, werden beim
Neustart ggf. mehrere/alle Breakpoints in einem einzigen Frame aufgeholt und die
Auswertung landet direkt in Phase 2, ohne dass die Log-Zeile in diesem Frame je
Phase 1 durchläuft — der Wechsel passiert real, wird aber nicht geloggt. Zusätzlich
plausibel: Falls nach dem Stop-Tap kein nennenswertes weiteres Playback mehr
stattfand, gab es schlicht keine weiteren echten Abschnittswechsel mehr, die das
Log hätten füllen können.

**Fix:** Die Log-Zeile steht jetzt VOR der Phase-1/Phase-2-Verzweigung und feuert
bei jedem `segmentJustChanged`, unabhängig davon, in welcher Phase der aktuelle
Frame gerade landet. Zusätzlich wird jetzt auch bei "kein Modell"/"reines
Instrumental-Segment" (`segW<=0`) geloggt (vorher gar nicht), erkennbar an
`segW=-1` bzw. `segW=0` in der Log-Zeile.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich. **Nächste
  Session:** sauberer Test — Bed of Roses frisch kalibrieren, Song bis zum Ende
  laufen lassen, DANACH den Song nochmal von vorne abspielen (damit die neue
  Kalibrierung unter realer Wiedergabe mehrfach durchläuft), dann den kompletten
  Log senden. Erst mit "Segment:"-Zeilen für alle 6 Übergänge lässt sich die
  Lese-Uhr wirklich beurteilen.

### Sprint 5.49 DONE: Fix — Datenverlust-Bug: Kalibrierung ging verloren, wenn der Song während der Aufnahme zu Ende lief

### Sprint 5.49 DONE (ungetestet): Fix — Kalibrierung übersteht Songwechsel während der Aufnahme

User-Report nach 5.48 (Lese-Uhr): beim Live-Test wirkte alles "willkürlich" — Intro
lief weg, Übergänge sprangen mal, blieben mal "in der Mitte" stehen, kein
erkennbares Muster. Auf Nachfrage (Diagnose-Log) zeigte sich: die geloggten
Breakpoints waren **exakt die uralten 11 Punkte aus einer Kalibrierung von vor
mehreren Sprints** — obwohl der User für diesen Test ausdrücklich neu kalibriert
hatte ("ich habe aber neu kalibriert, dann hat das Log nicht funktioniert").

**Root Cause gefunden (echter Datenverlust-Bug, kein Lese-Uhr-Logikfehler):**
`calibrating`/`calibrationPoints` sind `remember(song.id, openSession)`-gebunden.
User bestätigt: der Song lief beim Kalibrieren bis ganz zum Ende durch. Dabei
armt die App im CUE-Modus lautlos den nächsten Song (Sprint 5.22/5.43) —
`song.id` wechselt, und der GESAMTE Kalibrierungs-Zustand (alle bereits
getippten Punkte) wird dabei verworfen, BEVOR ein manueller Stop-Tap sie
speichern konnte. Die App fiel danach still auf die alten, längst überholten
Kalibrierungsdaten zurück. Diese alten Daten hatten zudem eine andere
Zeilen-Granularität (Punkt 1 auf Zeile 4 statt der ersten Verszeile) — von Hand
durchgerechnet ergibt das für das allererste Segment: nur eine Zeile Gewicht
vor einem 22-Sekunden-Break → Lese-Phase dauert bei gelerntem Tempo nur ~5s,
die restlichen ~17s "parkt" der Text bereits an der Zielposition. Das erklärt
plausibel, wie sich beides zusammen als "wirkt willkürlich" angefühlt haben kann
— OHNE dass die Lese-Uhr-Logik selbst falsch wäre.

**Fix:** `DisposableEffect(song.id) { onDispose { ... } }` in `LyricsContent` —
`onDispose` feuert exakt in dem Moment, in dem der alte `song.id`-Kontext durch
den neuen ersetzt wird. Ist `calibrating` zu diesem Zeitpunkt noch aktiv, werden
die (in der Closure noch vorhandenen) alten `calibrationPoints` sofort
gespeichert, bevor sie weg sind — kein Datenverlust mehr, unabhängig davon, ob
der User rechtzeitig manuell Stop drückt. Zusätzliche Warn-Logzeile im
(jetzt persistenten, Sprint 5.43) Diagnose-Log macht diesen Fall künftig sofort
sichtbar. Siehe Gotcha 12.

**Wichtig:** Die Lese-Uhr-Logik selbst (Sprint 5.48) wurde NICHT angefasst — der
Verdacht auf ein Problem bei sehr kurzen/isolierten Segmenten (wie dem
Intro-Segment mit nur einer Zeile Gewicht) bleibt vorerst unbestätigte Theorie,
da der Test mit falschen (alten) Daten lief. Braucht einen sauberen Retest mit
garantiert frisch gespeicherten Daten.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich (nur
  statisch geprüft: Klammerbalance, DisposableEffect-Closure-Verhalten von Hand
  durchdacht). **Nächste Session: sauber retesten** — Bed of Roses neu
  kalibrieren (ein Tap pro Abschnitts-Anfang inkl. der allerersten Gesangszeile
  nach der Intro, Intro/Solo selbst überspringen), Song bis zum Ende laufen
  lassen (testet automatisch den Fix), danach den KOMPLETTEN Diagnose-Log per
  Share schicken (inkl. aller "Segment: ..."-Zeilen, nicht nur die Start-Zeile)
  — erst mit dieser sauberen Datenbasis lässt sich beurteilen, ob die
  Intro-/Sprung-Beobachtungen durch die Lese-Uhr selbst verursacht wurden.

### Sprint 5.48 DONE (ungetestet): Lese-Uhr-Modell (Idee 1) — Instrumental-Zeit aussitzen statt schmieren

User nach 5.46/5.47-Tests: das Oben-Anker-Modell sitzt an den Kalibrierungspunkten
exakt, aber die zu singenden Zeilen landeten im MITTLEREN Bereich und die kurze Intro
lief davon. **Nach Analyse des formatierten Bed-of-Roses-Texts + der echten
Breakpoints die Wurzel gefunden:** Das lineare Scrollen über die PIXEL verteilt die
Zeit der Instrumental-Teile ([Intro], [Solo]) und der Ausklänge auf die Gesangszeilen
— die haben viel Zeit, aber kaum Pixel, ziehen also die Scroll-Geschwindigkeit runter
→ Gesangszeilen kriechen zu langsam (Mitte). Der User wollte KEIN Viel-Tappen; er bat
um geniale, einfache Lösungen und eine immer anwendbare Routine. Idee 1 ("Lese-Uhr")
wurde gemeinsam geplant, Denkfehler ausgemerzt, dann freigegeben.

**Umsetzung (siehe Gotcha 12, Abschnitt "Lese-Uhr-Modell"):**
- Vorberechnung `lineIsLyric` / `lineWeight` (Zeichenzahl bei Gesang, sonst 0).
- Zwei-Phasen-Scroll pro Segment: Phase 1 (Lesen im gelernten `naturalPace`),
  Phase 2 (Instrumental/Ausklang sanft aussitzen bis zum nächsten Anker).
- `naturalPace` (ms/Gewicht) wird aus den dichtesten Segmenten gelernt (~20.
  Perzentil), einmal beim Loop-Start.
- ALLE Zeilen werden jetzt vermessen (auch Leerzeilen-Spacer) + Sentinel bei
  Index `lines.size` → `readingPixel()`/`weightBetween()` haben saubere Pixel.
- `handleTap()` verankert nur echte Gesangszeilen; `anchorLineIdx` neu.
- Kalibrier-Anleitung + KDoc: nur noch ein Tap pro ABSCHNITTS-Anfang, Intro/Solo
  überspringen (Phase 2 fängt sie ab). Oben-Anker (5.46), Monoton-Klemmung,
  60fps-Positions-Hochrechnung (5.44), Recalibration-Isolation (5.47): unverändert.

**Text-Routine (immer anwendbar):** jeder Abschnitt ein Label ([Intro], [Verse n],
[Chorus], [Bridge], [Solo], [Outro]); reine Instrumental-Teile als eigenes Label
ohne Textzeilen; eine Gesangsphrase pro Zeile. Der bestehende Bed-of-Roses-Text
erfüllt das bereits — kein Neu-Formatieren nötig.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session (Google-Maven 403).
  Statisch geprüft: Klammerbalance (nur Real-Code), alle Symbole konsistent,
  keine verwaisten Referenzen, Phasen-/Anker-Logik von Hand durchgerechnet
  (Intro & Solo werden von Phase 2 korrekt abgefangen, ohne eigenen Tap).
  **Nächste Session:** Bed of Roses FRISCH kalibrieren — Record → bei jedem
  Abschnitts-Anfang tippen, sobald du ihn ansingst, Intro/Solo NICHT tippen →
  Stop. Dann mitsingen: steht der Abschnitt oben, laufen die Zeilen im Sing-Tempo,
  werden Intro/Solo sanft ausgesessen? Falls ein winziger konstanter Rest bleibt:
  Vorlauf (`lyricsLeadMs`, Feld+Migration v17 noch da) reaktivierbar.

### Sprint 5.47 DONE: Kalibrierung pro Zeile + Recalibration-Isolation — durch Lese-Uhr (5.48) überholt

User-Test von 5.46 (Oben-Anker, CI grün) mit drei präzisen Beobachtungen:
(1) kurze Intro → die ersten 1–2 Zeilen sind oben schon durchgelaufen, bevor der
Gesang einsetzt; (2) 2. Vers stand korrekt oben (Modell funktioniert an den
kalibrierten Punkten!), gefühlt kurzes Innehalten vor dem Weiterscrollen;
(3) die tatsächlich zu singenden Zeilen standen eher im MITTLEREN statt oberen
Bereich.

**Diagnose (kein Bug im Modell):** (2) beweist, dass das Oben-Anker-Modell an den
Kalibrierungspunkten exakt sitzt. (1) und (3) sind beide Symptome derselben
Ursache — **ein Tap pro ABSCHNITT ist zu grob.** Zwischen zwei Punkten
interpoliert die App nur linear in Pixeln; über einen langen Abschnitt mit vielen
Zeilen passt das nicht zur echten Gesangs-Zeilenfolge → die Zeilen dazwischen
driften nach unten (Mitte), bis der nächste Tap sie wieder hochsnappt. Die
Intro-Zeilen VOR dem ersten Tap sind gar nicht verankert → laufen ungebremst
durch. Für "jede gesungene Zeile steht oben, wenn ich sie singe" braucht es einen
Anker PRO ZEILE — mathematisch zwingend, nicht optional.

**Umsetzung (Code konnte beliebige Granularität schon immer, es war die Anleitung):**
- Kalibrier-Hinweistexte (Toast + Statuszeile) + KDoc auf **"bei jeder Zeile
  tippen, sobald du sie singst (auch die erste!)"** geändert. Je feiner, desto
  genauer sitzt jede Zeile oben; besonders die erste Gesangszeile mittappen löst
  das Intro-Problem (davor bleibt der Anker bei (0,0), die ersten Zeilen driften
  nur minimal statt davonzulaufen).
- **Recalibration-Isolation (echte Robustheits-Verbesserung):** Während einer
  laufenden neuen Kalibrierung (`calibrating == true`) treibt die ALTE
  gespeicherte Kalibrierung den Auto-Scroll NICHT mehr (`useBreakpoints =
  !calibrating` im Frame-Loop → keine Anker-Weiterschaltung, kein nextBreak,
  nur langsamer Drift ab dem letzten Tap). Sonst hätten altes Auto-Scrolling und
  frische Taps gegeneinander gekämpft und `handleTap()` hätte Zeilen relativ zur
  alten, falschen Scroll-Position aufgezeichnet. Beim Stop wird die neue Punkt-
  liste gespeichert → LaunchedEffect startet mit den neuen Breakpoints neu. Beim
  Nicht-Kalibrieren ist das Verhalten identisch zu vorher (risikoarm). Siehe
  Gotcha 12.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich (nur statisch
  geprüft: Klammerbalance, Scope, keine verwaisten Referenzen). **Nächste Session:**
  Bed of Roses FRISCH kalibrieren — Record → bei JEDER Zeile tippen (mit der ersten
  Gesangszeile anfangen) → Stop, dann mitsingen und prüfen, ob jetzt jede Zeile zu
  ihrem Einsatz oben steht.

### Sprint 5.46 DONE: Abschnitts-Modell — Oben-Anker statt 30%-Lesepunkt

User-Ansage nach 5.45 (Vorlauf-Regler): "das gefällt mir so nicht", stattdessen
eine eigene Vorstellung — einmal durchtippen, dann soll **jeder Abschnitt als
Ganzes sanft an den oberen Rand gleiten**, jeder Abschnitt mit eigener
Geschwindigkeit, während der Song durchläuft. Per Frage/Antwort geklärt:
durchgehend langsames Gleiten (kein Halten/Snappen), Fokus-Position ganz oben.

**Kern-Erkenntnis (endlich die Wurzel des "hinkt hinterher"):** Der feste
30%-Lesepunkt (Sprint 5.39) rückte über die gerade gesungene Zeile permanent
~15 Zeilen (0.3 × Viewport / Zeilenhöhe) BEREITS GESUNGENEN Text — genau
dorthin, wo man beim Singen instinktiv hinschaut. Das erklärt das dauerhafte
"der Text ist hinter mir"-Gefühl STRUKTURELL, unabhängig von Rate/Interpolation
(die laut Diagnose-Log nachweislich korrekt waren, 0.0128–0.0333 px/ms). ALLE
Nachhinken-Reports (5.40–5.45) liefen mit diesem 30%-Anker.

**Fix (bewusst minimal & risikoarm):** Platzierung im `Layout` von
`readingAnchorPx - scrollOffsetPx` zurück auf `-scrollOffsetPx` (Oben-Anker) —
bei `scrollOffsetPx == Pixel eines Abschnitts-Anfangs` steht dieser exakt oben,
genau was die Kalibrierung anpeilt. `scrollOffsetPx` selbst (Rate pro Segment,
Monoton-Klemmung, `handleTap()`, Positions-Hochrechnung aus 5.44) bleibt
UNVERÄNDERT — deshalb sehr risikoarm, die gefürchtete Fehlerklasse
(Rückwärtssprung/Ruckeln/Race) betrifft nur `scrollOffsetPx`. Entfernt:
`ANCHOR_FRACTION`, die 30%-Indikator-Linie, der Vorlauf-Regler samt −/+ UI
und `updateLyricsLeadMs` (DAO/VM). Behalten: DB-Feld `lyricsLeadMs` + Migration
v17 (Schema-Stabilität, Gerät bereits auf v17 — analog `lyricsStartMs`).
Kalibrierung, Diagnose-Log/Share, Struktur-Labels, 60fps-Hochrechnung: alle
unverändert. Siehe Gotcha 12.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich (Google-Maven
  für Android-Gradle-Plugin gesperrt, 403 — nur statisch geprüft: Klammerbalance,
  keine verwaisten Referenzen, Imports, Schema). **Nächste Session: live testen** —
  Bed of Roses (Kalibrierung bleibt gespeichert): fühlt sich der Text jetzt beim
  Mitsingen richtig an, kommt der aktuelle Abschnitt oben an, ist das Nachhinken
  weg? Falls ein kleiner konstanter Rest bleibt: Vorlauf (5.45) sauber wieder
  aktivierbar.

### Sprint 5.45 DONE: Einstellbarer Vorlauf — vom User abgelehnt, in 5.46 zurückgenommen (Room v16→17)

User-Report nach 5.44 (Positions-Hochrechnung, CI grün): "das Problem besteht
weiterhin" — der Text hinkt beim Mitsingen weiterhin konstant hinterher, die
Geschwindigkeit fühlt sich unverändert an. User-Anweisung: die komplette Logik
nochmal ganz genau auf Denkfehler durchgehen.

**Analyse-Ergebnis:** Der 5.44-Fix (60fps-Interpolation) hat das Nachhinken NICHT
behoben — Beweis, dass die Ursache KEIN <200ms-Glättungsproblem und KEIN
Rechenfehler in Rate/Interpolation ist (die Segment-Raten im geteilten Log
variieren nachweislich korrekt 0.0128–0.0333 px/ms über 11 Kalibrierungspunkte).
Übrig bleibt strukturell nur ein KONSTANTER Zeit-Versatz. Ursache: (1) menschliche
Reaktionszeit beim Kalibrieren — jeder Tap wird ~0,3–0,5s NACH dem echten
Abschnittswechsel gesetzt, dieser Verzug ist in jeden Kalibrierungspunkt eingebacken
und wird beim Abspielen 1:1 reproduziert; (2) ein Sänger will die Zeile SEHEN, bevor
er sie singt, nicht genau wenn.

**Fix (bewusst KEIN fünfter unsichtbarer Automatik-Versuch):** direkt vom User
verstellbarer **Vorlauf** statt weiterem Raten an der Automatik. Room v16→17:
`Song.lyricsLeadMs: Long = 0L`, `MIGRATION_16_17`, `SongDao.updateLyricsLeadMs`,
`PlayerViewModel.updateLyricsLeadMs`. Im Frame-Loop wird `leadMs` auf die geschätzte
Position addiert (`pos = (estimatedPositionMs() + leadMs).coerceIn(0, dur)`) → der
Scroll rechnet mit einer voraus liegenden Position, jede Zeile erreicht den Lesepunkt
`leadMs` früher. −/+ Buttons im Header (250ms-Schritte, −3s..+8s), live während der
Wiedergabe verstellbar, sofort pro Song persistiert. Lead wirkt NUR auf die
Playback-Position, NICHT auf `handleTap()` (Kalibrierung zeichnet weiter Roh-Positionen
auf) — reine Kompensation ON TOP, komponiert automatisch korrekt. Siehe Gotcha 12.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich. **Nächste
  Session: live testen** — Bed of Roses mitsingen, per + den Vorlauf so weit
  hochdrehen, bis der Text genau da steht, wo gesungen wird. Prüfen, ob der Wert
  pro Song erhalten bleibt (Screen schließen/neu öffnen).

### Sprint 5.44 DONE: Fix — Positions-Hochrechnung (200ms-Poll vs. 60fps-Loop), hat das Nachhinken laut User NICHT behoben (siehe 5.45)

User-Report nach 5.43 (persistenter Diagnose-Log, CI grün): Mit dem jetzt korrekt
zugeordneten, vollständigen Log für Bed of Roses (11 Kalibrierungspunkte, echte
Segment-Wechsel sichtbar) zeigte sich beim Live-Mitsingen weiterhin dasselbe
Symptom — der Text bleibt konstant hinter dem Gesang zurück. User-Anweisung: die
komplette Logik nochmal von Grund auf auf Denkfehler durchgehen, nicht nur die
Rate-Formel selbst (die laut Log nachweislich korrekt unterschiedliche Werte pro
Segment liefert, 0.0128–0.0333 px/ms).

**Root Cause gefunden (Datenquelle, nicht Rate-Formel):** `PlayerViewModel`
aktualisiert `positionMs` nur alle 200ms (Poll-Loop, `delay(200L)`), der
Scroll-Frame-Loop in `LyricsOverlay.kt` läuft aber mit 60fps (`withFrameNanos`).
Ohne Hochrechnung bleibt `pos` für ~12 von 12 Frames eingefroren und springt dann
sprunghaft nach — der Scroll bewegt sich in "Treppenstufen" statt smooth, verbringt
die meiste Zeit stillstehend. Das erklärt beide Symptome exakt: "konstant" (viel
sichtbare Stillstandszeit) UND "hinkt hinterher" (der angezeigte Text liegt im
Schnitt immer ein Stück hinter der kontinuierlich fortschreitenden Musik).

**Fix:** `estimatedPositionMs()` (neue lokale Funktion in `LyricsContent`) verankert
den letzten echten 200ms-Messwert (`lastRawPositionMs`/`lastRawPositionAtNs`) und
rechnet dazwischen per realer Systemzeit (`System.nanoTime()`) linear hoch, gekappt
auf max. 400ms Overshoot — der nächste echte 200ms-Wert korrigiert automatisch,
kein Drift. Nur aktiv während `isPlaying`; beim Pause→Play-Übergang wird der Anker
sofort neu verankert. Auf Composable-Ebene gehalten (nicht lokal im
`LaunchedEffect`), damit sowohl die Frame-Loop als auch `handleTap()`
(Kalibrierungs-Aufnahme) dieselbe Schätzung verwenden — sonst wären neu
aufgenommene Kalibrierungspunkte leicht verrauscht relativ zur Playback-
Darstellung. Siehe Gotcha 12 für Details.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich. **Nächste
  Session: gezielt live testen**, ob sich der Scroll jetzt beim Mitsingen von
  Bed of Roses spürbar smooth und ohne Nachhinken anfühlt.

### Sprint 5.43 DONE: Diagnose-Log übersteht Song-Wechsel (CUE-Arming-Bug gefunden)

User-Hinweis nach 5.42: Der geteilte Log gehörte zu einem Song, der laut User "gar
nicht abgespielt wurde". Root Cause im Code bestätigt: `PlayerViewModel.skipNext()`
→ `selectSong()` wird beim Auto-Advance (Song-Ende-Erkennung, siehe Gotcha 2) auch im
**CUE-Modus** aufgerufen (`endAction=0`: "arm, kein Play", siehe Sprint 5.22) —
dabei wird `_currentSong.value` sofort auf den NÄCHSTEN Song gesetzt und dessen Audio
per `engine.activatePreloaded()` in die Engine geladen, OBWOHL `_isPlaying` auf
`false` bleibt und der User den Song nie hört. Der Lyrics-Screen folgt `currentSong`
direkt — blieb der Screen nach Songende offen (kein automatisches Schließen bei
Songwechsel), sprang der komplette Diagnose-Zustand (inkl. `debugLog`, das bisher
`remember(song.id, openSession)`-gebunden war) lautlos auf den neu **georarmten, aber
nie gehörten** nächsten Song um — der User hatte dadurch nie eine Chance, den echten
Log des zuvor gehörten Songs zu teilen.

**Fix:** `debugLog` lebt jetzt nicht mehr in `LyricsOverlay.kt` selbst (dort per
`remember(song.id, openSession)` bei jedem Songwechsel zurückgesetzt), sondern als
einfache `MutableList<String>` in `PlayerViewModel` (`lyricsDebugLog`,
`logLyricsDebug()`/`logLyricsWarn()`) — überlebt damit JEDEN Songwechsel innerhalb
der App-Session, auch stille CUE-Arm-Übergänge. `LyricsOverlay`/`LyricsContent`
bekommen `debugLog: List<String>` sowie `onLogDebug`/`onLogWarn`-Callbacks von außen
injiziert statt sie selbst zu verwalten; `MainScreen.kt` verdrahtet sie auf
`vm.lyricsDebugLog`/`vm.logLyricsDebug()`/`vm.logLyricsWarn()`. Jede Log-Zeile enthält
weiterhin den Songtitel (`"Lyrics-Loop start: song='...'"`), dadurch bleibt auch bei
mehreren Songs im selben Log nachvollziehbar, welche Zeile zu welchem Song gehört.

- **Nicht verifiziert:** kein Gradle-Build in dieser Session möglich. **Nächste
  Session: gezielt testen**, dass der Share-Button jetzt den vollständigen Log des
  tatsächlich gehörten Songs enthält, auch wenn der Song inzwischen zu Ende ist und
  der nächste automatisch (CUE-Modus) geladen wurde.

### Sprint 5.42 DONE: Segment-Wechsel-Bug aufgeklärt — kein Code-Bug, fehlende Kalibrierung

Diagnose-Log (Sprint 5.40/5.41) hat sofort die Ursache gezeigt: `Lyrics-Loop start:
song='Can't judge a book K0' ... breakpoints=[]` — für GENAU DIESEN Song waren keine
Kalibrierungspunkte gespeichert. Per Rückfrage bestätigt: dieser Song wurde bisher nie
selbst kalibriert (der frühere "Ja, frisch komplett durchkalibriert"-Report bezog sich
auf einen anderen Song, vermutlich "Bed Of Roses"). Ohne Kalibrierungspunkte läuft die
Loop im dokumentierten Fallback-Modus (reine globale Positions-Proportion über die
ganze Songlänge, siehe Gotcha 12) — das ist das erwartete, korrekte Verhalten ohne
Kalibrierungsdaten, kein Defekt im Segment-Wechsel-Code selbst.

**Kein Code-Fix nötig.** Segment-Code (Rate-Formel, Anker-Weiterschaltung) ist korrekt,
wie schon in Sprint 5.40 vermutet — es hat nur nie Kalibrierungsdaten zum Anwenden
gehabt. Lösung für den User: für JEDEN Song, der abschnittsweise dynamisch laufen soll,
einmal individuell Record → durchtippen → Stop machen. Die Diagnose-Infrastruktur aus
5.40/5.41 (Log.d/Log.w + in-App teilbares `debugLog`) bleibt bestehen — nützlich für
zukünftige ähnliche Reports, um sofort zu sehen ob `breakpoints=[]` die Ursache ist,
bevor an der eigentlichen Logik gesucht wird.

Nebenbei aufgefallen (nicht Ursache dieses Falls, da durch `maxOf()` bereits
abgefangen): `song.duration='03:24'` (204000ms) vs. live gemeldete `durationMs=410425`
— fast exakt Faktor 2 auseinander. Für diesen Song ist die beim Import gemessene
Anzeige-Dauer offenbar falsch/halbiert. Betrifft nur die Fallback-Rate-Berechnung
potenziell in Edge-Fällen, aktuell durch `maxOf(durationMs, dbDurationMs)` unkritisch,
da die größere (richtige) Live-Dauer gewinnt — trotzdem als bekannte Dateninkonsistenz
notiert, falls „${song.duration}"-Anzeigen an anderer Stelle in der App betroffen sind.

### Sprint 5.41 DONE (Diagnose, kein Fix): Diagnose-Log ohne adb teilbar

User hat keinen PC/adb zur Verfügung, um das in Sprint 5.40 ergänzte Logging
auszulesen ("wo finde ich diese logs?"). Statt adb-Anleitung: dieselben
Meldungen (`Lyrics-Loop start …`, `Neues Segment …`, Warnungen zu
übersprungenen Kalibrierungspunkten/Guard-Blocks/großen Sprüngen) landen jetzt
zusätzlich in einer In-App-Liste (`debugLog`, gekappt bei 300 Einträgen,
mit `song.id`/`openSession` gekeyt wie der übrige Scroll-Zustand). Neuer
Share-Icon-Button im Header (zwischen Record/Stop und Schließen) öffnet
Androids Share-Sheet (`Intent.ACTION_SEND`, `text/plain`) mit dem kompletten
Log als Text — User kann direkt vom Handy aus per WhatsApp/E-Mail/Notiz an
den Chat schicken, kein Kabel/PC nötig. `Log.d`/`Log.w` bleiben zusätzlich
bestehen (adb funktioniert weiterhin, falls doch mal ein PC zur Hand ist) —
`logDebug()`/`logWarn()` sind dünne Wrapper, die beides gleichzeitig tun.
Reine Zusatz-Instrumentierung, keine Änderung an Rate-/Segment-Logik selbst.

### Sprint 5.40 DONE (Diagnose, kein Fix): Logging für Segment-Wechsel-Bug

User-Report nach Live-Test von Sprint 5.39 (fester Lesepunkt): der Text läuft "immer
ganz konstant anstatt seine Geschwindigkeit dynamisch anzupassen", manche Songteile
scrollen zu langsam. Explizit bestätigt: eine komplett frische Kalibrierung (Record →
durchtippen → Stop) wurde in diesem Build bereits gemacht — das schließt "keine/kaputte
Kalibrierungsdaten" als Ursache aus. Die Rate-Formel selbst (`Pixel-Distanz /
Zeit-Distanz` pro Segment) ist mathematisch korrekt und sollte bei unterschiedlich
langen Abschnitten automatisch unterschiedliche Geschwindigkeiten ergeben — dass es
trotzdem konstant wirkt, deutet auf einen Bug im Segment-Wechsel selbst hin, nicht auf
fehlende Daten.

**Bewusst KEIN Blind-Fix (Lehre aus 5.33–5.35):** Statt eine fünfte Theorie zu raten,
wurde nur zusätzliches Logging ergänzt (rein additiv, keine Verhaltensänderung):
- `Log.w`, falls ein Kalibrierungspunkt beim Anker-Weiterschalten KEINE gemessene
  Zeilen-Position findet (`linePositions[lineIdx] == null`) — dieser Fall wurde bisher
  still übersprungen (Punkt zählt als "erledigt", Anker bleibt aber unverändert), was
  mehrere echte Segmente unsichtbar zu einem einzigen verschmelzen lassen könnte
  (Hauptverdacht für "wirkt konstant").
- `Log.d` bei jedem tatsächlichen Segment-Wechsel mit Anker, Segment-Ende und
  berechneter Rate — macht sichtbar, ob und wie stark sich die Rate zwischen
  Abschnitten wirklich unterscheidet.
- Die bereits vorhandene Logzeile beim Öffnen (`Lyrics-Loop start: ... breakpoints=…`,
  Sprint 5.35) zeigt zusätzlich die komplette geparste Kalibrierungsliste.

**Nächste Session:** `adb logcat -s LyricsOverlay` während eines kompletten Songdurchlaufs
mitschneiden und auswerten — zeigt, ob (a) Kalibrierungspunkte beim Abspielen
übersprungen werden (Log-Warnung) oder (b) die Segmente korrekt wechseln, aber mit
falscher/zu ähnlicher Rate (Segment-Wechsel-Logzeilen vergleichen). Erst danach den
eigentlichen Fix gezielt umsetzen.

### Sprint 5.39 DONE (ungetestet): Fester Lesepunkt im Teleprompter

User-Wunsch nach Sprint 5.38 (Scroll-Stillstand-Fix, damals noch ungetestet): der
jeweils aktuelle Abschnitt/die aktuelle Zeile soll optisch besser im Fokus stehen —
konkret nachgefragt am Beispiel Vers→Chorus-Übergang. Vor der Umsetzung explizit per
Rückfrage (User-Anweisung: "sag mir bitte, ob Du alles korrekt verstanden hast und
lass uns die Sachen in eine Frage Antwort Session klären") drei Optionen zur Wahl
gestellt; User hat sich für **"Fester Lesepunkt"** entschieden: Scroll bleibt exakt
so smooth/monoton wie bisher, aber der Text wird nicht mehr ab dem Viewport-Oberrand
platziert, sondern ab einer festen Position bei ~30% Bildschirmhöhe — jede Zeile läuft
beim Durchscrollen sichtbar durch diese Position, mit einer dünnen Linie als visueller
Anker markiert.

- **Umsetzung:** siehe Gotcha 12, Abschnitt "Fester Lesepunkt". Nur eine Verschiebung
  des Platzierungs-Nullpunkts im bestehenden `Layout` plus eine rein dekorative,
  nicht-scrollende Indikator-Linie — keinerlei Änderung an der Rate-/Segment-Berechnung,
  der Monoton-Klemmung oder `handleTap()`. Dadurch strukturell risikoarm: die am
  meisten gefürchtete Fehlerklasse dieses Features (Rückwärtssprung, Ruckeln,
  Race-Bugs) betraf ausschließlich `scrollOffsetPx`, das hier unangetastet bleibt.
- Zusätzlich beantwortet (keine Code-Änderung nötig, nur Erklärung an den User): wie
  die App weiß, welche Zeile "im Fokus" sein muss (aus den beim Kalibrieren gemessenen
  `(Zeilen-Index, Position)`-Paaren, siehe Kalibrierung oben) und wie textarme
  Abschnitte wie "[Solo]" korrekt langsamer/schneller laufen (ergibt sich automatisch
  aus der Segment-Rate-Formel `Pixel-Distanz / Zeit-Distanz` zwischen zwei
  Kalibrierungspunkten — kein Sonderfall nötig).
- **Nicht verifiziert:** Wie 5.30–5.38 kein Gradle-Build in dieser Session möglich.
  **Nächste Session: live testen**, ob der Lesepunkt bei ~30% sitzt, der Text sauber
  hindurchläuft, und ob Sprint 5.38 (Scroll überhaupt bewegt sich) zusammen mit diesem
  Fix funktioniert — das war die letzte ungetestete Baustelle vor diesem Feature.

### Sprint 5.38 DONE (ungetestet): Fix — Text scrollte überhaupt nicht (struktureller Box-Constraint-Bug)

User-Report nach Live-Test von Sprint 5.37 (Commit c4bf09c, CI grün): Kalibrierungspunkte
werden weiterhin korrekt gespeichert, ABER der Text bewegt sich überhaupt nicht mehr —
weder während der Kalibrierung noch danach beim normalen Abspielen. User stellt klar,
wofür die Funktion eigentlich da ist: nach abgeschlossener Kalibrierung soll der Text
beim Songabspielen butterweich und in korrekter Geschwindigkeit mitlaufen, damit man
als Musiker live immer an der richtigen Stelle im Text ist.

**Root Cause — struktureller Bug, keine Race Condition:** `LyricsOverlay.kt` verschachtelte
Viewport und Content in einer normalen `Box(fillMaxSize) { Column(fillMaxWidth) }`. Eine
`Box` reicht ihre eigene (durch den Viewport begrenzte) `maxHeight`-Constraint automatisch
an ihre Kinder weiter — die Content-Column konnte dadurch NIE höher gemessen werden als
der sichtbare Ausschnitt selbst. `contentHeightPx` blieb praktisch immer identisch zu
`viewportHeightPx`, `maxScrollPx` (= Differenz) damit strukturell ~0 — die Frame-Loop
wartete (per `allLinesMeasured`/`maxScrollPx > 0`-Gate aus Sprint 5.36) für immer auf
sinnvollen Scroll-Bedarf, der nie kam. Kompletter Stillstand, unabhängig von Taps oder
Kalibrierung — ein deterministischer Layout-Fehler, kein Timing-/Race-Problem wie in
5.33–5.35 vermutet.

**Fix:** Eigenes `Layout`-Composable ersetzt `Box { Column { ... } }`. Die Content-Column
wird jetzt EXPLIZIT mit `constraints.copy(maxHeight = Constraints.Infinity)` gemessen —
kein Verlass mehr auf automatische Constraint-Weitergabe. Viewport-Höhe (`constraints.
maxHeight`) und Content-Höhe (`placeable.height`) werden direkt in der Messphase erfasst,
Platzierung inkl. Scroll-Offset direkt selbst über `placeable.placeRelative(0,
-scrollOffsetPx…)` im `layout{}`-Block — derselbe "nur Neuplatzierung, keine
Neuvermessung"-Mechanismus wie beim vorherigen `Modifier.offset{}`, also weiterhin
performant.

- **Nicht verifiziert:** Wie 5.30–5.37 kein Gradle-Build in dieser Session möglich.
  Diesmal aber eine konkrete, nachvollziehbare strukturelle Ursache (bekanntes
  Compose-Verhalten: Box reicht Constraints an Kinder weiter) statt einer Race-
  Condition-Vermutung — entsprechend hohe Zuversicht. **Nächste Session: gezielt
  prüfen**, ob der Text jetzt sowohl während der Kalibrierung (sofort bei jedem Tap)
  als auch danach beim normalen Wiedergeben (kontinuierlich, smooth, ohne Ruckler)
  scrollt.

### Sprint 5.37 DONE: Fix — Tap-Sofortsprung fehlte + Touch fiel zur TopBar durch (Commit c4bf09c, CI grün — Kalibrierung/Speichern bestätigt funktionsfähig, Scroll-Stillstand-Bug erst in 5.38 gefunden)

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
- ✅ **Tap-Feedback + Touch-Durchfall (ERLEDIGT):** Sprint 5.37 — Tap während
  Kalibrierung springt sofort sichtbar zur getippten Zeile, Touch fällt nicht
  mehr zur MainScreen-TopBar durch. Beides laut User-Report weiterhin korrekt
  (das eigentliche Problem beim erneuten Test war der Scroll-Stillstand aus
  5.38, keine Regression bei diesen beiden Fixes). Nicht mehr offen.
- ✅ **Sprint 5.38 (Scroll-Stillstand) + Sprint 5.39 (fester Lesepunkt)
  (ERLEDIGT):** Vom User bestätigt funktionsfähig — Text bewegt sich, Lesepunkt
  sitzt sichtbar bei ~30%. Nicht mehr offen.
- ✅ **Segment-Wechsel-"Bug" (AUFGEKLÄRT, Sprint 5.42, KEIN Code-Bug):** Diagnose-
  Log zeigte `breakpoints=[]` für den getesteten Song — der Song war schlicht nie
  individuell kalibriert worden (früherer "frisch kalibriert"-Report bezog sich
  auf einen anderen Song). Segment-Rate-Formel ist korrekt. Kein Fix nötig — User
  muss jeden Song, der abschnittsweise dynamisch laufen soll, einmal einzeln über
  Record → durchtippen → Stop kalibrieren. Diagnose-Infrastruktur (`debugLog` +
  Share-Button, Sprint 5.40/5.41) bleibt für künftige Reports nützlich. Nicht mehr
  offen. Separat notiert (kein aktueller Bug, nur beobachtet): `song.duration` für
  diesen Song war ~Faktor 2 kleiner als die live gemeldete `durationMs` — durch
  `maxOf()` bereits unkritisch abgefangen, aber als bekannte Dateninkonsistenz für
  diesen einen Song vermerkt.
- ✅ **Datenverlust-Bug: Kalibrierung ging beim Songende verloren (ERLEDIGT,
  Sprint 5.49):** User hatte korrekt neu kalibriert, aber das Log zeigte trotzdem
  uralte Punkte — der Song lief beim Kalibrieren bis zum Ende, die App armte
  lautlos den nächsten Song (CUE-Modus), der komplette Kalibrierungs-Zustand
  wurde dabei verworfen, bevor Stop etwas speichern konnte. Fix:
  `DisposableEffect(song.id) { onDispose { ... } }` speichert beim Songwechsel
  automatisch, falls noch kalibriert wurde (siehe Gotcha 12). Nicht mehr offen.
- ✅ **Logging-Lücke: "Segment:"-Zeile feuerte nur zufällig (ERLEDIGT, Sprint
  5.50):** Nach erfolgreicher Neu-Kalibrierung (5.49-Fix bestätigt, frische 7
  Punkte im Log sichtbar) enthielt der Log trotzdem keine "Segment: ..."-Zeilen.
  Root Cause: die Log-Zeile stand nur im Phase-1-Zweig, feuerte also nur, wenn
  der Wechsel-Frame zufällig noch in Phase 1 lag. Fix: Log steht jetzt vor der
  Phase-Verzweigung, feuert bei jedem echten Abschnittswechsel. Nicht mehr offen.
- ✅ **Erste vollständige Log-Auswertung + Root Cause "falsche Wartezeiten"
  (ERLEDIGT, Sprint 5.51):** Mit dem 5.50-Fix kam der erste komplette Log
  (alle 6 Segmente). Ergebnis: Mathematik korrekt, aber 3 von 6 Segmenten
  hatten 12–19s künstliche Wartezeit ohne echtes Instrumental (globales
  `naturalPace` zu schnell für diese Segmente). Fix: strukturelle
  Instrumental-Erkennung (`isInstrumentalLabel`/`segmentHasInstrumental`,
  siehe Gotcha 12) — nur Segmente MIT echtem Instrumental-Label behalten das
  Warte-Modell, alle anderen nutzen jetzt lokales statt globales Tempo. Nicht
  mehr offen (Fix gemacht, aber ungetestet — siehe nächster Punkt).
- 🔴 **Teleprompter: Sprint-5.51-Fix (lokales Tempo) live testen (PRIO 1):**
  Bestehende Kalibrierung von Bed of Roses reicht, KEIN Neu-Tippen nötig. Song
  einmal komplett durchlaufen lassen. **Erwartung laut Handrechnung:** die 3
  Segmente, die vorher rechnerisch "hängenblieben" (19→29, 29→45, 45→58 in der
  letzten Kalibrierung), sollten jetzt gleichmäßig ohne Pause laufen; das
  Bridge→Solo-Segment sollte weiterhin sichtbar warten. Diagnose-Log senden und
  prüfen: taucht `instrumental=true` nur beim Solo-Segment auf, `false` bei den
  anderen? Fühlt sich der Scroll jetzt insgesamt smooth/nicht-willkürlich an?
  Falls
  v17 da) reaktivierbar.
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
