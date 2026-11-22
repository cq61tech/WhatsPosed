package com.wmods.wppenhacer.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.FloatingBarActivity
import com.wmods.wppenhacer.activities.ElasticMessagesActivity
import com.wmods.wppenhacer.activities.MaterialExpressiveActivity
import com.wmods.wppenhacer.activities.ThemesActivity
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment

class CustomizationFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.fragment_customization, rootKey)
        findPreference<Preference>("floating_bottom_bar_editor")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), FloatingBarActivity::class.java))
            true
        }
        findPreference<Preference>("elastic_messages_editor")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ElasticMessagesActivity::class.java))
            true
        }
        findPreference<Preference>("material_expressive_editor")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MaterialExpressiveActivity::class.java))
            true
        }
        findPreference<Preference>("themes_center")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ThemesActivity::class.java))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        setDisplayHomeAsUpEnabled(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activityIntent = activity?.intent
        val scrollToKey = activityIntent?.getStringExtra("scroll_to_preference")
        if (scrollToKey != null) {
            scrollToPreference(scrollToKey)
            activityIntent.removeExtra("scroll_to_preference")
        }
    }
}
