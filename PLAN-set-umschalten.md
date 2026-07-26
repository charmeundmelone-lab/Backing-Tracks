# Umsetzungsplan: Set-Umschalten im Gig (Einzel-Set-Ansicht + Übersicht)

> Ziel: Sets innerhalb eines Gigs elegant wechseln, ohne durch die komplette
> Songliste des vorherigen Sets scrollen zu müssen. Geplant per GrillMe am
> 2026-07-26. Dieser Plan ist so geschnitten, dass er Schritt für Schritt
> (auch von einem kleineren Modell) umsetzbar ist.

## Geklärtes Verhalten (verbindlich, nicht neu diskutieren)

1. **Immer nur EIN Set sichtbar** — die volle Songliste nur des aktiven Sets, maximaler Platz.
2. **Griff oben** bei der Gig-Überschrift: zeigt den aktuellen Set-Namen + „gespielt X/Y", ein Tipp öffnet die Übersicht. (Kein Dauer-Panel — versteckt hinter einem Tipp.)
3. **Übersicht** = kompakte Liste ALLER Sets (Name + Song-Anzahl + gespielt-Zähler, aktives Set markiert). Ein Tipp auf ein Set → umschalten. Dient GLEICHZEITIG als zentrale **Set-Verwaltung**: umbenennen, löschen, neu anlegen, Reihenfolge ändern. Enthält außerdem den Auto-Übergang-Schalter (siehe 6).
4. **Umschalten = Armen** (ein einziger Zustand „aktives Set"), aber **ohne Unterbrechung**: ein gerade laufender Song spielt ungestört zu Ende, das neue Set übernimmt erst danach.
5. **Song-bezogene Aktionen bleiben in der Einzel-Set-Ansicht**: Songs hinzufügen, Songs umsortieren, End-Aktion pro Song (CUE/STOP/▶▶), gespielt-Markierungen zurücksetzen. Nur set-weite Aktionen (umbenennen/löschen/neu/sortieren) wandern in die Übersicht.
6. **Auto-Übergang ins nächste Set** (wenn ein Set fertig durchgespielt ist): **optional, pro Gig einstellbar, Standard AUS**.
7. **Beim Öffnen eines Gigs**: immer das **zuletzt aktive Set** dieses Gigs wieder anzeigen/armen (Option A). „Von vorn" ist der bewusste Reset der gespielt-Markierungen. Beim allerersten Öffnen (kein letztes Set gespeichert): erstes Set.

---

## Schritt 1 — Datenmodell & DB-Migration (Room v17 → v18)

**Datei `data/GigEntity.kt`** — zwei Felder ergänzen:
```kotlin
@Entity(tableName = "gigs")
data class GigEntity(
    @PrimaryKey(autoGenerate = true) val gigId: Long = 0,
    val name: String,
    val lastActiveSetId: Long = 0L,      // 0 = noch keins → erstes Set nehmen
    val autoAdvanceSets: Boolean = false // Auto-Übergang ins nächste Set, pro Gig
)
```

**Datei `data/GigDao.kt`** — zwei Update-Queries ergänzen:
```kotlin
@Query("UPDATE gigs SET lastActiveSetId = :setId WHERE gigId = :gigId")
suspend fun updateLastActiveSet(gigId: Long, setId: Long)

@Query("UPDATE gigs SET autoAdvanceSets = :enabled WHERE gigId = :gigId")
suspend fun updateAutoAdvance(gigId: Long, enabled: Boolean)
```

**Datei `data/AppDatabase.kt`:**
- `version = 17` → `version = 18`
- Neue Migration nach dem Muster von `MIGRATION_16_17`:
```kotlin
private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE gigs ADD COLUMN lastActiveSetId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE gigs ADD COLUMN autoAdvanceSets INTEGER NOT NULL DEFAULT 0")
    }
}
```
- In `.addMigrations(...)` ganz am Ende `MIGRATION_17_18` ergänzen.

> ⚠️ Gotcha 1 in CLAUDE.md danach anpassen: „Room v18 — nächste Migration wäre 18→19".

---

## Schritt 2 — Fortschritt pro Set (für „gespielt X/Y")

**Datei `data/SetDao.kt`** — eine Aggregat-Query + Rückgabe-Klasse:
```kotlin
data class SetProgress(val setId: Long, val total: Int, val completed: Int)

@Query("""
    SELECT setId AS setId, COUNT(*) AS total, SUM(isCompleted) AS completed
    FROM set_song_cross_ref WHERE setId IN (:setIds) GROUP BY setId
""")
suspend fun getSetProgress(setIds: List<Long>): List<SetProgress>
```
(Sets ohne Songs tauchen nicht auf → in der UI als 0/0 behandeln.)

**Datei `ui/GigViewModel.kt`** — dünner Wrapper:
```kotlin
suspend fun setProgress(setIds: List<Long>): Map<Long, SetProgress> =
    withContext(Dispatchers.IO) { setDao.getSetProgress(setIds).associateBy { it.setId } }
```

---

## Schritt 3 — GigViewModel: Umschalten, Auto-Übergang, Verwaltung

Die vorhandenen `armSetIfIdle` / `loadSetAsQueue` bleiben unverändert bestehen.
Neu hinzufügen:

### 3a) `switchToSet` — umschalten = armen OHNE Unterbrechung
```kotlin
fun switchToSet(gigId: Long, setId: Long, playerVm: PlayerViewModel) {
    viewModelScope.launch {
        _activeSetId.value = setId
        withContext(Dispatchers.IO) { gigDao.updateLastActiveSet(gigId, setId) }
        installSetCompletedCallback(playerVm)   // siehe 3b (aus armSetIfIdle extrahiert)

        if (playerVm.isPlaying.value) {
            // Läuft gerade ein Song → NICHT unterbrechen. Nur die Queue auf das
            // neue Set umbiegen; der laufende Song bleibt current, danach kommt
            // das neue Set. reloadQueueFromSet filtert den current-Song raus.
            withContext(Dispatchers.IO) { reloadQueueFromSet(setId, playerVm) }
        } else {
            // Idle/pausiert → neues Set sofort armen (erster ungespielter Song).
            val songs = withContext(Dispatchers.IO) { setDao.getSongsInSetOnce(setId) }
            val first = songs.firstOrNull { !it.completedInSet } ?: return@launch
            playerVm.clearQueue()
            playerVm.selectSong(first.song, getApplication(), isGigSet = true)
            playerVm.activeEndAction.value =
                withContext(Dispatchers.IO) { setDao.getEndAction(setId, first.song.id) ?: 0 }
            songs.filter { !it.completedInSet && it.song.id != first.song.id }
                .forEach { playerVm.addToQueueEnd(it.song) }
        }
    }
}
```

### 3b) Callback extrahieren (aus `armSetIfIdle`/`loadSetAsQueue`) + Auto-Übergang
Den bisher doppelten `playerVm.onSongCompleted = { ... }`-Block in eine private
Funktion ziehen und dort den Auto-Übergang einbauen:
```kotlin
private fun installSetCompletedCallback(playerVm: PlayerViewModel) {
    playerVm.onSongCompleted = { completedId ->
        viewModelScope.launch(Dispatchers.IO) {
            val activeSet = _activeSetId.value ?: return@launch
            setDao.markSongCompleted(activeSet, completedId, true)

            // Set fertig? → optional automatisch ins nächste Set
            val remaining = setDao.getSongsInSetOncePlain(activeSet)
                .count { !it.completedInSet }
            if (remaining == 0) {
                val gigId = _selectedGigId.value
                val gig = if (gigId != null) gigDao.getGigById(gigId) else null
                if (gig?.autoAdvanceSets == true) {
                    val sets = setDao.getSetsForGigOnce(gig.gigId)   // ggf. neue Once-Query
                    val idx = sets.indexOfFirst { it.setId == activeSet }
                    val next = sets.getOrNull(idx + 1)
                    if (next != null) withContext(Dispatchers.Main) {
                        switchToSet(gig.gigId, next.setId, playerVm)
                    }
                }
            }
            val newId = playerVm.currentSong.value?.id ?: return@launch
            playerVm.activeEndAction.value = setDao.getEndAction(activeSet, newId) ?: 0
        }
    }
}
```
`armSetIfIdle` und `loadSetAsQueue` rufen künftig `installSetCompletedCallback(playerVm)`
auf, statt den Block inline zu duplizieren.

> Falls `setDao.getSetsForGigOnce(gigId): List<SetEntity>` (suspend, ORDER BY position)
> noch nicht existiert: eine solche Query in `SetDao` ergänzen. `getSetsForGig` gibt
> aktuell nur einen `Flow` zurück.

### 3c) Auto-Übergang-Schalter + Reset-„von-vorn"
```kotlin
fun setAutoAdvance(gigId: Long, enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) { gigDao.updateAutoAdvance(gigId, enabled) }
}
```
(Der bestehende `resetCompletedForSet` deckt „von vorn" pro Set bereits ab.)

---

## Schritt 4 — UI (`ui/GigManagementScreen.kt`, `GigDetailView`)

`GigDetailView` bekommt zusätzlich die `GigEntity` (für `lastActiveSetId` +
`autoAdvanceSets`) übergeben. Prüfen, ob der Aufrufer weiter oben (`GigManagementScreen`)
das Gig-Objekt schon hat — falls nur die `sets` durchgereicht werden, das `gig` mit
durchreichen.

### 4a) Aktives Set bestimmen
```kotlin
val activeSetId by gigVm.activeSetId.collectAsState()
val currentSet = remember(sets, activeSetId, gig.lastActiveSetId) {
    sets.firstOrNull { it.setId == activeSetId }
        ?: sets.firstOrNull { it.setId == gig.lastActiveSetId }
        ?: sets.firstOrNull()
}
// Beim Öffnen zuletzt aktives Set armen (nur einmal):
LaunchedEffect(gig.gigId, sets) {
    val target = sets.firstOrNull { it.setId == gig.lastActiveSetId } ?: sets.firstOrNull()
    if (target != null && activeSetId == null)
        gigVm.switchToSet(gig.gigId, target.setId, playerVm)
}
```

### 4b) Griff oben (im bestehenden Header-`Row`, ersetzt die Sets-Liste)
- Statt „Set X von Y" reicht: **Set-Name + gespielt X/Y + `Icons.Filled.ArrowDropDown`** als Affordanz, das Ganze `clickable { showOverview = true }`.
- Kein Dauer-Panel. Der Rest der Fläche gehört der Songliste.

### 4c) Einzel-Set-Ansicht
- Den `else`-Zweig (Zeile ~370, `LazyColumn { items(sets){ SetCard } }`) ersetzen durch **eine einzige `SetCard(currentSet, …)`** (voller Platz).
- Den `sortSetsMode`-Zweig (Set-Reihenfolge per Drag) aus `GigDetailView` **entfernen** — die Set-Sortierung lebt jetzt in der Übersicht (Schritt 4d). `SetRowSortable` dorthin verschieben/wiederverwenden.

### 4d) `SetSwitcherSheet` (neu) — Übersicht + Verwaltung
Ein `ModalBottomSheet` (oder Vollbild-Dialog), geöffnet über `showOverview`:
- **Kopf:** „Sets" + Button `+ Neues Set` (ruft bestehendes `onCreate`).
- **Schalter:** `Switch` „Automatisch ins nächste Set" ↔ `gig.autoAdvanceSets`, `onCheckedChange = { gigVm.setAutoAdvance(gig.gigId, it) }`.
- **Liste aller Sets** (Fortschritt via `gigVm.setProgress(sets.map{it.setId})` in `LaunchedEffect(sets)` laden):
  - Pro Zeile: Set-Name, `"$completed/$total gespielt"`, aktives Set in `GigVolt` markiert.
  - Tap auf Zeile → `gigVm.switchToSet(gig.gigId, set.setId, playerVm); showOverview = false`.
  - Zeilen-Aktionen: **Umbenennen** (`renameTarget = set`), **Löschen** (bestehender Bestätigungsdialog `onDeleteSet`), **Drag-Handle** zum Sortieren (`SetRowSortable`-Muster aus dem alten `sortSetsMode`, persistiert über `gigVm.reorderSets`).
- **`isLocked` respektieren** (Gotcha 10): Umschalten selbst darf bei Lock erlaubt bleiben (Bühne!), aber Umbenennen/Löschen/Neu/Sortieren mit `enabled = !isLocked` gaten.

### 4e) `SetCard` entschlacken
- Aus dem `⋮`-DropdownMenu die set-weiten Einträge **Umbenennen** und **Löschen** entfernen (sind jetzt in der Übersicht).
- **Behalten** in der Einzel-Set-Ansicht: „Songs hinzufügen", „Completed zurücksetzen", der `editSongsMode`-Toggle (End-Aktion/Entfernen) und der `sortMode`-Toggle (Songs im Set umsortieren).
- Da `SetCard` nicht mehr in einer `LazyColumn` liegt, sondern allein die Fläche füllt: die innere Songliste kann/soll jetzt eine echte `LazyColumn` sein (löst nebenbei den zurückgestellten Lazy-TODO — siehe „Scroll-Performance"-Sprint). Höhe über `Modifier.weight(1f)` bzw. `fillMaxHeight` begrenzen, damit KEIN verschachtelter Infinite-Height-Crash wie im Revert vom 2026-07-25 auftritt.

---

## Edge Cases (bewusst behandeln)

- **Aktives Set gelöscht:** nach `onDeleteSet` fällt `currentSet` auf das erste
  verbleibende Set zurück (die `remember`-Kette in 4a deckt das ab). Ggf.
  `switchToSet` auf das neue erste Set nachziehen.
- **Set ist leer (0 Songs):** Griff zeigt „0/0", Einzel-Ansicht zeigt den bestehenden Empty-State.
- **Auto-Übergang + letztes Set fertig:** kein nächstes Set → nichts tun (bleibt stehen).
- **Umschalten während Pause (Song geladen, `isPlaying == false`):** gilt als „idle" → neues Set wird sofort gearmt (erwünscht).
- **`lastActiveSetId` zeigt auf ein inzwischen gelöschtes Set:** Fallback auf erstes Set (Kette in 4a).

---

## Test-Checkliste (nächste Session, am Gerät)

1. Gig mit 3 Sets, jeweils mehreren Songs. Griff oben zeigt Set 1 + „0/X".
2. Übersicht öffnen → Set 2 antippen → Ansicht zeigt sofort NUR Set 2, Griff aktualisiert.
3. Song aus Set 1 läuft → in Übersicht Set 2 antippen → laufender Song spielt zu Ende, DANN kommt Set 2 (keine Unterbrechung).
4. Gespielt-Zähler in der Übersicht stimmt pro Set.
5. Auto-Übergang AUS: Set 1 durchspielen → bleibt am Set-Ende stehen. Auto-Übergang AN: springt automatisch in Set 2.
6. App im Gig schließen (Set 2 aktiv, ein paar Songs gespielt) → neu öffnen → landet wieder in Set 2, Markierungen erhalten.
7. In der Übersicht: Set umbenennen, löschen, neu anlegen, Reihenfolge per Drag ändern.
8. `isLocked` (Live-Lock) an: Umschalten geht, Verwaltung ist gesperrt.

---

## Wichtige Hinweise für die Umsetzung

- **Kein lokaler Gradle-Build in der Sandbox** (Google-Maven 403) — nach dem Push
  **aktiv den CI-Status** via GitHub Actions prüfen (`mcp__github__actions_list` /
  `get_job_logs`), NICHT nur `apk-dist` fetchen (Lektion 2026-07-25).
- **Branch:** alles auf `main` (CLAUDE.md-Branch-Regel).
- **Reihenfolge einhalten:** erst Schritt 1–3 (kompiliert eigenständig), dann UI
  Schritt 4. So bleibt jeder Zwischenstand baubar.
- Nach Abschluss: CLAUDE.md-Sprint-Eintrag + Gotcha 1 (Room v18) aktualisieren,
  `.status.md` neu schreiben.
