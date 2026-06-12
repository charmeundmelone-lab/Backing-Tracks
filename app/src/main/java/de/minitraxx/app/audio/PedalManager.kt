package de.minitraxx.app.audio

import android.content.Context
import android.view.KeyEvent
import de.minitraxx.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Aktionen, die ein Pedal-Taster auslösen kann. */
enum class PedalAction { PLAY_PAUSE, NEXT }

/**
 * Bluetooth-Fußschalter (Page-Turner & Co.): Die Geräte melden sich als
 * HID-Tastatur und senden normale Tastendrücke. Hier werden sie auf
 * Player-Aktionen gemappt — inklusive Anlern-Modus, damit jedes Modell
 * funktioniert. Das Pedal ist bewusst auch bei gesperrtem Live-Screen aktiv.
 */
class PedalManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore.get(appContext)

    /** Ist gesetzt, wird der nächste Tastendruck dieser Aktion zugewiesen. */
    private val _learnTarget = MutableStateFlow<PedalAction?>(null)
    val learnTarget: StateFlow<PedalAction?> = _learnTarget

    /** Letzter erkannter (verarbeiteter) KeyCode — für Feedback im UI. */
    private val _lastKey = MutableStateFlow(-1)
    val lastKey: StateFlow<Int> = _lastKey

    fun startLearning(action: PedalAction) {
        _learnTarget.value = action
    }

    fun cancelLearning() {
        _learnTarget.value = null
    }

    /**
     * Von der Activity für jedes KeyDown aufgerufen.
     * @return true, wenn das Event verbraucht wurde (auch Volume-Tasten,
     *         damit das Pedal nicht nebenbei die Lautstärke verstellt).
     */
    fun onKeyDown(keyCode: Int): Boolean {
        if (isIgnoredKey(keyCode)) return false

        val learning = _learnTarget.value
        if (learning != null) {
            settingsStore.update {
                when (learning) {
                    PedalAction.PLAY_PAUSE -> it.copy(pedalPlayKey = keyCode)
                    PedalAction.NEXT -> it.copy(pedalNextKey = keyCode)
                }
            }
            _lastKey.value = keyCode
            _learnTarget.value = null
            return true
        }

        val action = actionFor(keyCode) ?: return false
        val controller = PlaybackController.get(appContext)
        if (!controller.state.value.hasSession) return false
        _lastKey.value = keyCode
        when (action) {
            PedalAction.PLAY_PAUSE -> controller.playPause()
            PedalAction.NEXT -> controller.next()
        }
        return true
    }

    /** KeyUp der gemappten Tasten ebenfalls schlucken. */
    fun consumesKey(keyCode: Int): Boolean =
        !isIgnoredKey(keyCode) && (actionFor(keyCode) != null || _learnTarget.value != null)

    private fun actionFor(keyCode: Int): PedalAction? {
        val s = settingsStore.settings.value
        return when (keyCode) {
            s.pedalPlayKey -> PedalAction.PLAY_PAUSE
            s.pedalNextKey -> PedalAction.NEXT
            // Medien-Tasten wirken immer als Play/Pause (BT-Media-Remotes, Headsets).
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> PedalAction.PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_NEXT -> PedalAction.NEXT
            else -> null
        }
    }

    /** Tasten, die nie als Pedal interpretiert werden dürfen. */
    private fun isIgnoredKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_MENU,
        -> true
        else -> false
    }

    companion object {
        @Volatile
        private var instance: PedalManager? = null

        fun get(context: Context): PedalManager =
            instance ?: synchronized(this) {
                instance ?: PedalManager(context.applicationContext).also { instance = it }
            }

        /** Lesbarer Name eines KeyCodes fürs UI, z. B. "PAGE_DOWN". */
        fun keyName(keyCode: Int): String =
            if (keyCode < 0) "—"
            else KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
}
