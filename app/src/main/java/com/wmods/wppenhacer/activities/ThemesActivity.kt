package com.wmods.wppenhacer.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.ui.compose.ThemesPrefs
import com.wmods.wppenhacer.ui.compose.ThemesScreen
import com.wmods.wppenhacer.ui.compose.WpPrefs
import com.wmods.wppenhacer.ui.compose.WpTheme

class ThemesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        App.changeLanguage(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val theme = WpPrefs(applicationContext)
        val prefs = ThemesPrefs(applicationContext)
        setContent {
            WpTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ThemesScreen(prefs) { finish() }
                }
            }
        }
    }
}
