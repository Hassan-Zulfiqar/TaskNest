package com.hassan.tasknest.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tasknest_preferences")

/** Wraps DataStore<Preferences> and exposes typed accessors for app preferences. */
class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")
        private val DEFAULT_FILTER = stringPreferencesKey("default_filter")
    }

    fun getThemeMode(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    fun getDefaultSortOrder(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_SORT_ORDER] ?: "DUE_DATE"
    }

    suspend fun setDefaultSortOrder(sortOrder: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_SORT_ORDER] = sortOrder
        }
    }

    fun getDefaultFilter(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_FILTER] ?: "ALL"
    }

    suspend fun setDefaultFilter(filter: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_FILTER] = filter
        }
    }
}
