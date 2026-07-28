# PLAN — Tempo-Filter im Songarchiv

Umsetzungs-Spezifikation aus einem GrillMe-Interview (2026-07-28). Die
Entscheidungen unten sind final abgestimmt.

## Zweck (Kernfakten aus dem Interview)

- Genutzt **live auf der Bühne** (filtern → mehrere Songs markieren → ins Set
  hängen, bestehender Batch-Weg) **und** bei der **Set-Planung zu Hause**.
- Muss auch bei aktivem Performance-Lock funktionierten (reines Anzeigen,
  keine Set-/DB-Änderung).
- **Kein Genre-Filter** — nur Tempo. Kein BPM-basierter Automatik-Filter,
  weil BPM-Daten im Bestand unzuverlässig sind → **manuelles Tag pro Song**.
- Bestand aktuell < 50 Songs → Editor-Weg zum Taggen ist vertretbar, kein
  Batch-Tagging nötig.

## Entscheidungen

1. **Drei Chips:** „Langsam" · „Mittel" · „Schnell" (deutsch, wie der Rest
   der App). Keine BPM-Zahlen, keine Genre-Namen.
2. **Exklusiv:** genau ein Chip aktiv, nochmal tippen = aus. Keine
   Kombinationen (kein UND/ODER mehrerer Chips).
3. **Ungetaggte Songs werden ausgeblendet**, wenn ein Filter aktiv ist —
   kein vierter „ohne Tempo"-Zustand im UI.
4. **Erreichbarkeit:** Lupe tippen → Suchfeld UND Chip-Reihe erscheinen
   zusammen (kein zweites Aufklappen). Zwei Taps bis zum gefilterten
   Ergebnis.
5. **Lebensdauer:** Filter stirbt mit der Suche — Suche schließen setzt ihn
   automatisch zurück auf „kein Filter". Keine Persistenz über
   App-Neustart.
6. **Tag setzen:** ausschließlich im `SongEditorSheet` (kein Batch-Chip in
   der Archiv-Auswahlleiste, anders als Genre). Im Archiv selbst **nicht
   sichtbar** — die gerade auf Lesbarkeit getrimmte Meta-Zeile
   (Tonart · Kapo · Dauer) bleibt unverändert.
7. Filter und Textsuche wirken zusammen (UND-Verknüpfung).

## Datenmodell (Room v18→19)

`data/Song.kt`: neues Feld
```kotlin
val tempoTag: Int = 0 // 0=ungetaggt, 1=Langsam, 2=Mittel, 3=Schnell
```

`data/AppDatabase.kt`: `version = 19`, neue Migration
```kotlin
private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN tempoTag INTEGER NOT NULL DEFAULT 0")
    }
}
```
In `addMigrations(...)` ergänzen (ADD-only-Konvention, wie alle bisherigen
Migrationen).

## ViewModel (`ui/PlayerViewModel.kt`)

- Neuer State neben `_searchQuery`:
  ```kotlin
  private val _tempoFilter = MutableStateFlow(0) // 0=kein Filter, 1..3=Tag
  val tempoFilter: StateFlow<Int> = _tempoFilter.asStateFlow()

  fun setTempoFilter(tag: Int) {
      _tempoFilter.value = if (_tempoFilter.value == tag) 0 else tag
  }
  ```
- `filteredSongs` kombiniert jetzt drei Flows (`combine` mit 3 Flows ist
  bereits importiert) statt zwei; Tempo-Bedingung UND Textsuche:
  ```kotlin
  val filteredSongs: StateFlow<List<Song>> = combine(songs, _searchQuery, _tempoFilter) { list, q, tempo ->
      list.filter { s ->
          (tempo == 0 || s.tempoTag == tempo) &&
          (q.isBlank() ||
              s.title.contains(q, ignoreCase = true) ||
              s.artist.contains(q, ignoreCase = true) ||
              s.bpm.toString().contains(q) ||
              s.keySignature.contains(q, ignoreCase = true) ||
              s.genre.contains(q, ignoreCase = true))
      }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  ```
- `saveSongEdits(...)` bekommt einen zusätzlichen Parameter `tempoTag: Int`
  und schreibt ihn in die eine atomare `dao.update()` (gleiches Muster wie
  Capo/Titel/Artist — siehe Capo-Speicher-Bug-Historie, KEIN separater
  Schreibpfad).

## UI — Suchleiste (`ui/MainScreen.kt`, `SearchBar`)

- `SearchBar`-Signatur um `tempoFilter: Int` und `onTempoFilter: (Int) -> Unit`
  erweitern.
- Wenn `active == true`: unter/neben dem Suchfeld eine kompakte Chip-Reihe
  mit „Langsam" / „Mittel" / „Schnell" (gleiches Button-Pattern wie die
  Genre-Chips in `GenreBar`, aber als Toggle mit sichtbarem aktiven Zustand
  — aktiver Chip `Volt`-gefüllt mit schwarzem Text, inaktive `BgCard` mit
  `Volt`-Text, analog zum bestehenden Auswahl-Look).
- Aufrufer (`ArchivTab`) übergibt `vm.tempoFilter` und
  `{ vm.setTempoFilter(it) }`.
- `onToggle`-Lambda beim Schließen der Suche (`searchActive = false`) ruft
  zusätzlich `vm.setTempoFilter(0)` auf (Regel 5 — Filter stirbt mit der
  Suche).

## UI — Song-Editor (`ui/MainScreen.kt`, `SongEditorSheet`)

- Neuer lokaler State `var tempoTag by remember(song.id) { mutableStateOf(song.tempoTag) }`.
- Drei-Chip-Reihe („Langsam" · „Mittel" · „Schnell", exklusiv wählbar,
  gleiches Toggle-Pattern wie die Suchleisten-Chips) unter dem Capo-Stepper.
- `onSave(...)`-Signatur um `tempoTag: Int` erweitern (8. Parameter),
  Aufrufer in `MainScreen.kt` reicht ihn an `vm.saveSongEdits(...)` durch.

## Bewusst NICHT angefasst

- Meta-Zeile in `ArchivSongRow`/`SetSongRow` — Tempo bleibt dort unsichtbar.
- `GenreBar` (Batch-Genre-Zuweisung) — kein Tempo-Batch-Chip, Tagging läuft
  nur einzeln über den Editor.
- Kein Persistieren des Filterzustands (SharedPreferences o.ä.).

## Test & Rollout

- Kein Gradle-Build in der Sandbox möglich (Google-Maven 403) — nur
  manuell gegengelesen (Klammerbalance, Typen, Aufrufstellen).
- Nach Push: CI-Status aktiv prüfen (`mcp__github__actions_list` /
  `get_job_logs`), dann APK an den User.
- Live-Checkliste: Lupe → Chips erscheinen sofort · Chip antippen filtert ·
  nochmal antippen hebt auf · Song ohne Tag verschwindet im aktiven Filter ·
  Suche schließen setzt Filter zurück · Tag im Editor setzen/ändern bleibt
  nach Speichern erhalten.
