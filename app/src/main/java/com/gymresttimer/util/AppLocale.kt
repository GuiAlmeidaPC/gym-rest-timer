package com.gymresttimer.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Self-contained per-app language selection that works on API 26+ without
 * AppCompat. The chosen BCP-47 tag is persisted in SharedPreferences and applied
 * by wrapping the base context of every component in [wrap]; callers re-create
 * the activity after [setTag] so the new locale takes effect immediately.
 */
object AppLocale {
    private const val PREFS = "settings"
    private const val KEY_LANG = "app_language_tag"

    /** Supported language tags. Empty string means "follow the system locale". */
    const val SYSTEM = ""

    /** Returns the persisted language tag, or [SYSTEM] when none was chosen. */
    fun persistedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, SYSTEM) ?: SYSTEM

    fun setTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, tag)
            .apply()
    }

    /** Wraps [base] so resources resolve in the persisted language. */
    fun wrap(base: Context): Context {
        val tag = persistedTag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
