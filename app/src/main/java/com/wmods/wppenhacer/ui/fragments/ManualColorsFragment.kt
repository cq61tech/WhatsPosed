package com.wmods.wppenhacer.ui.fragments

import android.os.Bundle
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment

class ManualColorsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.fragment_manual_colors, rootKey)
    }
}
