package com.wmods.wppenhacer.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.ui.compose.AppearanceScreen
import com.wmods.wppenhacer.ui.compose.WpPrefs
import com.wmods.wppenhacer.ui.compose.WpTheme

class AppearanceActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        App.changeLanguage(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = WpPrefs(applicationContext)
        setContent {
            WpTheme(prefs) {
                Surface(androidx.compose.ui.Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppearanceScreen(prefs) { finish() }
                }
            }
        }
    }
}
