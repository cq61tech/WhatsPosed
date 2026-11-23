package com.wmods.wppenhacer.xposed.features.general

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.net.toUri
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.features.customization.ThemeCoordinator
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class NewChat(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (!xprefs.getBoolean("newchat", true)) return

        if (MenuHome.isHubEnabled(xprefs)) {
            MenuHome.addQuickTool { activity ->
                MenuHome.QuickToolEntry(
                    id = "new_chat",
                    order = 40,
                    title = activity.getString(R.string.new_chat),
                    subtitle = activity.getString(R.string.quick_tools_new_chat_sum),
                    icon = DesignUtils.getDrawableByName("vec_ic_chat_add"),
                    active = null,
                    onClick = { showNewChatDialog(activity) }
                )
            }
            return
        }

        if (ThemeCoordinator.isIos27(xprefs) && xprefs.getBoolean("ios27_header_search", true)) return
        val action = xprefs.getBoolean("buttonaction", true)

        homeActivityClass.resolve().firstMethod {
            name = "onCreateOptionsMenu"
            superclass()
            parameters(Menu::class.java)
        }.hook {
            after {
                val activity = instance as Activity
                val menu = args[0] as Menu
                val item = menu.add(0, 0, 0, R.string.new_chat)
                val drawable = DesignUtils.getDrawableByName("vec_ic_chat_add")

                if (drawable != null) {
                    drawable.setTint(if (action) DesignUtils.getPrimaryTextColor() else -0x796960)
                    item.icon = drawable
                }

                if (action) {
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }

                item.setOnMenuItemClickListener {
                    showNewChatDialog(activity)
                    true
                }
            }
        }
    }

    private fun showNewChatDialog(activity: Activity) {
        val view = LinearLayout(activity)
        view.gravity = Gravity.CENTER
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        val edt = EditText(view.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.0f
            )
            inputType = InputType.TYPE_CLASS_PHONE
            transformationMethod = null
            setHint(R.string.number_with_country_code)
            view.addView(this)
        }

        AlertDialogWpp(activity)
            .setTitle(activity.getString(R.string.new_chat))
            .setView(view)
            .setPositiveButton(
                activity.getString(R.string.message)
            ) { _, _ ->
                val number = edt.text.toString()
                val numberFormatted = number.replace("[+\-()/\s]".toRegex(), "")
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = ("https://wa.me/$numberFormatted").toUri()
                intent.setPackage(Utils.application.packageName)
                activity.startActivity(intent)
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    public override fun getPluginName(): String {
        return "New Chat"
    }
}
