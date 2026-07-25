package com.flysafeweather.app.data.update

import android.content.Context

/**
 * "Not now" snoozes the update prompt for 24 hours.
 * A release newer than the snoozed one prompts immediately.
 */
class UpdatePromptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldPrompt(versionCode: Int): Boolean {
        val snoozedVersion = prefs.getInt(KEY_VERSION, 0)
        if (versionCode > snoozedVersion) return true
        return System.currentTimeMillis() >= prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
    }

    fun snooze(versionCode: Int) {
        prefs.edit()
            .putInt(KEY_VERSION, versionCode)
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .apply()
    }

    private companion object {
        const val PREFS = "app_update_prompt"
        const val KEY_VERSION = "snoozed_version_code"
        const val KEY_SNOOZE_UNTIL = "snooze_until_ms"
        const val SNOOZE_MS = 24L * 60L * 60L * 1000L
    }
}
