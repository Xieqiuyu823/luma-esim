package im.angry.openeuicc.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import im.angry.openeuicc.common.R

object LumaAppearance {
    private val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)

    fun apply(context: Context) {
        val selection = context.getSharedPreferences("luma_ui", Context.MODE_PRIVATE)
            .getInt("theme", 0).coerceIn(modes.indices)
        AppCompatDelegate.setDefaultNightMode(modes[selection])
    }

    fun showPicker(context: Context) {
        val preferences = context.getSharedPreferences("luma_ui", Context.MODE_PRIVATE)
        val selected = preferences.getInt("theme", 0).coerceIn(modes.indices)
        val labels = arrayOf(context.getString(R.string.luma_theme_system),
            context.getString(R.string.luma_theme_light), context.getString(R.string.luma_theme_dark))
        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.luma_appearance)
            .setSingleChoiceItems(labels, selected) { dialog, choice ->
                preferences.edit().putInt("theme", choice).apply()
                dialog.dismiss()
                apply(context)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
