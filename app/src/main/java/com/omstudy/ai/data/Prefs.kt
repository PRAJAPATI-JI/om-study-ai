package com.omstudy.ai.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "settings")

object Prefs {
    /** 0 = फोन जैसा, 1 = लाइट, 2 = डार्क */
    val THEME = intPreferencesKey("theme")
    val NAME = stringPreferencesKey("name")
}
