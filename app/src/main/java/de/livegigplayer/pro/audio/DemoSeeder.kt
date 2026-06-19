package de.livegigplayer.pro.audio

import android.content.Context
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.SongDao
import java.io.File

object DemoSeeder {

    suspend fun seedIfNeeded(context: Context, dao: SongDao) {
        val root = File(context.filesDir, "demo").also { it.mkdirs() }

        // WAV-Dateien immer neu generieren (beat-aligned, idempotent)
        val hotelDir  = File(root, "hotel_california").also { it.mkdirs() }
        val sultanDir = File(root, "sultans_of_swing").also { it.mkdirs() }
        val numbDir   = File(root, "comfortably_numb").also { it.mkdirs() }

        // Loop-Länge = exakt 8 Beats → kein Stottern am Loop-Punkt
        val hotelN   = WavSynth.beatsToSamples(bpm = 147, beats = 8)   // ≈ 3.27 s
        val sultansN = WavSynth.beatsToSamples(bpm = 149, beats = 8)   // ≈ 3.22 s
        val numbN    = WavSynth.beatsToSamples(bpm =  63, beats = 4)   // ≈ 3.81 s

        generateHotelWavs(hotelDir,  hotelN)
        generateSultansWavs(sultanDir, sultansN)
        generateNumbWavs(numbDir,    numbN)

        // DB-Einträge nur beim ersten Mal anlegen
        if (dao.countDemoSongs() == 0) {
            dao.insert(Song(title = "Hotel California", bpm = 147, timeSignature = "4/4",
                playlistId = 1, audioFilePath = hotelDir.absolutePath, duration = "06:31"))
            dao.insert(Song(title = "Sultans of Swing", bpm = 149, timeSignature = "4/4",
                playlistId = 1, audioFilePath = File(sultanDir, "mix.wav").absolutePath, duration = "04:03"))
            dao.insert(Song(title = "Comfortably Numb", bpm = 63, timeSignature = "4/4",
                playlistId = 1, audioFilePath = numbDir.absolutePath, duration = "06:22"))
        }
    }

    private fun generateHotelWavs(dir: File, n: Int) {
        WavSynth.writeLeftSine(File(dir, "drums.wav"),  100f, n)
        WavSynth.writeLeftSine(File(dir, "bass.wav"),   110f, n)
        WavSynth.writeLeftSine(File(dir, "keys.wav"),   440f, n)
        WavSynth.writeLeftSine(File(dir, "vocals.wav"), 330f, n)
        WavSynth.writeClickRight(File(dir, "click.wav"), bpm = 147, numSamples = n)
        WavSynth.writeRightSine(File(dir, "cue.wav"),   880f, n)
    }

    private fun generateSultansWavs(dir: File, n: Int) {
        WavSynth.writeStereoMix(File(dir, "mix.wav"), n)
    }

    private fun generateNumbWavs(dir: File, n: Int) {
        WavSynth.writeLeftSine(File(dir, "drums.wav"), 100f, n)
        WavSynth.writeLeftSine(File(dir, "bass.wav"),   82f, n)
        WavSynth.writeLeftSine(File(dir, "keys.wav"),  523f, n)
        // vocals.wav absichtlich nicht erzeugen → Fader ausgegraut
        WavSynth.writeClickRight(File(dir, "click.wav"), bpm = 63, numSamples = n)
        WavSynth.writeRightSine(File(dir, "cue.wav"),   660f, n)
    }
}
