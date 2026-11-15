package com.wmods.wppenhacer.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.wmods.wppenhacer.ui.compose.ComposeHomeFragment
import com.wmods.wppenhacer.ui.fragments.CustomizationFragment
import com.wmods.wppenhacer.ui.fragments.GeneralFragment
import com.wmods.wppenhacer.ui.fragments.MediaFragment
import com.wmods.wppenhacer.ui.fragments.PrivacyFragment
import com.wmods.wppenhacer.ui.fragments.RecordingsFragment

class MainPagerAdapter(fragmentActivity: FragmentActivity, private val pageCount: Int) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = pageCount

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> ComposeHomeFragment()
        1 -> GeneralFragment()
        2 -> PrivacyFragment()
        3 -> MediaFragment()
        4 -> CustomizationFragment()
        else -> RecordingsFragment()
    }
}
