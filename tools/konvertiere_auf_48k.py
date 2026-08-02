#!/usr/bin/env python3
"""
Rechnet Backing-Track-WAVs auf 48 kHz / 24 Bit um.

Wozu: Im geplanten USB-Multitrack-Betrieb gibt das Mischpult den Takt vor — dort läuft
alles mit 48 kHz. Ältere Exporte in 44,1 kHz müssten sonst live umgerechnet werden,
genau an der zeitkritischsten Stelle. Einmal vorher umrechnen ist der sichere Weg.
Für den normalen Stereo-Betrieb der App ist das NICHT nötig, dort spielt Android
beide Formate ohne Zutun.

Bedienung (im Ordner mit den WAV-Dateien ausführen):

    python konvertiere_auf_48k.py                 # nur anzeigen, was zu tun wäre
    python konvertiere_auf_48k.py --start         # umrechnen
    python konvertiere_auf_48k.py --start --ersetzen
                                                  # Originale wegsichern und die neuen
                                                  # an ihre Stelle legen

Ohne --start passiert nichts, es wird nur gelistet. Originale werden nie überschrieben.

Die Arbeitsordner ("<Ordnername> 48k" und "<Ordnername> _originale") entstehen bewusst
NEBEN dem Track-Ordner, nicht darin: Die App wertet beim Import jeden Unterordner als
Multitrack-Song, ein Arbeitsordner im Track-Ordner würde als Geister-Song erscheinen.

Voraussetzung: ffmpeg muss installiert sein (bringt den Umrechner mit).
    Windows: winget install Gyan.FFmpeg
    macOS:   brew install ffmpeg
    Linux:   sudo apt install ffmpeg
"""

from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import sys
from pathlib import Path

ZIEL_RATE = 48000
ZIEL_CODEC = "pcm_s24le"  # 24 Bit, wie die neuen Studio-One-Exporte


def wav_format(pfad: Path) -> tuple[int, int, int] | None:
    """(Samplerate, Bits, Kanäle) aus dem WAV-Kopf. None, wenn es keine WAV ist."""
    try:
        with pfad.open("rb") as f:
            kopf = f.read(12)
            if len(kopf) < 12 or kopf[0:4] != b"RIFF" or kopf[8:12] != b"WAVE":
                return None
            # Chunks durchgehen, bis "fmt " kommt.
            for _ in range(64):
                chunk = f.read(8)
                if len(chunk) < 8:
                    return None
                kennung, groesse = chunk[0:4], struct.unpack("<I", chunk[4:8])[0]
                if kennung == b"fmt ":
                    daten = f.read(max(16, min(groesse, 64)))
                    if len(daten) < 16:
                        return None
                    kanaele = struct.unpack("<H", daten[2:4])[0]
                    rate = struct.unpack("<I", daten[4:8])[0]
                    bits = struct.unpack("<H", daten[14:16])[0]
                    return rate, bits, kanaele
                f.seek(groesse + (groesse % 2), 1)  # Chunks sind gerade gepolstert
    except OSError:
        return None
    return None


def ffmpeg_pfad() -> str | None:
    return shutil.which("ffmpeg")


def umrechnen(ffmpeg: str, quelle: Path, ziel: Path) -> tuple[bool, str]:
    """Ruft ffmpeg auf. Rückgabe (geklappt, Meldung)."""
    ziel.parent.mkdir(parents=True, exist_ok=True)
    befehl = [
        ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
        "-i", str(quelle),
        "-ar", str(ZIEL_RATE),
        "-c:a", ZIEL_CODEC,
        str(ziel),
    ]
    ergebnis = subprocess.run(befehl, capture_output=True, text=True)
    if ergebnis.returncode != 0:
        return False, ergebnis.stderr.strip().splitlines()[-1] if ergebnis.stderr else "ffmpeg-Fehler"
    return True, ""


def main() -> int:
    parser = argparse.ArgumentParser(description="WAVs auf 48 kHz / 24 Bit umrechnen")
    parser.add_argument("ordner", nargs="?", default=".", help="Ordner mit den WAVs (Standard: aktueller)")
    parser.add_argument("--start", action="store_true", help="wirklich umrechnen (sonst nur anzeigen)")
    parser.add_argument("--ersetzen", action="store_true",
                        help="Originale wegsichern und die neuen an ihre Stelle legen")
    args = parser.parse_args()

    wurzel = Path(args.ordner).resolve()
    if not wurzel.is_dir():
        print(f"Ordner nicht gefunden: {wurzel}")
        return 1

    # WICHTIG: beide Arbeitsordner liegen NEBEN dem Track-Ordner, nicht darin.
    # Die App wertet beim Import jeden Unterordner als Multitrack-Song mit den
    # enthaltenen Dateien als Spuren — ein "48k"- oder "_originale"-Ordner im
    # Track-Ordner würde also als Geister-Song mit 25 Spuren auftauchen.
    ausgabe_ordner = wurzel.parent / f"{wurzel.name} 48k"
    backup_ordner = wurzel.parent / f"{wurzel.name} _originale"

    # Unterordner werden mitgenommen (Multitrack-Songs liegen als Ordner vor),
    # die eigenen Arbeitsordner aber nicht.
    dateien = sorted(
        p for p in wurzel.rglob("*.wav")
        if ausgabe_ordner not in p.parents and backup_ordner not in p.parents
    )
    if not dateien:
        print(f"Keine WAV-Dateien in {wurzel}")
        return 0

    zu_tun: list[tuple[Path, int, int]] = []
    schon_gut = 0
    unlesbar: list[Path] = []

    for datei in dateien:
        format = wav_format(datei)
        if format is None:
            unlesbar.append(datei)
            continue
        rate, bits, _ = format
        if rate == ZIEL_RATE:
            schon_gut += 1
        else:
            zu_tun.append((datei, rate, bits))

    print(f"{len(dateien)} WAV-Dateien in {wurzel}")
    print(f"  bereits {ZIEL_RATE} Hz: {schon_gut}")
    print(f"  umzurechnen:            {len(zu_tun)}")
    if unlesbar:
        print(f"  nicht lesbar:           {len(unlesbar)}")
    print()

    for datei, rate, bits in zu_tun:
        print(f"  {datei.relative_to(wurzel)}  ({rate} Hz / {bits} bit)")
    for datei in unlesbar:
        print(f"  ! nicht lesbar: {datei.relative_to(wurzel)}")

    if not zu_tun:
        print("\nNichts zu tun — alles liegt schon in 48 kHz vor.")
        return 0

    if not args.start:
        print("\nNur angezeigt. Zum wirklichen Umrechnen nochmal mit  --start  aufrufen.")
        return 0

    ffmpeg = ffmpeg_pfad()
    if ffmpeg is None:
        print("\nffmpeg wurde nicht gefunden — es macht die eigentliche Umrechnung.")
        print("  Windows: winget install Gyan.FFmpeg")
        print("  macOS:   brew install ffmpeg")
        print("  Linux:   sudo apt install ffmpeg")
        return 1

    print(f"\nRechne {len(zu_tun)} Dateien um …\n")
    fertig, fehler = 0, []
    for nummer, (datei, _, _) in enumerate(zu_tun, start=1):
        relativ = datei.relative_to(wurzel)
        ziel = ausgabe_ordner / relativ
        print(f"  [{nummer}/{len(zu_tun)}] {relativ}", flush=True)
        geklappt, meldung = umrechnen(ffmpeg, datei, ziel)
        if not geklappt:
            fehler.append((relativ, meldung))
            continue

        if args.ersetzen:
            # Original zuerst wegsichern, dann die neue Datei an seinen Platz —
            # in dieser Reihenfolge geht bei einem Abbruch nichts verloren.
            sicherung = backup_ordner / relativ
            sicherung.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(datei), str(sicherung))
            shutil.move(str(ziel), str(datei))
        fertig += 1

    if args.ersetzen:
        # Leeres Gerüst des Ausgabeordners aufräumen.
        for ordner in sorted(ausgabe_ordner.rglob("*"), reverse=True):
            if ordner.is_dir() and not any(ordner.iterdir()):
                ordner.rmdir()
        if ausgabe_ordner.is_dir() and not any(ausgabe_ordner.iterdir()):
            ausgabe_ordner.rmdir()

    print(f"\nFertig: {fertig} umgerechnet.")
    if args.ersetzen:
        print(f"Originale liegen jetzt in: {backup_ordner}")
        print("Erst löschen, wenn du die Dateien in der App gegengehört hast.")
    else:
        print(f"Ergebnisse liegen in: {ausgabe_ordner}")
        print("Die Originale wurden nicht angefasst.")
    if fehler:
        print(f"\n{len(fehler)} Fehler:")
        for relativ, meldung in fehler:
            print(f"  {relativ}: {meldung}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
