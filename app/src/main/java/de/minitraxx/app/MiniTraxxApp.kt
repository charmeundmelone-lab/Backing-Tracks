package de.minitraxx.app

import android.app.Application
import de.minitraxx.app.audio.NativeEngine

class MiniTraxxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Engine früh initialisieren, damit der erste Play-Befehl ohne Verzögerung kommt.
        NativeEngine.start()
    }
}
