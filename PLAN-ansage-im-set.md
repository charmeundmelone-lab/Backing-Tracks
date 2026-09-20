# PLAN — Ansage/Pause-Schnellzugriff im Set (verbindet TODO 8: Player im Lyrics-Fenster)

Umsetzungs-Spezifikation aus einem GrillMe-Interview (2026-09-20) plus
anschließendem Logikfehler-Review. Die Entscheidungen unten sind final
abgestimmt. **Kein Code wurde in dieser Session angefasst** — reine Planung.

## Zweck (Kernfakten aus dem Interview)

- User mag die Show-Automatik (Ansagen + Pause) sehr, will sie aber nicht
  nur über den Song-Editor im Archiv einrichten können, sondern **schnell
  direkt im Set**.
- Ausdrücklich mit TODO 8 (normaler Player soll unten im Lyrics-Fenster
  mitlaufen) verbunden — beide sollen **in einem Rutsch** entstehen, weil
  sie dieselbe Player-UI-Basis erweitern.

## Logikfehler, der den ursprünglichen Plan gekippt hat

Erster Entwurf: ein Icon **im Player** (GlobalPlayer/PlayerInfoBar), das auf
`currentSong` wirkt — man tippt dafür erst die Set-Zeile an, um den Song
„anzuwählen". Beim Gegenprüfen im Code:

- `PlayerViewModel.selectSong()` → `AudioEngine.load()` → `releaseCurrent()`
  reißt die aktuell laufenden ExoPlayer-Tracks ab.
- Heißt: Song A läuft, User tippt Song B an, nur um kurz dessen Ansage zu
  checken → **Song A stoppt sofort**, mitten im Gig.
- Deshalb: der Player-Icon-Weg ist NICHT sicher für „mal kurz einen anderen
  Song bearbeiten, während der aktuelle läuft" — genau der Hauptnutzen, den
  der User wollte.

**Fix:** zwei Zugänge statt einem, siehe Entscheidung 2/4 unten.

## Entscheidungen

1. **UI-Form — schlankes Mini-Sheet.** Zeigt NUR die bestehenden
   Automatik-Bausteine: `VoiceNoteRow` (Vorlauf-Notiz, Nachlauf-Notiz) +
   den `ManualPauseKeypadDialog`/Pause-Timer. Kein Titel, keine Lyrics,
   kein Mixer, keine anderen Song-Editor-Felder.

2. **Primärer, sicherer Zugang — Icon in der Set-Zeile.** Das bestehende
   Mic-`Icon` in `SetSongRow` (`GigManagementScreen.kt`, aktuell nur
   `Icon`, kein `IconButton`, sichtbar wenn `introNoteFilePath` oder
   `outroNoteFilePath` gesetzt sind) wird zu einem echten `IconButton` mit
   eigenem `onClick`, der **direkt** das Mini-Sheet öffnet — OHNE
   `onPlay`/`selectSong`/`loadSetAsQueue`, also ohne jeden Kontakt zur
   AudioEngine. Sicher bei JEDEM Song, jederzeit, auch während ein anderer
   Song spielt. (Technisch bestätigt: die Row hat außen ein
   `Modifier.clickable{ onPlay() }`, ein `IconButton` innen konsumiert den
   Klick vorher — exakt das gleiche Muster wie das bestehende
   Entfernen-X/End-Aktion-Icon im Edit-Modus.)

3. **Icon immer sichtbar, nicht nur wenn schon Ansage existiert.** Die
   `if (introNoteFilePath.isNotBlank() || outroNoteFilePath.isNotBlank())`-
   Bedingung fällt weg — leer = dezentes Outline-Icon, befüllt = wie
   bisher kräftig. Ermöglicht Neuanlage einer Ansage/Pause direkt im Set,
   ohne Archiv-Umweg.

4. **Sekundärer, bequemer Zugang — Icon im Player.** Zusätzliches Icon in
   `PlayerInfoBar`/`GlobalPlayer` (`MainScreen.kt`), wirkt auf
   `currentSong`. **Nur sichtbar im Gig-Set-Modus** (`isGigSetMode`,
   analog zum bestehenden endAction-Button). Öffnet dasselbe Mini-Sheet
   wie Punkt 2 — für den Song, der ohnehin schon angewählt/gerade aktiv
   ist, kein Risiko, weil kein zusätzlicher `selectSong()`-Aufruf nötig
   ist.

5. **Schutz vor Verwechslung.** Das Mini-Sheet zeigt oben groß
   „Für: `<Songname>` – `<Künstler>`" — das reicht als Absicherung, kein
   zusätzlicher Bestätigungsschritt nötig.

6. **Performance-Lock.** `isLocked` sperrt BEIDE Zugänge (Row-Icon UND
   Player-Icon) wie alle anderen Set-Editier-Aktionen (Gotcha 10).

7. **Kein technischer Lärmschutz** bei Live-Aufnahme (PA-Lärm während des
   Gigs). Bewusste Nutzungssache — die Aufnahme gehört in ruhige Momente
   (Pause, Backstage, Soundcheck), das ist bereits so gedacht.

8. **Verbindung zu TODO 8.** Der normale Player (Seekbar, Play/Pause/Stop,
   Loop, Automatik-Countdown) wird in `LyricsOverlay.kt` eingebettet;
   das Player-Icon aus Punkt 4 zieht dabei automatisch mit rein, weil
   beide dieselbe Player-UI-Basis erweitern. **Trotzdem in mehreren
   kleinen Commits umsetzen**, nicht als ein Riesen-Block — nach jedem
   Schritt CI/Gerät prüfen (bewährte Vorgehensweise dieses Projekts).

## Offene technische Prüfung (keine Nutzer-Entscheidung, beim Bauen klären)

- Lässt sich `VoiceNoteRow` + Aufnahme-/Preview-Logik (separater
  `MediaRecorder`/`MediaPlayer`, siehe Show-Automatik-Sprint) aus dem
  `SongEditorSheet`-Kontext in eine eigenständige, wiederverwendbare
  Composable extrahieren — genutzt sowohl vom neuen Mini-Sheet als auch
  weiterhin vom `SongEditorSheet`? Ziel: keine Duplizierung.
- Für den eingebetteten Player in `LyricsOverlay.kt`: `MainScreen.kt`s
  `GlobalPlayer` müsste seine Zustände/Callbacks (nextSong, Loop-State,
  Automatik-Status, jetzt zusätzlich: Ansage-Icon-Callback) so
  weiterreichen, dass `LyricsOverlay` sie ohne Doppel-Implementierung
  nutzt.

## Voraussichtlich betroffene Dateien

- `ui/GigManagementScreen.kt` — `SetSongRow`: Mic-`Icon` → `IconButton`,
  immer sichtbar, öffnet Mini-Sheet direkt.
- `ui/MainScreen.kt` — neue Mini-Sheet-Composable (nur Automatik-Felder),
  neues Icon in `PlayerInfoBar`/`GlobalPlayer` (nur `isGigSetMode`).
- `ui/LyricsOverlay.kt` — eingebetteter Player (TODO 8) inkl. desselben
  Icons.
- Eventuell neue gemeinsame Datei/Funktion für die aus `SongEditorSheet`
  extrahierten Automatik-Bausteine.

## Test & Rollout

- Kein Gradle-Build in der Sandbox möglich (Google-Maven 403, wie immer)
  — nur manuell gegenlesen, dann CI aktiv prüfen.
- **Zentraler Regressionstest** (deckt genau den gefundenen Logikfehler
  ab): Song A läuft → Row-Icon eines ANDEREN Songs (B) im Set antippen →
  Mini-Sheet öffnet sich, Song A spielt **ungestört weiter**, keine
  Unterbrechung.
- Live-Checkliste sonst: Row-Icon bei Song ohne Ansage öffnet leeres
  Sheet → Notiz aufnehmen → speichern → Icon wird kräftig. Player-Icon
  nur im Gig-Set-Modus sichtbar, verschwindet beim freien Abspielen aus
  dem Archiv. `isLocked` sperrt beide Icons.

## Nächster Schritt

Code bauen (kleine Commits), jeweils CI prüfen, dann am Gerät live testen
— siehe „Zentraler Regressionstest" oben als wichtigsten Einzelfall.
