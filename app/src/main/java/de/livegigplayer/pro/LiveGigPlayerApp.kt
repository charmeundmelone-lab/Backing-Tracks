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
