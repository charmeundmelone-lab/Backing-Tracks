package de.livegigplayer.pro

import android.app.Application
import de.livegigplayer.pro.audio.DemoSeeder
import de.livegigplayer.pro.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiveGigPlayerApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DemoSeeder.seedIfNeeded(this@LiveGigPlayerApp, database.songDao())
        }
    }
}
