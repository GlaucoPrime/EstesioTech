package com.code.EstesioTech

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {
    fun setLocale(context: Context) {
        val prefs = context.getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString("language", "pt") ?: "pt" // Padrão PT

        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}