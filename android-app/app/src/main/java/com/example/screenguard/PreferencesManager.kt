package com.example.screenguard

import android.content.Context

object PreferencesManager {
    private const val PREFS_NAME = "screenguard_settings"
    private const val KEY_CHILD_MODE = "is_child_mode"

    fun setChildMode(context: Context, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CHILD_MODE, isEnabled).apply()
    }

    fun isChildMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CHILD_MODE, false) // Defaults to Adult Profile (false)
    }
}