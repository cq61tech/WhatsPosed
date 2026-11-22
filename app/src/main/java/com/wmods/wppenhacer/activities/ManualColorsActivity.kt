package com.wmods.wppenhacer.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.ui.fragments.ManualColorsFragment

class ManualColorsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        App.changeLanguage(this)
        super.onCreate(savedInstanceState)
        val containerId = View.generateViewId()
        val container = FrameLayout(this).apply {
            id = containerId
            fitsSystemWindows = true
        }
        setContentView(container)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.theme_manual_colors_title)
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(containerId, ManualColorsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
