# PLAN — Setlist-Lesbarkeit & Übersichtlichkeit (Variante B)

Umsetzungs-Spezifikation für die Redesign-Session. Ausgangspunkt ist ein
GrillMe-Interview mit dem User; die Entscheidungen unten sind final abgestimmt.
Optische Referenz: Artifact „Setlist-Redesign — Entwürfe", **Variante B (Chart)**.

## Die drei goldenen Regeln (Leitplanken für JEDE Entscheidung)

1. **Optische Lautstärke folgt Wichtigkeit.** Das Größte/Hellste/Kontrastreichste
   gehört dem, was auf der Bühne in einer halben Sekunde abgelesen werden muss.
2. **Seltene Struktur-Knöpfe tragen ein Wort.** Nur blind getroffene Dauer-Knöpfe
   (Play/Stop/Loop) dürfen wortlos sein. Selten benutzte „Anlegen/Import"-Aktionen
   brauchen ein Label.
3. **Eine Akzentfarbe = eine Bedeutung.** Gelb (`GigVolt`) heißt ab jetzt
   ausschließlich „läuft/aktiv". Musik-Info (Tonart/Kapo) und Aktions-Buttons sind
   neutral, nicht gelb.

## Kein Datenmodell-Umbau nötig

`capoPosition: Int = 0` und `keySignature: String = ""` existieren bereits in
`data/Song.kt` (Zeile 18–19) und sind über `songInSet.song.*` erreichbar.
**Keine Room-Migration.** Wenn die Felder in der DB leer/0 sind, greifen die
Ausblend-Regeln unten (Kapo 0 → weg, leere Tonart → weg).

---

## Änderung 1 — Die Song-Zeile (Variante B)

**Datei:** `ui/GigManagementScreen.kt`, `SetSongRow` (ab Zeile 954).

Variante B = **Songname groß, darunter eine feine Meta-Zeile** aus Tonart · Kapo · Dauer.

### 1a. Nummer wird dezent grau (Regel 1)
- Zeile ~1063–1067: Nummer-`Text`.
  - `fontSize = 24.sp` → **`14.sp`**
  - `fontWeight = FontWeight.Black` → **`FontWeight.Medium`**
  - Farbe `voltColor` → **`GigGray`** (immer grau — auch bei aktivem/gespieltem Song).
  - `Box(Modifier.width(44.dp))` → **`width(26.dp)`**
  - Format `"%02d"` → **`"%d"`** (ohne führende Null, dezenter).
- **Spontan-Marker bleibt:** der gold-gelbe `Icons.Filled.Star` (Zeile 1068–1074) bleibt
  als eigener Wunsch-Indikator erhalten. `voltColor` wird sonst nirgends mehr für die
  Nummer gebraucht — die Variable kann entfallen oder nur noch den Star-Farbwert liefern.

### 1b. Songname wird dominant (Regel 1)
- Zeile 1079: Titel-`Text`.
  - `fontSize = 15.sp` → **`20.sp`**, `FontWeight.Bold` bleibt.
  - Farbe: **normal `GigWhite`, aber `if (isCurrentSong) GigVolt`** (aktiver Song = gelb, Regel 3).

### 1c. BPM raus, Tonart + Kapo (nur wenn >0) + Dauer rein
- Zeile 981–984 (`bpmTxt`, `pre`, `subtitle`) **ersetzen**. Neue Meta-Zeile bauen:
  ```kotlin
  val key  = songInSet.song.keySignature.trim()
  val capo = songInSet.song.capoPosition
  val metaParts = buildList {
      if (key.isNotEmpty()) add(key)          // Tonart
      if (capo > 0)         add("Kapo $capo") // Kapo NUR wenn > 0
      add(songInSet.song.duration)            // Dauer bleibt, klein
  }
  ```
- Zeile 1081: die alte Subtitle-`Text` ersetzen durch eine Meta-Zeile mit **Tonart in
  einem neutralen Cool-Ton, Rest grau** (Regel 3 — Musik-Info ist nicht gelb):
  - Neue Farbe anlegen (bei den anderen `Gig*`-Farbkonstanten): `val GigCool = Color(0xFF9FB2C4)`.
  - Rendern als **eine** Zeile, `fontSize = 12.sp`. Empfohlen via `AnnotatedString`:
    Tonart-Segment `GigCool` + `FontWeight.Medium`, Trenner `  ·  ` und Rest `GigGray`.
  - Falls einfacher gewünscht: ganze Zeile `GigGray`, Tonart als erstes Segment in `GigCool`.
- **Artist entfällt** in der Zeile (war Teil des alten Subtitles; im Bühnen-Blick nicht
  priorisiert). Bei Bedarf später leicht wieder anhängbar.

### 1d. Aktiv-Zustand deutlicher (Regel 1/3)
- Zeile 1036: Hintergrund `GigVolt.copy(alpha = 0.18f)` für `isCurrentSong` **bleibt**.
- **Optional (empfohlen, entspricht Mockup):** zusätzlich eine 3dp breite `GigVolt`-Leiste
  am linken Zeilenrand bei `isCurrentSong` (z.B. via `Modifier.drawBehind` oder eine
  schmale `Box`). Kein Muss.

### NICHT anfassen in dieser Zeile
- **Swipe-Gestik** (`pointerInput`, `detectHorizontalDragGestures`, `rememberUpdatedState`,
  `guarded`, die Dialoge) — fragil, siehe Gotcha 6/7 in CLAUDE.md. Nur Text/Farbe/Größe ändern.
- **Dimmen gespielter Songs**: `graphicsLayer { alpha; compositingStrategy = ModulateAlpha }`
  (Zeile 1037–1040) bleibt unverändert.
- **Edit-Mode-Controls** (endAction-Button, Entfernen-X, Zeile 1084–1091) bleiben.

### Konsistenz-Nachzug (empfohlen, gleiche Regeln anwenden)
- `SetSongRowSortable` (Zeile 889) rendert dieselbe Zeile im Sortier-Modus — Nummer/Titel
  dort optisch gleich behandeln, damit der Sortier-Modus nicht abweicht.

---

## Änderung 2 — Kopf: Gig als leise Brotkrume (Regel 1)

**Datei:** `ui/GigManagementScreen.kt`, `GigDetailView` (ab Zeile 243).

Der Gig-Name wird im Gig fast nie gebraucht (User bestätigt) → er darf nicht so laut sein
wie das Set. Umsetzung: **Breadcrumb `jan › first`** statt großem Titel + den gelben „+"
aus dem Kopf entfernen.

### 2a. Header-Zeile (280–293) umbauen
- Zeile 287: `Text(gig.name, … 17.sp Bold GigWhite)` → **Breadcrumb-Row**:
  - `gig.name` in `GigGray`, `13.sp` → dann Trenner ` › ` (`GigGray`, gedämpft) →
    dann `currentSet?.name` in `GigWhite`, `13.sp`, `FontWeight.Medium`.
  - Ist `currentSet == null`: nur `gig.name` anzeigen.
  - Der Back-Arrow (283–286) bleibt.
- Zeile 289–292: den `IconButton` mit `Icons.Filled.Add` („Neues Set", gelbes „+") **hier
  entfernen**. Set-Anlegen wandert in den Set-Wechsler (siehe 2b).

### 2b. „Set anlegen" lebt im Wechsler-Menü (Regel 2 — Aktion bei ihrem Objekt)
- `SetSwitcherSheet` hat bereits `onCreateSet` (Aufruf Zeile 359). **Sicherstellen**, dass
  der Eintrag dort als klar beschriftete Zeile **„+ Neues Set"** erscheint (Wort, nicht nur „+").
- Der Set-Wechsler wird über den „Wechseln"-Griff (297–319) geöffnet — dort gehört das
  Anlegen hin.

### 2c. Leerer Gig braucht einen sichtbaren Anlegen-Button (Regressions-Falle!)
- Empty-State (321–331) sagt aktuell „Tippe + um ein Set anzulegen" — nach Entfernen des
  Header-„+" gäbe es sonst **keinen Weg mehr**, im leeren Gig ein Set anzulegen.
- **Fix:** im Empty-State einen sichtbaren Button **„+ Neues Set"** ergänzen (setzt
  `showDialog = true`), Text entsprechend anpassen.

### Duplikat-Hinweis (bewusst ok)
Breadcrumb zeigt den Set-Namen und die Set-Karte darunter ebenfalls — die Karte ist aber
der **Bedien-Griff** („Wechseln"), die Brotkrume nur **Kontext**. Kein Konflikt.

---

## Änderung 3 — Import-Button bekommt ein Wort (Regel 2)

**Datei:** `ui/MainScreen.kt`, Top-Toolbar, Zeile 348:
`Icon(Icons.Filled.AddCircleOutline, contentDescription = "Import", …)`.

- Statt nacktem Icon: **Icon + Text-Label „Import"** (kompakte Pill oder `Row` mit
  kleinem Label rechts neben dem Icon). Platz in der Top-Bar beachten — kompakt halten.

---

## Änderung 4 — Gelb-Disziplin durchziehen (Regel 3)

Gelb (`GigVolt`) nur noch, wo etwas **läuft/aktiv** ist. Prüfen & neutralisieren:
- ✅ Nummern → grau (Änderung 1a).
- ✅ Aktiver Song → Titel + Hintergrund + Leiste gelb (das ist „aktiv", korrekt).
- ⚠️ **„Wechseln"-Griff** (Zeile 314–317): Text/Icon sind `GigVolt`. „Wechseln" ist eine
  Aktion, kein aktiver Zustand → **auf neutral umstellen** (`GigWhite`/`GigGray`).
- ⚠️ Import-Pill (Änderung 3): **neutral** halten (nicht gelb).
- **Bewusst NICHT anfassen:** die endAction-Statusfarben (CUE hellblau / STOP rot /
  AUTOPLAY gelb) — das ist eine eigene Zustands-Codierung, keine Deko. Separat lassen.

---

## Änderung 5 — Terminologie: nur „Set", nie „Playlist"

- Repo nach UI-sichtbaren „Playlist"-Strings durchsuchen (`grep -rn "Playlist"` in `ui/`)
  und auf „Set" vereinheitlichen. (Die `Playlist`-Room-Entity darf intern so heißen —
  es geht NUR um für den User sichtbaren Text.)

---

## Was ausdrücklich gut ist und bleibt

- **Drei Zustände** (läuft / gespielt / kommt) sind für den User bereits auf einen Blick
  erkennbar → Erkennungs-Logik nicht ändern, nur die Farbgebung an Regel 3 anpassen.
- Swipe-to-Queue, endAction-Live-Button, Set-Umschalten, Sortier-Modi: unberührt lassen.

---

## Test & Rollout

- **Kein Gradle-Build in der Sandbox möglich** (Google-Maven 403). Nach dem Push:
  1. CI-Status aktiv prüfen (`mcp__github__actions_list` / `get_job_logs`), NICHT blind
     `apk-dist` fetchen.
  2. Grüner Release-Build → APK an den User zum Live-Test.
- **Live-Checkliste für den User:** Songname aus Distanz lesbar? Tonart/Kapo korrekt,
  Kapo bei 0 unsichtbar? Gig-Brotkrume leise, Set-Name dominant? „Import" & „Neues Set"
  beschriftet und auffindbar (auch im leeren Gig)? Gelb nur noch beim laufenden Song?

## Scope-Grenze (Phase 2, bewusst später)

- `ArchivSongRow` (`MainScreen.kt` Zeile 915) benutzt dasselbe laute Nummern-/Titel-Muster.
  Für App-weite Konsistenz später gleich behandeln — **nicht** Teil dieser ersten Runde,
  um den Diff fokussiert und testbar zu halten.
