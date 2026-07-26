package app.calio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.calio.shared.CalioApp

/**
 * The only activity in the application.
 *
 * Everything the user sees is Compose from the shared module, so there is nothing for a second
 * activity to do, and navigation never has to cross an activity boundary.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CalioApplication).container
        setContent { CalioApp(container) }
    }
}
