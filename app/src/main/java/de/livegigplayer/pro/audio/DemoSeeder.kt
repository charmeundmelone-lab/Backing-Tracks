package de.livegigplayer.pro.audio

import android.content.Context
import de.livegigplayer.pro.data.Song
import de.livegigplayer.pro.data.SongDao
import java.io.File

object DemoSeeder {

    suspend fun seedIfNeeded(context: Context, dao: SongDao) {
        if (dao.countDemoSongs() > 0) return

        val root = File(context.filesDir, "demo").also { it.mkdirs() }
        seedHotelCalifornia(File(root, "hotel_california").also { it.mkdirs() }, dao)
        seedSultansOfSwing(File(root, "sultans_of_swing").also { it.mkdirs() }, dao)
        seedComfortablyNumb(File(root, "comfortably_numb").also { it.mkdirs() }, dao)
    }

    private suspend fun seedHotelCalifornia(dir: File, dao: SongDao) {
        // Alle 6 Spuren vorhanden — drums/bass/keys/vocals links (PA), click/cue rechts (In-Ear)
        WavSynth.writeLeftSine(File(dir, "drums.wav"),  100f)
        WavSynth.writeLeftSine(File(dir, "bass.wav"),   110f)
        WavSynth.writeLeftSine(File(dir, "keys.wav"),   440f)
        WavSynth.writeLeftSine(File(dir, "vocals.wav"), 330f)
        WavSynth.writeClickRight(File(dir, "click.wav"), bpm = 147)
        WavSynth.writeRightSine(File(dir, "cue.wav"),   880f)
        dao.insert(Song(
            title = "Hotel California", bpm = 147, timeSignature = "4/4",
            playlistId = 1, audioFilePath = dir.absolutePath, duration = "06:31"
        ))
    }

    private suspend fun seedSultansOfSwing(dir: File, dao: SongDao) {
        // Legacy-Modus: einzelne Stereo-WAV
        val wav = File(dir, "mix.wav")
        WavSynth.writeStereoMix(wav)
        dao.insert(Song(
            title = "Sultans of Swing", bpm = 149, timeSignature = "4/4",
            playlistId = 1, audioFilePath = wav.absolutePath, duration = "04:03"
        ))
    }

    private suspend fun seedComfortablyNumb(dir: File, dao: SongDao) {
        // Multitrack ohne vocals.wav → Vocals-Fader ausgegraut im Mixer
        WavSynth.writeLeftSine(File(dir, "drums.wav"), 100f)
        WavSynth.writeLeftSine(File(dir, "bass.wav"),   82f)
        WavSynth.writeLeftSine(File(dir, "keys.wav"),  523f)
        // vocals.wav absichtlich nicht erzeugen
        WavSynth.writeClickRight(File(dir, "click.wav"), bpm = 63)
        WavSynth.writeRightSine(File(dir, "cue.wav"),   660f)
        dao.insert(Song(
            title = "Comfortably Numb", bpm = 63, timeSignature = "4/4",
            playlistId = 1, audioFilePath = dir.absolutePath, duration = "06:22"
        ))
    }
}
