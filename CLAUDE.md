# Live-Gig-Player Pro — Projektkontext für Claude

## SESSION-START — ZUERST LESEN (PFLICHT)

**Diese CLAUDE.md ist die einzige Quelle der Wahrheit.** Kein GitHub-Search, keine Issue-Suche, kein Raten. Alle TODOs, der aktuelle Stand und die Branch-Regel stehen hier unten.

0. Falls `.status.md` existiert: still lesen (3 Zeilen Kurz-Kontext), dann 1-Satz-Status + Rückfrage vor dem nächsten Schritt (siehe "Arbeitsweise" unten).
0b. **GrillMe-Skill ist immer verfügbar** — liegt fest in `.claude/skills/grillme.md` (auf `main`) und wird beim Session-Start automatisch als `/grillme` geladen. Bei Planungs-/Konzept-Themen aktiv nutzen (sokratisches Interview, siehe "Arbeitsweise").
1. Branch prüfen: `git branch` → muss `* main` zeigen. Falls nicht: `git checkout main`
2. Letzten Stand lesen: Abschnitt "Letzter Stand" weiter unten.
3. Offene TODOs lesen: Abschnitt "Offene TODOs (nächste Session)" weiter unten.
4. CI-Status prüfen (GitHub Actions, letzter Build auf `main`).

## Arbeitsweise (Kommunikation & Workflow — PFLICHT)

- **Sprache/Ton:** Antworten auf Deutsch, keine Floskeln/Begrüßungen. Erklärungen
  als kurze Bullet-Points (max. 3).
- **Plan-First:** Bei Logik/Feature-Änderungen NICHT direkt Code schreiben —
  erst Lösung in 2-3 einfachen Sätzen erklären, EINE konkrete Frage stellen,
  Antwort abwarten. Triviale Ein-Zeiler-Fixes (Tippfehler, offensichtliche Bugs)
  dürfen direkt gemacht werden.
- **GrillMe-Modus:** Bei Befehl "GrillMe <Thema>" → Sokratischer Modus, NIE die
  Lösung direkt geben. Genau EINE Leitfrage stellen, auf Antwort warten, bei
  oberflächlicher Antwort mit EINER Unterfrage nachbohren (Rekursion bis
  wasserdicht), dann kurz validieren ("Korrekt.") und nächste Frage. Endet nur
  auf expliziten Exit-Befehl.
- **Code-Output:** Keine kompletten Dateien ausgeben (außer Neuanlage) — nur
  Diffs/geänderte Zeilen + 2-3 Zeilen Kontext. Keine geratenen UI/Touch-Gesten.
- **Fehler-Handling:** Bei eingefügter Fehlermeldung: kein Entschuldigen, EIN
  Satz Ursache + minimaler Fix-Diff.
- **Context-Sparsamkeit:** Keine blinden Volltext-Scans großer/unbekannter
  Dateien — vorher `wc -l`, dann gezielt `grep`/Zeilenbereiche lesen statt
  ganze Datei laden.
- **Handover (`.status.md`):** Bei Befehl "Übergabe"/"Speichern" wird
  `.status.md` neu geschrieben (genau 3 Punkte: Hauptziel, betroffene
  Dateipfade, nächster Schritt) — das ist der SCHNELLE Einstieg für die nächste
  Session, ERSETZT NICHT die CLAUDE.md. Die ausführlichen Sprint-Logs/Gotchas
  hier bleiben das Langzeit-Gedächtnis (Grund: mehrere Bugs — z.B. der
  Lyrics-Scroll-Bug in Gotcha 12 — brauchten 5+ Anläufe; ohne die volle
  Historie würden alte, bereits widerlegte Theorien wiederholt). Neue
  Sprint-Einträge ab jetzt kurz halten (siehe oben), bestehende NICHT kürzen.
  CLAUDE.md wird nur bei echtem Feature-/Fix-Abschluss ergänzt, nicht bei
  jedem Session-Ende — dafür reicht `.status.md`.

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
│   ├── PdfLyricsImporter.kt  — PDF-Textlayer → Akkord-Filter → Lyrics (PdfBox-Android),
│   │                            merkt zuletzt genutzten PDF-Ordner (SharedPreferences)
│   └── SongScanner.kt        — erkennt TrackMode aus DocumentFile-Struktur
├── data/
│   ├── Song.kt               — Room-Entity (id, title, artist, bpm, bpmExact, keySignature,
│   │                            genre, capoPosition, volDrums/Bass/Keys/Vocals/Click/Cue,
│   │                            autoStop, playlistId, audioFilePath, duration)
│   ├── SongDao.kt            — CRUD + resetAllMixerSettings
│   ├── Playlist.kt           — Room-Entity (id, name, isLiveLocked)
│   ├── PlaylistDao.kt        — getAllPlaylists
│   ├── GigEntity.kt          — Room-Entity (gigId, name, lastActiveSetId, autoAdvanceSets)
│   ├── GigDao.kt             — getAllGigs, insert, delete, setLastActiveSetId, setAutoAdvanceSets
│   ├── SetEntity.kt          — Room-Entity (setId, gigOwnerId, name, position)
│   ├── SetSongCrossRef.kt    — (setId, songId, positionInSet, isCompleted, isSpontaneous, endAction)
│   ├── SetDao.kt             — abstract class: CRUD + moveSpontaneousNext/Later + reorderSongs
│   │                            (@Transaction, Cut&Paste / Batch-Write-Pattern) + getSetProgress
│   ├── SetProgress.kt        — Datenklasse (setId, total, completed) für "gespielt X/Y"
│   ├── SongInSet.kt          — @Embedded Song + positionInSet + completedInSet + spontaneousInSet + endAction
│   ├── AppDatabase.kt        — RoomDatabase v18, Migrationen bis v18
│   └── TrackMode.kt          — sealed class: Legacy(filePath) | Multitrack(drums,bass,keys,vocals,click,cue)
├── ui/
│   ├── MainScreen.kt         — Compose-UI: zwei Tabs (Archiv / Gig-Sets), Mini-Player, Mixer,
│   │                            SongEditorSheet mit Lyrics-Textfeld, Lyrics-Button in GlobalPlayer
│   ├── PlayerViewModel.kt    — AndroidViewModel: StateFlow, Queue, Loop, AutoStop, isGigSetMode,
│   │                            loopHint (einmaliger Toast-Hinweis fürs LOOP-Verhalten),
│   │                            showLyrics/openLyrics/closeLyrics (Teleprompter-Trigger)
│   ├── GigViewModel.kt       — Gig/Set/Song CRUD, armSetIfIdle, loadSetAsQueue, switchToSet
│   │                            (Set-Umschalten ohne Unterbrechung, siehe Sprint "Set-Umschalten"),
│   │                            installSetCompletedCallback (gemeinsamer Song-Ende-Callback +
│   │                            Auto-Übergang), insertSpontaneousNext/Later (Mutex-serialisiert),
│   │                            reorderSongsInSet/reorderSets, renameSet, setAutoAdvance, setProgress
│   ├── GigManagementScreen.kt — GigListView/GigRow, GigDetailView (Griff-Button zeigt aktives
│   │                             Set + Fortschritt, öffnet SetSwitcherSheet)/SetCard/SetSongRow
│   │                             (Swipe-Handler), SetSwitcherSheet/SetSwitcherRow (Set-Übersicht:
│   │                             Umschalten/Umbenennen/Löschen/Sortieren/Auto-Übergang-Schalter,
│   │                             siehe Sprint "Set-Umschalten"), SetSongRowSortable + SetRowSortable
│   │                             (Drag-Handle-Sortiermodi), isLocked bis SetSongRow
│   │                             durchgereicht. KEIN Gig-weiter Edit-Sammel-Modus mehr
│   │                             (siehe Sprint 5.29) — Umbenennen/Löschen eines Sets laufen
│   │                             seit "Set-Umschalten" nur noch über SetSwitcherRow im
│   │                             SetSwitcherSheet (nicht mehr in SetCard). SetCards eigenes
│   │                             "⋮"-DropdownMenu enthält nur noch song-bezogene Aktionen
│   │                             ("Songs hinzufügen" → AddSongsToSetDialog, "Completed
│   │                             zurücksetzen"); der per-Set "Bearbeiten"-Toggle steuert
│   │                             weiterhin nur die Song-Zeilen-Controls (End-Aktion/Entfernen).
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

## Datenmodell Song (Room v17)

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
| lyrics | String | Songtext ohne Akkorde, mit Struktur-Labels wie `[Chorus]` (v14) |
| lyricsStartMs | Long | Superseded durch lyricsSyncPoints (v16) — nur Schema-Kompatibilität, unbenutzt |
| lyricsSyncPoints | String | Teleprompter-Kalibrierungspunkte "lineIdx:ms,…", ein Tap pro Abschnitt (v16) |
| lyricsLeadMs | Long | (v17) Reserviert/unbenutzt — war Vorlauf-Regler, abgelöst durch Abschnitts-Modell (5.46) |

## Build-Setup

```bash
./build_apk.sh          # Debug-APK bauen
# APK liegt dann unter: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

CI baut automatisch bei jedem Push auf `main` einen **Release-APK** (mit Debug-Key
signiert, `isMinifyEnabled=false`, `debuggable=false` → ART optimiert vorab, kein
JIT-Warmup-Ruckeln wie im Debug-Build) und legt ihn auf `apk-dist`:
```bash
git fetch origin apk-dist
git show origin/apk-dist:LiveGigPlayer-release.apk > /tmp/LiveGigPlayer.apk
```

## Wichtige Gotchas

1. **Room v18** — nächste Migration wäre 18→19. Migrationen NIE doppelt anlegen.
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
12. **Lyrics-Teleprompter — TabSync, deklarativ (aktueller Stand seit
    2026-07-21, Commits `4b18842`–`57a0e62`)** — `LyricsOverlay.kt` berechnet
    den Scroll-Offset rein deklarativ aus `positionMs` + Kalibrierungspunkten
    (`computeTargetOffsetPx()`), OHNE Frame-Loop. `animateFloatAsState(tween(200))`
    glättet die 200ms-Position-Ticks zu sichtbar smoothem Scroll.
    - **Kalibrierung:** ein Tap PRO ZEILE (nicht mehr pro Abschnitt) — Record-Button,
      speichert `(Zeilen-Index, Position)` in `song.lyricsSyncPoints`
      (`"lineIdx:ms,…"`, siehe `serializeSyncPoints`/`parseSyncPoints`).
    - **Instrumental-Erkennung:** textbasiert (`segmentIsInstrumental()`) — ein
      Segment zwischen zwei Kalibrierpunkten gilt als Instrumental, wenn dort
      keine einzige echte Songzeile liegt. Instrumental → Scroll steht still bis
      exakt zum nächsten Tap-Punkt. Vocal → linearer Scroll zwischen den Punkten.
    - **Top-Anchor:** die aktuell zu singende Zeile landet am literalen oberen
      Bildschirmrand (`readpointY = 0f`).
    - **Nach wie vor gültig (aus der alten Architektur übernommen, NICHT ändern):**
      - KEIN `ScrollState`/`verticalScroll` — eigenes `Layout`, das die
        Content-Column explizit mit `constraints.copy(maxHeight =
        Constraints.Infinity)` misst (Grund: eine normale `Box` reicht ihre
        eigene Höhen-Begrenzung an Kinder weiter, Content kann dann nie höher
        gemessen werden als der Viewport → Scroll bleibt bei 0 stecken).
      - `rememberUpdatedState` für Tap-Handler-Closures (`pointerInput(Unit)`
        startet nie neu — sonst Stale-Capture wie Gotcha 6).
      - `DisposableEffect(song.id) { onDispose { ... } }` rettet eine laufende
        Kalibrierung, falls der Song mitten in der Aufnahme wechselt (CUE-Modus
        armt lautlos den nächsten Song, siehe Gotcha 2/Sprint 5.22).
      - Leerer `detectTapGestures {}` auf der äußersten Box gegen Touch-Durchfall
        zur MainScreen-TopBar.
    - **Archiviert (nicht mehr im Code, nur Historie):** Der Abschnitt "Sprint
      5.30–5.52" weiter unten beschreibt eine frühere Frame-Loop-Architektur
      (`withFrameNanos`, `naturalPace`/"Lese-Uhr"-Zwei-Phasen-Modell, Anker-
      Monoton-Klemmung, 200ms→60fps-Positions-Hochrechnung) — komplett durch
      die obige deklarative Lösung ersetzt. Bleibt als Lessons-Learned stehen
      (u.a. WARUM `ScrollState`/`Box{Column{}}` nicht funktionieren — das gilt
      weiterhin), beschreibt aber keinen existierenden Code mehr. Bei Bugs im
      aktuellen Teleprompter NICHT als IST-Zustand nehmen.

## Letzter Stand

**Datum:** 2026-07-26  
**Status:** ✅ Scroll-Performance Archiv + Set-Songliste-Scroll-Bug + CI auf Release umgestellt — vom User live bestätigt ("es läuft fantastisch!"). Details siehe Sprint-Eintrag unten.  
**Branch:** `main`  
**Letzter Code-Commit:** `8cdcb57` — "CI: Release-APK statt Debug bauen (echte Scroll-Performance, kein JIT-Warmup)"  
**CI Build:** Grün, verifiziert (Commit `8cdcb57`, Build #330, `LiveGigPlayer-release.apk` auf `apk-dist`)  
**⚠️ Wichtig für nächste Session:** CI baut jetzt **Release** (nicht mehr Debug). Fetch-Pfad ist `origin/apk-dist:LiveGigPlayer-release.apk` (siehe Build-Setup oben). Release ist mit Debug-Key signiert → installierbar, Update ohne Deinstallieren.

### Scroll-Performance Archiv + Set-Scroll-Fix + Release-Build DONE (2026-07-26, live bestätigt: "es läuft fantastisch!")

Vom User gemeldet: Archiv-Scroll "nicht snappy, klebt nicht am Finger, ruckelt, Fling
falsch" (Gig-Sets besser, aber auch nicht weltklasse). Drei Ursachen nacheinander
chirurgisch gefunden & behoben, jeweils per APK live gegengetestet:

1. **LazyColumn ohne Keys** (`MainScreen.kt`, `ArchivSongRow`-Liste, Commit `db3914b`):
   `itemsIndexed(songs)` hatte kein `key`/`contentType` → keine Slot-Wiederverwendung,
   jede einströmende Zeile wurde beim Scrollen komplett neu komponiert (Frame-Budget).
   Fix: `key = { _, song -> song.id }` + `contentType = { _, _ -> "song" }`. Bindet
   nebenbei `dragX`-State an Song-Identität → robustere Swipes (wie SetCard, Gotcha 7).
   Zusätzlich `graphicsLayer` zunächst nur noch bei gedimmter Zeile.
2. **Gedimmte Zeilen = Compositing-Layer** (Commit `167ce5f`): User-A/B-Test zeigte —
   OHNE Gig (keine gedimmten Zeilen) scrollt es perfekt, MIT Gig nur leicht besser.
   Einzige Rendering-Differenz waren die gedimmten Zeilen (in Sets/Gig verplant oder
   completed → `rowAlpha < 1f`), die pro Zeile einen `graphicsLayer` (ModulateAlpha)
   anlegten. Fix: `graphicsLayer` komplett raus, Alpha direkt in jede Farbe multipliziert
   (`.copy(alpha = ... * rowAlpha)`: Hintergrund, Nummer, Titel, Untertitel, Chevrons,
   Edit/Delete-Icons). Gedimmte Zeile rendert jetzt exakt so günstig wie eine normale;
   bei `rowAlpha == 1f` alles unverändert. `graphicsLayer`/`CompositingStrategy`-Imports
   entfernt.
3. **"Wird besser, je öfter man scrollt" = Debug-Build-Warmup** (Commit `8cdcb57`): Das
   ist der Fingerabdruck von ART-JIT-Warmup + Erst-Komposition, im Debug-Build
   (`debuggable=true`, kein AOT) dramatisch überzeichnet. Umstellung der CI auf einen
   **Release-Build** (`assembleRelease`, `isMinifyEnabled=false`, mit Debug-Key signiert)
   → `debuggable=false` → ART optimiert vorab. User: "es läuft fantastisch!". Falls je
   noch Erst-Scroll-Kälte auffällt: **Baseline-Profile** wäre der saubere nächste Schritt
   (kompiliert Hot-Paths schon bei Installation vor) — bewusst noch NICHT gebaut.

**Nebenbei behoben — Set-Songliste nicht scrollbar** (`GigManagementScreen.kt`, Commit
`cb2c362`): Regression aus dem Set-Umschalten-Umbau. `GigDetailView` rendert die
`SetCard` direkt in einem nicht-scrollbaren `Column(fillMaxSize)`; die `SetCard` listet
Songs per `forEach` ohne Scroll-Container → bei 8 Songs waren die unteren unerreichbar.
Fix: `SetCard` bekommt vom Eltern-Column `Modifier.weight(1f)` (begrenzte Höhe), die
Song-Liste (sortMode-Box bzw. `forEach`) wandert in eine innere
`Column().weight(1f).verticalScroll(...)`. Header + Status-Zeile bleiben fix. **Offen als
kleiner Edge-Case:** falls ein Set IM Sortier-Modus länger als der Bildschirm wird,
können Drag-Handle und verticalScroll theoretisch konkurrieren (bei ~8 Songs unkritisch,
passt fast ganz aufs Display) — bei Bedarf Gesten sauber trennen.

**Damit ist das langjährige "SetCard nicht lazy"-TODO teilweise erledigt:** die Songs
scrollen jetzt sauber; ein echter `LazyColumn`-Umbau (statt `forEach`) ist weiterhin
offen, aber nach dem Release-Build-Wechsel deutlich weniger dringend (Performance war im
Release-Build "fantastisch").

### Set-Umschalten DONE (2026-07-26, live bestätigt: "funktioniert fantastisch")

Umsetzung von `PLAN-set-umschalten.md` (Schritte 1–4). Ein erster Anlauf mit einem
kleineren Modell (Haiku) blieb auf halbem Weg stehen — DB-Migration + Datenklassen
waren da, aber `switchToSet`/Auto-Übergang/Übersicht fehlten komplett und die UI
zeigte nur noch ein einzelnes Set ohne jede Wechsel-Möglichkeit. Mit Sonnet
vollständig nachgezogen, CI grün, vom User live bestätigt.

- **DB (v17→18):** `GigEntity.lastActiveSetId`/`autoAdvanceSets`, `SetDao.getSetProgress`
  (Fortschritt pro Set), `SetDao.getSetsForGigOnce`.
- **`GigViewModel.switchToSet`:** setzt `_activeSetId` + persistiert `lastActiveSetId`;
  läuft gerade ein Song (`playerVm.isPlaying`), wird NICHT unterbrochen — nur die Queue
  wird auf das neue Set umgebogen (`reloadQueueFromSet`); sonst wird sofort gearmt.
- **`installSetCompletedCallback`:** gemeinsamer Song-Ende-Callback für Auto-Arm/
  Set-Wiedergabe/Umschalten (vorher dreifach dupliziert); prüft nach jedem
  abgeschlossenen Song, ob das Set fertig ist und `autoAdvanceSets` an ist →
  springt automatisch ins nächste Set.
- **UI:** Griff-Button (Rahmen + "Wechseln"-Beschriftung, nach User-Feedback
  auffälliger gestaltet) unter dem Gig-Header öffnet `SetSwitcherSheet`
  (`ModalBottomSheet`) — Liste aller Sets mit Fortschritt, Tap zum Umschalten,
  Umbenennen/Löschen/Sortieren (Drag) pro Set, Auto-Übergang-Schalter, "+ Neues
  Set". `SetCard` zeigt nur noch das aktive Set; Set-Name/Nummer/Songzahl wurden
  aus dem `SetCard`-Header entfernt (stehen bereits im Griff, User-Feedback:
  Doppelanzeige) — die Zeile behält nur noch die song-bezogenen Icons
  (Sortieren/Bearbeiten/⋮ mit "Songs hinzufügen"/"Completed zurücksetzen").
- **Live getestet und bestätigt** (nicht nur CI-grün wie sonst üblich bei
  Sandbox-Builds) — kompletter Funktionsumfang inkl. Umschalten während laufender
  Song lief bereits vom User selbst am Gerät geprüft.

### PDF-zu-Lyrics-Import Phase 1 DONE (2026-07-26, live bestätigt)

Umsetzung des geplanten Features (Weg A / Phase 1). Alle 4 Commits CI grün, vom User
live getestet ("PDF Import geht super", "funktioniert!").

- **Dependency:** `com.tom-roush:pdfbox-android:2.0.27.0` (String-Notation, nicht im
  Versionskatalog). `PDFBoxResourceLoader.init(applicationContext)` in
  `LiveGigPlayerApp.onCreate()` (sonst Font-Crash bei manchen PDFs).
- **`audio/PdfLyricsImporter.kt` (neu):** `importLyricsFromPdf` (Textlayer via
  `PDFTextStripper`) + `filterChordLines`. Akkordzeile = ≥80 % der Tokens matchen
  das Akkord-Regex (aus CLAUDE.md) oder sind Takt-Tokens; `[Section]`-Labels, Leer-
  und Textzeilen bleiben, **Umbrüche 1:1** (keine eigene Umbruch-Regel — Phase 1).
  Zusätzlich `loadLastFolder`/`rememberFolderOf` (zuletzt genutzten PDF-Ordner in
  SharedPreferences merken, Parent-Ordner via `DocumentsContract` ableiten).
- **`ui/MainScreen.kt` → `SongEditorSheet`:** Import-Button (SAF-Picker
  `OpenDocument`, MIME `application/pdf`), Ergebnis ins Lyrics-Feld; belegtes Feld →
  Dialog (Ersetzen/Anhängen/Abbrechen), **kein Auto-Save**. Picker-Contract setzt
  `EXTRA_INITIAL_URI` → startet im zuletzt genutzten Ordner (best effort, geräteabh.).
- **UX-Fixes am Editor (3 Commits danach):** (1) `SongEditorSheet` war nicht
  scrollbar → Speichern bei langem Text unerreichbar; (2) `navigationBarsPadding` →
  Button nicht mehr von der Android-Steuerungsleiste verdeckt; (3) **Kopfzeile
  fixiert** (Navigation + Titel + **✓-Speichern-Häkchen**), nur der Felder-Bereich
  scrollt — Speichern immer sichtbar, alte untere Button-Leiste entfernt, Abbrechen
  via Wegwischen. Layout: äußere Column `heightIn(max = screenHeight*0.92)` +
  innerer Scrollbereich `weight(1f, fill=false).verticalScroll(...)`.

**Commits:** `3a071f6` (Import) → `4f9e5e6` (scrollbar) → `d2e9c25` (Nav-Padding +
Ordner-Gedächtnis) → `71de3a3` (fixiertes Speichern-Häkchen).

**Offen / Phase 2 (bewusst zurückgestellt):** Ob die 1:1-Umbrüche am echten Song
gut aussehen, ist noch nicht final beurteilt — falls nicht, eigene Umbruch-Regel
(Weg B) erst aus einem realen Input→Wunsch-Beispiel ableiten, nicht raten. OCR für
Bild-/Scan-PDFs ist NICHT Teil von Phase 1 (nur PDFs mit Textlayer, z.B. Ultimate
Guitar). `PLAN-pdf-import.md` im Repo-Root beschreibt die Phase-1-Umsetzung im Detail.

### Scroll-Performance Archiv & Gig-Verwaltung (2026-07-25)

User-Report: Scroll-Verhalten war in der App "noch nie sehr gut", explizit als eigenständiges
(nicht durch die Ausgrau-Feature-Session verursachtes) Problem gemeldet. Chirurgische Diagnose
vor jeder Änderung durchgeführt und dem User als Befund-Liste präsentiert, erst nach explizitem
"go" umgesetzt.

**Gefundene & behobene Probleme (in dieser Reihenfolge aufgetreten):**

1. **SetSongRow: `.alpha()` statt `graphicsLayer`+`ModulateAlpha`** (GigManagementScreen.kt) —
   erzeugte pro gedimmter Zeile (completed songs) einen Offscreen-Buffer. Fix: gleiches Pattern
   wie ArchivSongRow (siehe unten) — `graphicsLayer { alpha = ...; compositingStrategy =
   CompositingStrategy.ModulateAlpha }`. **Ist geblieben, funktioniert.**

2. **SetCard: `songs.forEach{}` statt `LazyColumn`** — komponierte ALLE Songs in ALLEN Sets
   gleichzeitig, nicht nur sichtbare. Erster Fix-Versuch: Umbau auf `LazyColumn(items(songs,
   key={it.song.id}))`. **CRASHTE beim Öffnen eines Sets** (verschachtelte LazyColumn in Column
   ohne Höhenbegrenzung — klassischer Compose-Infinite-Height-Konflikt). Sofort per
   `git reset`/manuellem Revert zurück auf `forEach{}` — **bewusst NICHT erneut versucht in
   dieser Session**, da Risiko/Nutzen-Verhältnis nach dem Crash-Vorfall neu bewertet werden
   muss (siehe TODO unten). `forEach{}` ist aktuell wieder aktiv, funktioniert (kein Crash),
   ist aber weiterhin nicht lazy.

3. **ROOT CAUSE des eigentlichen User-Reports — Swipe-Geste kollidierte mit Vertikal-Scroll**
   (MainScreen.kt, `ArchivSongRow`): `detectHorizontalDragGestures` akkumulierte `dx`
   UNABHÄNGIG von `dy` — ein schnelles, leicht diagonales Vertikal-Wischen (wie es bei echtem
   Scrollen mit dem Finger immer vorkommt) überschritt dabei versehentlich die 80f-Schwelle für
   Rechts-/Links-Swipe. Erklärte BEIDE User-Symptome auf einen Schlag: (a) das rätselhafte
   Popup "★ Songname → nächster", obwohl kein Song berührt wurde, UND (b) das ruckelige/hakelige
   Scrollen (die Geste kämpfte pro Zeile um dieselben Touch-Events wie die LazyColumn).
   **Fix:** eigener Gesture-Handler (`awaitEachGesture` + `awaitFirstDown` +
   `positionChange()`/`changedToUpIgnoreConsumed()`, alle aus
   `androidx.compose.foundation.gestures`/`androidx.compose.ui.input.pointer`) statt
   `detectHorizontalDragGestures` — entscheidet erst NACH Touch-Slop per Achsen-Dominanz
   (`abs(totalX) > abs(totalY) * 1.5f`): bei Vertikal-Dominanz wird der Touch NICHT konsumiert
   (LazyColumn scrollt ungestört), erst bei klar horizontaler Bewegung wird der Swipe überhaupt
   aktiviert. **Live getestet, User-Feedback: "schon wesentlich besser".**

4. **CI-Build-Fehler beim ersten Push des Gesture-Fixes:** `awaitFirstDown` fälschlich aus
   `androidx.compose.ui.input.pointer` importiert — liegt tatsächlich in
   `androidx.compose.foundation.gestures`. Da lokaler Gradle-Build in dieser Sandbox
   grundsätzlich nicht möglich ist (Google-Maven/Android-Gradle-Plugin 403, siehe
   `/root/.ccr/README.md`), fiel der Fehler erst durch CI auf. **Wichtige Lektion für künftige
   Sessions:** nach jedem Push aktiv den CI-Status prüfen (`mcp__github__actions_list` /
   `get_job_logs`), NICHT einfach `apk-dist` fetchen und annehmen, der neueste Build sei
   erfolgreich — das führte in dieser Session dazu, dass dem User zweimal versehentlich der
   ALTE, noch fehlerhafte APK-Build geschickt wurde, bevor der CI-Fehler bemerkt wurde.

**Commits (diese Session, chronologisch):**
```
dbaa12e  Fix: SetCard & SetSongRow scroll performance (forEach → LazyColumn, alpha → graphicsLayer)  [CRASH, reverted]
477040b  Hotfix: Revert LazyColumn in SetCard (crash on Set open)
08506f2  Fix: Archiv-Swipe löste bei schnellem Vertikal-Scroll fälschlich aus  [CI-Fehler]
356f903  Fix: CI-Build-Fehler durch falschen Import (awaitFirstDown)  [aktueller main-HEAD, CI grün]
```

**Aktueller Code-Stand:**
- `SetSongRow` (GigManagementScreen.kt): graphicsLayer+ModulateAlpha ✅
- `SetCard` Song-Liste (GigManagementScreen.kt): weiterhin `forEach{}`, NICHT lazy (Revert nach Crash) ⚠️
- `ArchivSongRow` (MainScreen.kt): orientierungssensitive Custom-Geste ✅, bereits graphicsLayer+ModulateAlpha aus Vorsession ✅

**Vom User explizit zurückgestellt:** "Kann noch weiter verbessert werden, allerdings möchte
ich das jetzt nicht machen." — kein Crash, kein Bug mehr offen, nur noch Politur-Potential
(siehe TODOs unten).

### TabSync Lyrics-Teleprompter — FINAL RELEASE (2026-07-23)

**IMPLEMENTIERUNG ABGESCHLOSSEN.** Alle Komponenten funktionierten, live getestet vom User ("Das funktioniert richtig super").

#### Was wurde gebaut
- ✅ **LyricsOverlay.kt** — Neuer Teleprompter-Renderer (~400 Zeilen, deklarativ)
  - Scroll aus `positionMs` berechnet (keine Frame-Loop)
  - Top-Anchor (0%) für Abschnitts-Anfänge
  - Zeilengenaues Tippen mit Volt-Balken + Grau-Färbung
  - INSTRUMENTAL-Freeze + VOCAL-Linear-Scroll
  - TextBasierte Instrumental-Erkennung (statt Zeit-Schwelle)

#### Commits (diese Session)
```
57a0e62  Fix: Kalibrierung stoppte nach erstem Tap (Stale-Capture-Bug)
2e3b513  Fix: CI-Build-Fehler durch falschen weight-Import
315c4a1  LyricsOverlay: Top-Anchor, zeilengenaues Tippen, Instrumental-Freeze
a2d0771  LyricsOverlay: Instrumental-Erkennung textbasiert statt Zeit-Schwelle
4b18842  LyricsOverlay: Scroll auf deklarative Berechnung umgestellt
```

#### Bonus: PDF-zu-Lyrics Web-App
- `pdf-to-lyrics.html` (standalone HTML+JS)
- PDF-Upload → Akkorde entfernt → `[Section]`-Format
- Getestet mit zwei echten Songs

#### Nächste Schritte (neue Session)
1. `git branch` → `* main` verifizieren
2. CI-Build prüfen (GitHub Actions)
3. Optional: Live-Test auf echtem Handy
4. Bei Bedarf: Vorlauf-Regler (`lyricsLeadMs`, Feld existiert schon)

---

**⚠️ AB HIER ARCHIVIERTE HISTORIE (Sprints 5.30–5.52):** Beschreibt die alte
Frame-Loop/"Lese-Uhr"-Architektur des Teleprompters — komplett durch die
deklarative Lösung ersetzt (Commits 4b18842–57a0e62, 2026-07-21). Bleibt als
Lessons-Learned stehen (siehe Gotcha 12), beschreibt aber KEINEN existierenden
Code mehr. Bei Bugs im aktuellen Teleprompter NICHT als IST-Zustand nehmen.

### Sprint 5.52 DONE (ungetestet): Fix stale durationMs bei Songwechsel + weicher Lese-/Warte-Übergang

User-Report anhand eines geteilten Diagnose-Logs zu zwei Symptomen: (1) Nach einem
Songwechsel ("Can't judge a book K0") änderte sich am Scroll-Verhalten "nichts",
wirkte quasi eingefroren/zu langsam; (2) generell fühlt sich der Scroll "holperig"
an, manchmal zu langsam — der User wünscht sich, dass jeder neue Abschnitt sanft
oben landet und von dort butterweich weiterläuft ("wie eine Rolltreppe").

**Root Cause 1 (stale durationMs — erklärt Symptom 1):** `PlayerViewModel._durationMs`
wird nur alle 200ms aus `engine.durationMs` aktualisiert (Poll-Loop). Beim
Songwechsel ändert sich `currentSong`/`song.id` sofort, aber `_durationMs` hält für
bis zu 200ms (oft länger, bis ExoPlayer die neue Dauer kennt) noch den Wert des
VORHERIGEN Songs. `LyricsOverlay.latestDurationMs = maxOf(durationMs, dbDurationMs)`
— dieser Guard wurde ursprünglich gebaut, um eine live zu KURZ gemeldete Dauer
abzufangen (siehe FolderImporter "Bug-Fix 2"-Kontext), wirkt aber genau verkehrt,
wenn `durationMs` stale vom VORHERIGEN, längeren Song ist: `maxOf()` wählt dann die
falsche, zu große alte Zahl statt der korrekten `dbDurationMs` des neuen Songs. Im
Log sichtbar: `dur=410425 (db=204000)` für einen frisch gewechselten Song — `dur`
war exakt der Wert des vorherigen Songs.

**Fix 1:** `PlayerViewModel.selectSong()` setzt `_positionMs`/`_durationMs` jetzt
sofort auf `0L`, bevor die Engine die neue Dauer meldet — kein Konsument sieht
mehr den Stale-Wert des alten Songs, `maxOf(0, dbDurationMs)` liefert sofort den
korrekten DB-Wert. Nebeneffekt (gewollt): auch die neue Seek-Bar (Sprint zuvor)
zeigt nach einem Songwechsel sofort 0% statt kurz den alten Fortschritt.

**Root Cause 2 (harter Tempo-Sprung — erklärt Symptom 2, unabhängig von Root Cause 1):**
Im Lese-Uhr-Modell (Sprint 5.48/5.51) wird jedes Segment mit Instrumental-Anteil in
zwei Phasen berechnet — Phase 1 (Lesen im Sing-Tempo `naturalPace`) und Phase 2
(Warten/Gleiten zum nächsten Anker). Der Code schaltete an der Grenze
(`elapsed < readDuration`) HART zwischen beiden Formeln um. Positions-mäßig gab es
dabei keinen Sprung (beide Formeln liefern an der Grenze denselben Pixelwert) —
aber die GESCHWINDIGKEIT ändert sich an dieser Stelle abrupt, teils sehr stark
(Phase 1 und Phase 2 haben oft sehr unterschiedliche Tempi). Das Auge nimmt so eine
Geschwindigkeitsänderung als Ruckeln wahr, auch ohne Positions-Sprung.

**Fix 2:** Beide Phasen werden jetzt über ein kurzes Zeitfenster (symmetrisch um
`readDuration`, max. 1200ms Gesamtlänge) per Smoothstep verblendet, statt hart
umzuschalten. Beide Teilkurven (`pRead`/`pWait`) sind jeweils außerhalb ihres
eigenen Gültigkeitsbereichs flach geklemmt (`coerceIn(0f, 1f)` auf den Fortschritt)
statt linear extrapoliert — dadurch ist jede für sich monoton (nie rückwärts), und
die gewichtete Mischung zwei monotoner, beschränkter Kurven bleibt selbst monoton.
Die bestehende "nur vorwärts"-Klemmung (`scrollOffsetPx` darf nie sinken) bleibt
dadurch strukturell sicher, ohne Sonderfall-Code. Endpunkte (Segment-Anfang landet
exakt oben, Segment-Ende trifft exakt den nächsten Anker) bleiben unverändert —
nur der ÜBERGANG dazwischen ist jetzt weich. Betrifft ausschließlich den
Lese-/Warte-Zweig (Segmente MIT Instrumental-Label); die beiden anderen Zweige
(reines Instrumental-Segment, Segment ohne Instrumental-Label) hatten ohnehin nie
diesen harten Umschaltpunkt und sind unverändert.

- **Nicht verifiziert:** Kein Gradle-Build in dieser Session möglich (Gradle-
  Distribution 403, wie in den Vorsprints dokumentiert). Statisch geprüft:
  Klammerbalance, Typen, Variablen-Scope, Grenzfälle von Hand durchgerechnet
  (`phase2Dur<=0`, `window<=0`, Verhalten exakt an den Segmentgrenzen). **Nächste
  Session: live testen** — Bed of Roses (bestehende Kalibrierung reicht) UND einen
  Songwechsel innerhalb eines Sets testen. Erwartung: (a) nach einem Songwechsel
  läuft die Lese-Uhr sofort mit der richtigen Songlänge, kein "eingefroren"-Gefühl
  mehr; (b) der Übergang zwischen Sing-Tempo und Instrumental-Warten fühlt sich
  jetzt weich an statt ruckelig, Abschnitts-Anfänge landen weiterhin exakt oben.

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

#### 🆕 Geplante Features (noch nicht begonnen, Priorität mit User klären)
- **PDF-zu-Lyrics direkt in der App (statt externem Skript):** User möchte
  Akkord-PDFs künftig direkt in der App importieren können (Song-Editor →
  "Lyrics aus PDF importieren" o.ä.), statt wie bisher über ein externes
  Python-Skript (`/tmp/.../pdf_to_lyrics.py`, Session 2026-07-25, nutzt
  `pypdf` + Regex-Akkord-Filter, lief außerhalb der App wegen eines Content-
  Filter-Vorfalls beim direkten Ausgeben von Songtext-Auszügen im Chat —
  Songtext lief stattdessen NUR durchs Skript, nie durch die Chat-Antwort,
  Ergebnis als Datei-Download an den User).
  - **✅ ENTSCHEIDUNG (2026-07-26, per GrillMe geplant) — Weg A / Phase 1:**
    Umsetzung als Minimalversion. Erst die PDF-**Umbrüche 1:1 aus dem PDF-Textlayer
    übernehmen** (KEINE eigene Umbruch-Regel in Phase 1), damit der User am echten
    Song sieht, ob es reicht — falls nicht, wird eine konkrete Umbruch-Regel erst
    DANN aus einem realen Input→Wunsch-Beispiel abgeleitet (Weg B, zurückgestellt).
    Geklärter Kontext aus dem Interview: (a) Kernschmerz = Medienbruch (raus aus der
    App → externes Skript → zurückkopieren), soll komplett wegfallen; (b) Nutzung zu
    Hause in Ruhe bei der Vorbereitung, kein Bühnen-Speed; (c) Quelle = Ultimate
    Guitar / Web-Export → **echter Textlayer, KEIN OCR nötig**; (d) Offline-Machbarkeit
    war die Hauptsorge des Users — mit PdfBox-Android lokal auf dem Gerät voll
    machbar, kein Server; (e) Ziel-Ablauf im Editor: PDF wählen → gefilterter Text
    **direkt ins Lyrics-Feld**, User prüft & speichert selbst (KEIN Auto-Save); (f)
    9:16-Umbruch-Optimierung ist Wunsch, NICHT Bedingung — bewusst als separates
    Folge-Feature zurückgestellt. Offener Edge-Case fürs Design: falls das Lyrics-Feld
    schon Text enthält, nicht still überschreiben (nachfragen oder anhängen).
    **Konkreter erster Schritt der Umsetzungs-Session:** `PdfBox-Android`-Dependency
    hinzufügen, Akkord-Filter-Regex nach Kotlin portieren, SAF-PDF-Picker + Import-
    Button im `SongEditorSheet` (MainScreen.kt), extrahierten+gefilterten Text ins
    Lyrics-Feld schreiben. Danach an einem echten UG-Song gegen das frühere
    Skript-Ergebnis prüfen.
  - **Machbarkeit (mit User besprochen):** grundsätzlich einfach — die
    Akkord-Filter-Logik ist nur Regex, 1:1 nach Kotlin portierbar (siehe
    Skript-Logik: Zeile gilt als Akkord-Zeile, wenn ≥80% ihrer Tokens auf
    ein Akkord-Pattern passen `^[A-G](#|b)?(m|maj|min|sus|dim|aug|add)?\d{0,2}
    (/[A-G](#|b)?)?$` oder Taktstriche `|`/`:`/`/` sind; `[Section]`-Labels
    bleiben erhalten wie bisher).
  - **Einziger neuer Baustein:** PDF-Text-Extraktion auf Android — es gibt
    KEINE eingebaute API dafür (`PdfRenderer` rendert nur Bilder, kein
    Text-Layer-Zugriff). Empfehlung: `PdfBox-Android`
    (`com.tom-roush:pdfbox-android`), etabliert, ein paar MB zusätzliche
    APK-Größe.
  - **Grenze:** funktioniert nur bei PDFs mit eingebettetem Textlayer (wie
    bei "Here I Go Again" getestet). Eingescannte/reine Bild-PDFs bräuchten
    zusätzlich OCR (z.B. ML Kit Text Recognition) — deutlich größerer
    Aufwand, NICHT Teil dieser Einschätzung, nur falls es später relevant wird.
  - **Vorschlag für die Umsetzung (grober Ablauf, noch nicht verfeinert):**
    SAF-Datei-Picker für PDF im Song-Editor → PdfBox-Android extrahiert
    Text pro Seite → derselbe Akkord-Filter-Regex (Kotlin-Port) → Ergebnis
    direkt ins Lyrics-Textfeld einfügen (User kann vor dem Speichern noch
    manuell nachbessern, kein automatisches Direkt-Speichern).
- **Bluetooth-Fußschalter (Page-Turner-Pedal):** Gab es schon vor dem
  Neustart (`5063e46`, 19.06.) als `de/minitraxx/app/audio/PedalManager.kt` —
  Pedale melden sich als HID-Tastatur, normale `KeyEvent`s + Anlern-Modus pro
  Aktion (PLAY_PAUSE, NEXT). Kein BLE-Pairing-Code nötig. Alter Code als
  Referenz per `git show 5063e46~1:app/src/main/java/de/minitraxx/app/audio/PedalManager.kt`
  abrufbar, muss aber an die neue Architektur (PlayerViewModel/AudioEngine
  statt altem Repository-Pattern) angepasst werden, nicht 1:1 übernehmbar.
- **Echtes Multitrack-Audio über USB-C→USB-B zum Allen & Heath CQ20B:**
  Aktuell mischt `AudioEngine` alle Spuren intern zu 2 Bussen (MAIN links,
  CUE rechts, siehe README-Routing-Konzept) — für den CQ20B sollen stattdessen
  einzelne Spuren als eigene, diskrete Kanäle über USB Audio (UAC2)
  rausgehen. Größerer Umbau: braucht Android-USB-Audio-Multichannel-Output
  (AudioTrack-Channel-Mask statt Stereo-Summierung).
  
  ✅ **FEASIBILITY BEWIESEN (2026-07-24, per Gerätetest am echten CQ20B):**
  Diagnose-Tools in die App gebaut (liegen auf Branch
  `claude/read-current-md-file-7fy90m`, NICHT auf main gemerged):
  `audio/UsbToneTester.kt` (8-Kanal-Dauerton via AudioTrack), `audio/UsbDescriptorScanner.kt`
  (rohe UAC-Descriptor-Auswertung), Buttons im USB-Diagnose-Dialog in `MainScreen.kt`.
  Ergebnisse der Live-Tests:
  - **AudioTrack-Weg scheitert:** 8-Kanal-`AudioTrack` (setChannelIndexMask) wird zwar
    INITIALIZED und schreibt/routet zum CQ20B, aber Android **mischt die USB-Ausgabe
    auf Stereo herunter** (AudioFlinger). Ergebnis am Pult: nur CQ-**Stream**-Modus (2ch)
    bekommt Ton, **Multitrack**-Modus (diskrete Returns) bleibt still. → Standard-Audio-API
    ist eine Sackgasse für echtes Multitrack (iOS/StageTraxx kann es, weil Core Audio
    Mehrkanal-USB nativ ausgibt).
  - **USB-Descriptor-Scan beweist den Pfad:** CQ20B = **VID 0x22F0 / PID 0x0022**, UAC2.
    **IF 1 / alt 1**: AudioStreaming, `bNrChannels=24`, 24-bit (bSubslotSize=4),
    **Endpoint 0x01 = OUT (Wiedergabe), isochron, maxPkt=1024** + Feedback-EP 0x81 IN.
    (IF 2/alt1 = 24ch Aufnahme, EP 0x82 IN.) → Das Pult bietet **24 diskrete
    Wiedergabe-Kanäle** über USB an.
  - **Nötiger Umbau (großes Projekt):** Endpoint isochron bespielen. Androids
    **Java-`UsbDeviceConnection` kann KEINE isochronen Transfers** → nur über **nativen
    C/C++-Code (NDK, libusb bzw. usbfs-URBs)** via `getFileDescriptor()`. Zusätzlich muss
    der Kernel-Treiber `snd-usb-audio` vom Interface **gelöst** werden (USBDEVFS_DISCONNECT
    + claimInterface + SET_INTERFACE alt 1). Bewährter Weg (so macht es „USB Audio Player
    PRO"), aber Wochen-Größenordnung.
  - **✅ UMSETZUNG BEGONNEN (2026-07-24, GrillMe-geplant): NDK-Pfad steht, KK1+KK2 bestanden.**
    Native usbfs-Lösung gebaut & am echten CQ20B (Nothing Phone 3a) getestet:
    - `cpp/usb_detach.c` + `UsbDetachTester.kt`: **Kill-Kriterium 1 BESTANDEN** — Detach
      von `snd-usb-audio` OHNE Root geht (Detach/Claim/SetAlt alle OK). Stock-nahes
      Nothing OS begünstigt das.
    - `cpp/usb_tone.c` + `UsbIsoToneTester.kt`: **Kill-Kriterium 2 BESTANDEN** — 440-Hz-Ton
      isochron (SUBMITURB/REAPURB, ISO_ASAP, eigener pthread, 48kHz/24ch/24-bit-in-32
      MSB-bündig) kam sauber & **NUR auf Kanal 9** an (diskret, kein Übersprechen),
      Paketfehler 45/500955 = **0,009%**.
    - NDK in `app/build.gradle.kts` (26.1.10909125, nur arm64-v8a), `cpp/CMakeLists.txt`;
      Buttons im USB-Diagnose-Dialog (`MainScreen.kt`). CI baut das NDK grün (Build #296).
      `AudioEngine` unberührt. Test-Loop: Code → CI/apk-dist → User testet am Pult.
  - **NÄCHSTER SCHRITT:** **Feedback-Sync** — EP 0x81 (IN, feedback) auslesen und
    Samples/Intervall nachführen. Behebt die Rest-Glitches (User: „Töne kamen nach und
    nach dazu", alle auf Kanal 9 = Sample-Über-/Unterläufe durch frei laufenden Takt) +
    Anlauf-Knacken. Danach: echte Stems statt Sinus, variables Stem→Kanal-Mapping pro Song
    (Kanalzahl variiert, ~8 ideal), App-Mixer (Mute/Gain/Low-Cut — z.B. Drums-Stem muten
    bei echtem Schlagzeuger). Scope Phase 1 bewusst nur Nothing 3a (kein „beliebige Geräte").

#### 🔴 PRIO 1 — Sofort nach Session-Start
1. ✅ **Branch verifizieren:** `git branch` → `* main` zeigen
2. ✅ **CI-Status prüfen:** Letzter Build auf `main` noch grün?
3. 🟡 **Scroll-Performance weiter polieren (User hat das bewusst zurückgestellt, kein Bug,
   nur Wunsch nach mehr Feinschliff):**
   - **SetCard-Songliste ist noch nicht lazy** (`forEach{}` statt `LazyColumn`, siehe Sprint
     2026-07-25). Ein direkter `LazyColumn`-Umbau CRASHTE beim Set-Öffnen (vermutlich
     verschachtelte LazyColumn ohne begrenzte Höhe in einer nicht scrollenden `Column`
     innerhalb der äußeren Sets-LazyColumn). Vor einem erneuten Versuch: Höhen-Constraint
     sauber lösen (z.B. `Modifier.heightIn(max = ...)` auf der inneren LazyColumn, oder ganz
     auf eine flache Struktur ohne verschachtelte Scroll-Container umbauen — evtl. die
     gesamte Song-Liste EINER LazyColumn auf oberster Ebene (Sets + Songs zusammen als ein
     Item-Stream) statt Sets als LazyColumn mit SetCard-Items, die selbst wieder Listen
     enthalten). ERST mit kleinem, isoliertem Testfall (z.B. Emulator/Screenshot-Test oder
     Constraint-Logging) verifizieren, dass KEIN Crash mehr auftritt, bevor an den User
     ausgeliefert wird — die App crashte beim letzten Versuch sofort und vollständig.
   - Danach ggf. auch bei `SetCard` selbst state-turbulence reduzieren (Flow-Subscriptions
     deduzieren, siehe alte Diagnose-Befunde 3+4 aus der Session vom 2026-07-25 — Details im
     Abschnitt "Scroll-Performance Archiv & Gig-Verwaltung" oben).
   - **WICHTIG:** vor jeder Auslieferung an den User: CI-Status aktiv per
     `mcp__github__actions_list`/`get_job_logs` prüfen, NICHT nur `apk-dist` fetchen — sonst
     Risiko, einen alten/kaputten Build zu verschicken (siehe Lektion aus 2026-07-25).
4. 🔵 **Optional:** Lyrics-Teleprompter Live-Test auf echtem Handy
   - Song mit Lyrics laden
   - Teleprompter öffnen (Tap auf Song-Titel)
   - Record → zeilengenaues Tippen → Stop
   - Abspielen: Läuft Text smooth? Jede Zeile oben beim Singen?

#### 🟠 PRIO 2 — Falls nötig
- **Vorlauf-Regler (`lyricsLeadMs`):** Falls konstanter Zeit-Offset bleibt (~0,3–0,5s)
  - Feld + Migration v17 existiert schon
  - Nur UI (−/+ Buttons im Header) nötig

#### ✅ ERLEDIGT (diese Session)
- ✅ **Scroll-Performance Archiv & Gig-Verwaltung (ERLEDIGT, Session 2026-07-25):** Swipe-
  Gesture-Bug im Archiv behoben (Fehl-Popup + Ruckeln bei schnellem Vertikal-Wischen), dimm-
  Performance (graphicsLayer+ModulateAlpha) auch in SetSongRow nachgezogen. Vom User live
  getestet, "schon wesentlich besser". Details siehe Abschnitt "Scroll-Performance Archiv &
  Gig-Verwaltung" oben. Weitere Politur (SetCard-Lazy-Loading) bewusst vom User zurückgestellt,
  siehe PRIO 1 oben — nicht mehr akut, aber offen.
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
- 🔴 **Teleprompter: Sprint 5.52 (Stale-durationMs-Fix + weicher Lese-/Warte-
  Übergang) live testen (PRIO 1):** Bestehende Kalibrierung von Bed of Roses
  reicht, KEIN Neu-Tippen nötig. Zwei Dinge gezielt prüfen: **(a)** Songwechsel
  innerhalb eines Sets — läuft die Lese-Uhr beim neuen Song sofort mit der
  richtigen Songlänge (kein "wirkt eingefroren/zu langsam" mehr direkt nach dem
  Wechsel)? **(b)** Bed of Roses komplett durchlaufen lassen — fühlt sich der
  Übergang zwischen Sing-Tempo (Lesen) und Instrumental-Warten jetzt weich an
  statt ruckelig, landet jeder Abschnitts-Anfang weiterhin exakt oben? Diagnose-
  Log bei Problemen erneut senden. Der 5.51-Fix (lokales statt globales Tempo
  bei Segmenten ohne Instrumental-Label) ist inhaltlich weiterhin ungetestet —
  bei diesem Durchlauf gleich mitbeurteilen: taucht `instrumental=true` nur beim
  Solo-Segment auf, `false` bei den anderen? Falls nach diesem Test noch ein
  kleiner konstanter Zeitversatz spürbar bleibt: Vorlauf-Feld (`lyricsLeadMs`,
  Migration v17 bereits da) wäre die naheliegende, saubere Feinjustierung.
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
