package com.empresa.autocleaner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lanza la actividad de configuración y luego se cierra.
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}