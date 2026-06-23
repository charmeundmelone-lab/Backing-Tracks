package de.livegigplayer.pro

import android.app.Application
import de.livegigplayer.pro.data.AppDatabase

class LiveGigPlayerApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
}
