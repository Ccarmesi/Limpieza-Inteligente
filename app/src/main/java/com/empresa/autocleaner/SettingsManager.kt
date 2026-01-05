package com.empresa.autocleaner

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFERENCES_FILE = "AutoCleanerSettings"
    private const val KEY_DAYS_TO_KEEP = "days_to_keep"
    private const val DEFAULT_DAYS = 30
    private const val KEY_EXECUTION_FREQUENCY = "execution_frequency"
    private const val DEFAULT_FREQUENCY_HOURS = 24

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    fun saveDaysToKeep(context: Context, days: Int) {
        getPreferences(context).edit().putInt(KEY_DAYS_TO_KEEP, days).apply()
    }

    fun getDaysToKeep(context: Context): Int {
        return getPreferences(context).getInt(KEY_DAYS_TO_KEEP, DEFAULT_DAYS)
    }

    fun saveExecutionFrequency(context: Context, hours: Int) {
        getPreferences(context).edit().putInt(KEY_EXECUTION_FREQUENCY, hours).apply()
    }

    fun getExecutionFrequency(context: Context): Int {
        return getPreferences(context).getInt(KEY_EXECUTION_FREQUENCY, DEFAULT_FREQUENCY_HOURS)
    }
}