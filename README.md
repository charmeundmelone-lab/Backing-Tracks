# MiniTraxx

Reduzierte Android-Alternative zu StageTraxx 4 für Live-Backing-Tracks:
**max. 4 synchrone Instrument-Spuren** plus **strikt getrennter Click/Cue-Kanal**.

## Routing-Konzept

```
Spur 1–4 (Stems) ──► Bus MAIN ──► mono summiert ──► LINKS   ─┐
                                                              ├─► USB-C-Y-Splitter
Click/Cue-Datei  ──► Bus CUE  ──► mono summiert ──► RECHTS  ─┘
```

- Alle Instrument-Spuren liegen ausschließlich auf der **linken** Seite (→ Pult/PA).
- Click + Cue-Ansagen liegen ausschließlich auf der **rechten** Seite (→ In-Ear).
- Kein Signalweg zwischen den Bussen; Tausch der Seiten in den Einstellungen möglich.

## Architektur

| Schicht | Technik |
|---|---|
| Audio-Engine | C++ / [Oboe](https://github.com/google/oboe) (AAudio, LowLatency), ein Stereo-Stream als Master-Clock, alle Stems im selben Callback gemischt → sample-genaue Synchronität |
| Stem-Format | Kanonisch WAV · PCM16 · mono · 48 kHz, per `mmap` eingeblendet (keine Allocations/Reads im RT-Pfad) |
| Import | Kotlin `MediaExtractor`/`MediaCodec`: beliebige Formate (WAV/MP3/FLAC/AAC/OGG) → Downmix + lineares Resampling ins Kanonformat |
| App | Kotlin · Jetpack Compose · Room · Foreground-Service mit Wake-Lock |

## Live-Verhalten

- **Songende:** Stopp + nächster Song der Setlist wird geladen („armed“), Start erst auf Play.
- **Safe-Mode:** Live-Screen startet gesperrt; nur Play/Pause bleibt bedienbar.
  Entsperren per Langdruck auf das Schloss, Zurück-Geste ist im gesperrten Zustand blockiert.
- **Anzeige:** großer Restzeit-Countdown (Farbwechsel < 15 s), Titel des nächsten Songs.
- **Robustheit:** Audiofokus-Verlust oder Geräte-Disconnect → sofort Pause + Warnung;
  Display bleibt an, Foreground-Service hält den Prozess am Leben.

## Workflow

1. **Songs** anlegen, pro Song bis zu 4 Instrument-Stems + 1 Click/Cue-Datei importieren
   (Gain pro Stem im Editor — Set-and-forget, kein Live-Mixer).
2. **Setlisten** per Drag-and-Drop zusammenstellen.
3. **Live starten** — Screen ist gesperrt, Countdown läuft, nächster Song wartet.

## Build

```bash
./gradlew assembleDebug
```

Benötigt Android SDK (compileSdk 35), NDK und CMake 3.22.1 — Android Studio
installiert beides automatisch. CI baut bei jedem Push (`.github/workflows/android-build.yml`)
und lädt die Debug-APK als Artifact hoch.
