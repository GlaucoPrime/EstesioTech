package com.code.EstesioTech.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {
    private const val PREFS_NAME   = "EstesioPrefs"
    private const val KEY_LANGUAGE = "language"
    private const val DEFAULT_LANG = "pt"

    fun setLocale(context: Context) {
        applyLocale(context, getSavedLanguage(context))
    }

    fun applyLocale(context: Context, langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun wrapContext(context: Context): Context {
        val langCode = getSavedLanguage(context)
        val locale   = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getSavedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANG) ?: DEFAULT_LANG

    fun saveAndApply(context: Context, langCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, langCode).apply()
        applyLocale(context, langCode)
    }
}
