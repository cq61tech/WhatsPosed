package com.wmods.wppenhacer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.adapter.MainPagerAdapter
import com.wmods.wppenhacer.databinding.ActivityMainBinding
import com.wmods.wppenhacer.ui.compose.WpDock
import com.wmods.wppenhacer.ui.compose.WpPrefs
import com.wmods.wppenhacer.ui.compose.WpTheme
import com.wmods.wppenhacer.ui.fragments.GeneralFragment
import com.wmods.wppenhacer.ui.fragments.HomeFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment
import com.wmods.wppenhacer.utils.FilePicker
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val batteryPermissionHelper = BatteryPermissionHelper.getInstance()
    private var pendingScrollToPreference: String? = null
    private var pendingScrollToFragment = -1
    private var pendingParentKey: String? = null
    private val currentPage = mutableIntStateOf(0)
    private var topInset = 0
    private var dockHeight = 0
    private var colorSnapshot = ""
    private var toolsBack: OnBackPressedCallback? = null

    val wpPrefs: WpPrefs by lazy { WpPrefs(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        WpPrefs.migrate(this)
        App.changeLanguage(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val pageCount = if (prefs.getBoolean("call_recording_enable", false)) 6 else 5
        colorSnapshot = wpPrefs.snapshot()

        binding.viewPager.adapter = MainPagerAdapter(this, pageCount)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage.intValue = position
                val scrollKey = pendingScrollToPreference
                if (pendingScrollToFragment == position && scrollKey != null) {
                    val parentKey = pendingParentKey
                    pendingScrollToPreference = null
                    pendingScrollToFragment = -1
                    pendingParentKey = null
                    binding.viewPager.postDelayed({
                        scrollToPreferenceInCurrentFragment(scrollKey, parentKey)
                    }, 300)
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = bars.top
            applyPagerPadding()
            binding.legacyContainer.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        binding.dock.setContent {
            WpTheme(wpPrefs) {
                WpDock(
                    selected = currentPage.intValue,
                    count = pageCount,
                    prefs = wpPrefs,
                    onSelect = { binding.viewPager.setCurrentItem(it, false) },
                    onHeight = {
                        dockHeight = it
                        applyPagerPadding()
                    },
                )
            }
        }

        binding.viewPager.setCurrentItem(0, false)
        createMainDir()
        FilePicker.registerFilePicker(this)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        wpPrefs.reload()
        val snap = wpPrefs.snapshot()
        if (colorSnapshot.isNotEmpty() && snap != colorSnapshot) {
            colorSnapshot = snap
            recreate()
        }
    }

    private fun applyPagerPadding() {
        binding.pagerHost.setPadding(0, topInset, 0, dockHeight)
    }

    private fun createMainDir() {
        val nomedia = File(App.waEnhancerFolder, ".nomedia")
        if (nomedia.exists()) nomedia.delete()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        val fragmentPosition = intent.getIntExtra("navigate_to_fragment", -1)
        val preferenceKey = intent.getStringExtra("scroll_to_preference")
        val parentKey = intent.getStringExtra("parent_preference")

        if (fragmentPosition >= 0 && preferenceKey != null) {
            pendingScrollToPreference = preferenceKey
            pendingScrollToFragment = fragmentPosition
            pendingParentKey = parentKey
            binding.viewPager.setCurrentItem(fragmentPosition, false)
            intent.removeExtra("navigate_to_fragment")
            intent.removeExtra("scroll_to_preference")
            intent.removeExtra("parent_preference")
        } else if (fragmentPosition >= 0) {
            binding.viewPager.setCurrentItem(fragmentPosition, true)
        }
    }

    private fun scrollToPreferenceInCurrentFragment(preferenceKey: String, parentKey: String?) {
        val currentItem = binding.viewPager.currentItem
        val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") ?: return
        if (fragment is GeneralFragment) {
            if (!parentKey.isNullOrEmpty()) {
                navigateToSubFragmentAndScroll(fragment, parentKey, preferenceKey)
            } else {
                scrollInChildFragment(fragment, preferenceKey)
            }
        } else if (fragment is BasePreferenceFragment) {
            fragment.scrollToPreference(preferenceKey)
        }
    }

    private fun navigateToSubFragmentAndScroll(
        parentFragment: Fragment,
        parentKey: String,
        childPreferenceKey: String,
    ) {
        val subFragment = when (parentKey) {
            "general_home" -> GeneralFragment.HomeGeneralPreference()
            "homescreen" -> GeneralFragment.HomeScreenGeneralPreference()
            "conversation" -> GeneralFragment.ConversationGeneralPreference()
            else -> null
        } ?: return
        val parentView = parentFragment.view ?: return
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.frag_container, subFragment)
            .commitNow()
        parentView.postDelayed({
            (subFragment as? BasePreferenceFragment)?.scrollToPreference(childPreferenceKey)
        }, 400)
    }

    private fun scrollInChildFragment(parentFragment: Fragment, preferenceKey: String) {
        val childFragment = parentFragment.childFragmentManager.findFragmentById(R.id.frag_container)
        (childFragment as? BasePreferenceFragment)?.scrollToPreference(preferenceKey)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        supportFragmentManager.fragments.forEach {
            it.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun openSearch() {
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.slide_in_right, R.anim.slide_out_left,
        )
        startActivity(Intent(this, SearchActivity::class.java), options.toBundle())
    }

    fun openAbout() {
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.slide_in_right, R.anim.slide_out_left,
        )
        startActivity(Intent(this, AboutActivity::class.java), options.toBundle())
    }

    fun openAppearance() {
        startActivity(Intent(this, AppearanceActivity::class.java))
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimization() {
        if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
            batteryPermissionHelper.getPermission(this, true, true)
        } else {
            val batteryIntent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            @Suppress("DEPRECATION")
            startActivityForResult(batteryIntent, 0)
        }
    }

    fun showTools() {
        if (supportFragmentManager.findFragmentByTag("legacy_home") == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.legacy_container, HomeFragment(), "legacy_home")
                .commit()
        }
        binding.legacyContainer.visibility = View.VISIBLE
        toolsBack?.remove()
        toolsBack = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                hideTools()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }
    }

    private fun hideTools() {
        supportFragmentManager.findFragmentByTag("legacy_home")?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        binding.legacyContainer.visibility = View.GONE
        toolsBack?.remove()
        toolsBack = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
