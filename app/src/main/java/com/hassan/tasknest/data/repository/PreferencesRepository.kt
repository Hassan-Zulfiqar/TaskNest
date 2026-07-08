package com.hassan.tasknest.data.repository

import com.hassan.tasknest.data.datastore.UserPreferencesDataStore
import com.hassan.tasknest.presentation.tasklist.TaskFilter
import com.hassan.tasknest.presentation.tasklist.TaskSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Mediates between the ViewModel layer and UserPreferencesDataStore, converting enums to/from stored Strings. */
class PreferencesRepository(private val dataStore: UserPreferencesDataStore) {

    fun getThemeMode(): Flow<String> = dataStore.getThemeMode()

    suspend fun setThemeMode(mode: String) = dataStore.setThemeMode(mode)

    fun getDefaultSortOrder(): Flow<TaskSortOrder> = dataStore.getDefaultSortOrder()
        .map { TaskSortOrder.valueOf(it) }

    suspend fun setDefaultSortOrder(sortOrder: TaskSortOrder) =
        dataStore.setDefaultSortOrder(sortOrder.name)

    fun getDefaultFilter(): Flow<TaskFilter> = dataStore.getDefaultFilter()
        .map { TaskFilter.valueOf(it) }

    suspend fun setDefaultFilter(filter: TaskFilter) =
        dataStore.setDefaultFilter(filter.name)
}
