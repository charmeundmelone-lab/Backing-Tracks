package de.minitraxx.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** Seiten tauschen: Main rechts, Cue links. */
    val swapSides: Boolean = false,
    /** Master-Gain Bus MAIN (linear). */
    val mainGain: Float = 1f,
    /** Master-Gain Bus CUE (linear). */
    val cueGain: Float = 1f,
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            swapSides = prefs.getBoolean("swapSides", false),
            mainGain = prefs.getFloat("mainGain", 1f),
            cueGain = prefs.getFloat("cueGain", 1f),
        )
    )
    val settings: StateFlow<AppSettings> = _settings

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit {
            putBoolean("swapSides", next.swapSides)
            putFloat("mainGain", next.mainGain)
            putFloat("cueGain", next.cueGain)
        }
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
