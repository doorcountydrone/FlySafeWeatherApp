package com.flysafeweather.app.data.update

import android.content.Context

/** Remembers which remote versionCode the user dismissed with "Not now". */
class UpdatePromptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dismissedVersionCode(): Int = prefs.getInt(KEY_DISMISSED, 0)

    fun dismiss(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED, versionCode).apply()
    }

    private companion object {
        const val PREFS = "app_update_prompt"
        const val KEY_DISMISSED = "dismissed_version_code"
    }
}
