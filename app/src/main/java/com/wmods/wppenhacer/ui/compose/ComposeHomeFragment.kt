package com.wmods.wppenhacer.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.wmods.wppenhacer.activities.MainActivity

class ComposeHomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val host = requireActivity() as MainActivity
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WpTheme(host.wpPrefs) {
                    HomeScreen(
                        lifecycle = viewLifecycleOwner.lifecycle,
                        onSearch = { host.openSearch() },
                        onAppearance = { host.openAppearance() },
                        onAbout = { host.openAbout() },
                        onTools = { host.showTools() },
                        onBattery = { host.requestBatteryOptimization() },
                        onFloatingBar = { host.openFloatingBar() },
                    )
                }
            }
        }
    }
}
