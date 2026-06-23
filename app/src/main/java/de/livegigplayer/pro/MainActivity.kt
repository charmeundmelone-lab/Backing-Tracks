package de.livegigplayer.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.livegigplayer.pro.ui.MainScreen
import de.livegigplayer.pro.ui.theme.LiveGigPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveGigPlayerTheme {
                MainScreen()
            }
        }
    }
}
