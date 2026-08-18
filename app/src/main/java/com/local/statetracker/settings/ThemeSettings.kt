package com.local.statetracker.settings

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
private val Context.themeDataStore by preferencesDataStore("appearance_settings")
class ThemeSettings(private val context:Context){
    private val key=stringPreferencesKey("theme_mode")
    private val cycleKey=booleanPreferencesKey("analytics_show_cycle_start")
    val mode:Flow<ThemeMode> = context.themeDataStore.data.map{runCatching{ThemeMode.valueOf(it[key]?:ThemeMode.SYSTEM.name)}.getOrDefault(ThemeMode.SYSTEM)}
    suspend fun set(mode:ThemeMode){context.themeDataStore.edit{it[key]=mode.name}}
    val showCycleOverlay:Flow<Boolean> = context.themeDataStore.data.map{it[cycleKey]?:false}
    suspend fun setCycleOverlay(show:Boolean){context.themeDataStore.edit{it[cycleKey]=show}}
}
