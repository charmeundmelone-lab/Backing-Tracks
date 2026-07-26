# Umsetzungs-Plan: PDF-zu-Lyrics-Import (Phase 1, Weg A)

> **Für die Umsetzungs-Session gedacht.** Schritt für Schritt abarbeiten, Reihenfolge
> einhalten, Code-Blöcke 1:1 übernehmen. Kein eigenes Umdenken nötig — alle
> Entscheidungen sind schon getroffen (siehe CLAUDE.md → „PDF-zu-Lyrics → ENTSCHEIDUNG").
> Ziel: PDF wählen → Akkorde raus → Text 1:1 (mit den PDF-Umbrüchen) ins Lyrics-Feld.
> **Kein Auto-Save**, kein OCR, keine eigene Umbruch-Regel in Phase 1.

## Vorbedingungen (nicht überspringen)
- [ ] `git branch` zeigt `* main`. Falls nicht: `git checkout main`.
- [ ] Letzter CI-Build auf `main` ist grün (`mcp__github__actions_list`, `list_workflow_runs`, branch `main`).
- [ ] Arbeitsverzeichnis sauber (`git status`).

---

## Schritt 1 — Dependency hinzufügen

**Datei:** `app/build.gradle.kts`
**Stelle:** im `dependencies { … }`-Block (aktuell Zeile 72–90), z. B. direkt **nach** der Zeile
`implementation(libs.androidx.documentfile)`.

**Einfügen:**
```kotlin
    // PDF-Textlayer-Extraktion für den Lyrics-Import (Phase 1). Reiner Text, kein OCR.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
```

> Hinweis: bewusst als String-Notation (nicht über den Versionskatalog `libs.*`), damit
> keine zusätzliche Eintragung in `gradle/libs.versions.toml` nötig ist.

---

## Schritt 2 — PdfBox einmalig initialisieren

PdfBox-Android braucht vor der ersten Nutzung `PDFBoxResourceLoader.init(...)`, sonst
kann bei manchen PDFs das Font-Laden crashen. Bester Ort: die Application-Klasse.

**Datei:** `app/src/main/java/de/livegigplayer/pro/LiveGigPlayerApp.kt`

**Datei komplett ersetzen durch:**
```kotlin
package de.livegigplayer.pro

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import de.livegigplayer.pro.data.AppDatabase

class LiveGigPlayerApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
```

---

## Schritt 3 — Akkord-Filter als neue Hilfsklasse (Kotlin-Port)

**Neue Datei anlegen:** `app/src/main/java/de/livegigplayer/pro/audio/PdfLyricsImporter.kt`
(Package `…audio` bewusst gewählt — dort lebt schon `FolderImporter`, gleiche „Import"-Domäne.)

**Kompletter Inhalt:**
```kotlin
package de.livegigplayer.pro.audio

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Phase 1: extrahiert den Textlayer eines PDF und entfernt reine Akkordzeilen.
 * Umbrüche bleiben 1:1 wie im PDF (keine eigene Umbruch-Regel). Kein OCR.
 */
object PdfLyricsImporter {

    // Ein Token gilt als Akkord: C, Am, G7, Dsus4, F#m, Cmaj7, C/E …
    private val CHORD_REGEX =
        Regex("^[A-G](#|b)?(m|maj|min|sus|dim|aug|add)?\\d{0,2}(/[A-G](#|b)?)?$")

    // Reine Takt-/Trenn-Tokens zählen ebenfalls als „Akkord-Token".
    private val BAR_TOKENS = setOf("|", ":", "/", "||", "|:", ":|")

    // [Verse], [Chorus] … bleiben IMMER erhalten (Songstruktur, kein Akkord).
    private val SECTION_REGEX = Regex("^\\[.*\\]$")

    /** Liest den PDF-Textlayer und gibt den gefilterten Lyrics-Text zurück. */
    fun importLyricsFromPdf(context: Context, uri: Uri): String {
        val rawText = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                PDFTextStripper().getText(doc)
            }
        } ?: return ""
        return filterChordLines(rawText)
    }

    /** Entfernt reine Akkordzeilen, behält Text-, Leer- und [Section]-Zeilen. */
    fun filterChordLines(text: String): String =
        text.lineSequence()
            .filterNot { isChordLine(it) }
            .joinToString("\n")
            .trim()

    private fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false                // Leerzeilen behalten
        if (SECTION_REGEX.matches(trimmed)) return false    // [Section] behalten
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        val chordCount = tokens.count { it in BAR_TOKENS || CHORD_REGEX.matches(it) }
        return chordCount.toDouble() / tokens.size >= 0.8   // ≥80 % → Akkordzeile
    }
}
```

---

## Schritt 4 — Import-Button im Song-Editor

**Datei:** `app/src/main/java/de/livegigplayer/pro/ui/MainScreen.kt`
Alle Änderungen betreffen die Funktion `SongEditorSheet` (ab Zeile 1095).

### 4a) Imports sicherstellen
Oben im Import-Block (Zeilen 1–150) prüfen und **nur hinzufügen, was noch fehlt**
(Duplikate vermeiden — vieles ist schon da):
```kotlin
import android.net.Uri
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import de.livegigplayer.pro.audio.PdfLyricsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```
> Bereits vorhanden (nicht doppelt anlegen): `rememberLauncherForActivityResult` (Z. 12),
> `ActivityResultContracts` (Z. 13), `Toast` (Z. 11), `Button` (Z. 78),
> `CircularProgressIndicator` (Z. 80), `Icon`, `Article`-Icon (Z. 55), `width` (Z. 50).

### 4b) State + PDF-Picker anlegen
**Stelle:** direkt **nach** Zeile 1109 (`var lyrics by remember(song.id) { mutableStateOf(song.lyrics) }`).

**Einfügen:**
```kotlin
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var importing by remember { mutableStateOf(false) }
        var pendingPdfText by remember { mutableStateOf<String?>(null) }

        val pdfPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                importing = true
                scope.launch {
                    val extracted = withContext(Dispatchers.IO) {
                        runCatching { PdfLyricsImporter.importLyricsFromPdf(context, uri) }
                            .getOrDefault("")
                    }
                    importing = false
                    when {
                        extracted.isBlank() ->
                            Toast.makeText(context, "Kein Text im PDF gefunden", Toast.LENGTH_LONG).show()
                        lyrics.isBlank() -> lyrics = extracted          // Feld leer → direkt einsetzen
                        else -> pendingPdfText = extracted              // Feld belegt → nachfragen
                    }
                }
            }
        }
```

### 4c) Button unter dem Lyrics-Feld
**Stelle:** direkt **nach** dem `OutlinedTextField`-Block des Lyrics-Feldes
(nach der schließenden `)` in Zeile 1172), **vor** `Spacer(modifier = Modifier.height(16.dp))` (Z. 1173).

**Einfügen:**
```kotlin
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BgTrack)
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Volt, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Article, contentDescription = null, tint = Volt, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aus PDF importieren (Akkorde entfernt)", color = Volt, fontSize = 13.sp)
            }
        }
```

### 4d) Dialog für „Feld ist nicht leer"
**Stelle:** am **Ende** der Funktion `SongEditorSheet`, direkt **vor** der letzten
schließenden Klammer `}` (aktuell Zeile 1206, nach dem Button-`Row`-Block).

**Einfügen:**
```kotlin
        if (pendingPdfText != null) {
            AlertDialog(
                onDismissRequest = { pendingPdfText = null },
                containerColor = BgTrack,
                title = { Text("Lyrics-Feld ist nicht leer", color = White, fontSize = 16.sp) },
                text = { Text("Wie soll der importierte Text eingefügt werden?", color = Gray, fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        lyrics = pendingPdfText!!
                        pendingPdfText = null
                    }) { Text("Ersetzen", color = Volt) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            lyrics = lyrics.trimEnd() + "\n\n" + pendingPdfText!!
                            pendingPdfText = null
                        }) { Text("Anhängen", color = Volt) }
                        TextButton(onClick = { pendingPdfText = null }) { Text("Abbrechen", color = Gray) }
                    }
                }
            )
        }
```

> **Kein Auto-Save:** Der Import schreibt nur in den lokalen `lyrics`-State. Erst der
> vorhandene „Speichern"-Button (Z. 1200, `onSave(...)`) persistiert. So gewollt.

---

## Schritt 5 — Statisch gegenlesen (kein lokaler Build möglich)
In dieser Sandbox ist **kein Gradle-Build** möglich (Google-Maven 403). Vor dem Commit
darum von Hand prüfen:
- [ ] Alle in 4a genannten Imports vorhanden, **keine doppelt**.
- [ ] Klammerbalance in `SongEditorSheet` stimmt (der neue Dialog steht **innerhalb** der Funktion).
- [ ] `BgTrack`, `Volt`, `White`, `Gray`, `ButtonDefaults` werden in der Datei bereits genutzt (sind vorhanden).
- [ ] Package der neuen Datei = `de.livegigplayer.pro.audio`.
- [ ] `PDFBoxResourceLoader`-Import in `LiveGigPlayerApp.kt` korrekt geschrieben.

---

## Schritt 6 — Commit, Push, CI
```bash
git add app/build.gradle.kts \
        app/src/main/java/de/livegigplayer/pro/LiveGigPlayerApp.kt \
        app/src/main/java/de/livegigplayer/pro/audio/PdfLyricsImporter.kt \
        app/src/main/java/de/livegigplayer/pro/ui/MainScreen.kt
git commit -m "PDF-zu-Lyrics-Import Phase 1: PdfBox-Extraktion + Akkord-Filter im Song-Editor"
git push -u origin main
```
- [ ] **CI aktiv prüfen** (`mcp__github__actions_list` → `list_workflow_runs`, branch `main`):
      neuer Build **grün**? NICHT blind `apk-dist` fetchen (Lektion 2026-07-25 — sonst wird ein
      alter/kaputter Build verschickt).
- [ ] Erst bei grünem Build die APK an den User geben.

---

## Schritt 7 — Live-Test (durch den User)
1. Song-Editor öffnen → „Aus PDF importieren" → ein echtes Ultimate-Guitar-PDF wählen.
2. Prüfen: Akkordzeilen weg, Textzeilen + `[Section]`-Labels erhalten, Umbrüche plausibel.
3. Speichern → Teleprompter öffnen → sieht der Text gut aus?

**Erwartetes Grenzverhalten:** Nur PDFs **mit Textlayer** funktionieren (UG-Export = ja).
Reine Bild-/Scan-PDFs liefern leeren Text → Toast „Kein Text im PDF gefunden". OCR ist
bewusst **nicht** Teil von Phase 1.

**Falls die Umbrüche im echten Song schlecht aussehen:** NICHT sofort eine eigene
Umbruch-Regel bauen. Stattdessen ein reales Beispiel (PDF-Input → gewünschter Output)
sammeln und daraus Weg B (Phase 2) ableiten — so in CLAUDE.md entschieden.
