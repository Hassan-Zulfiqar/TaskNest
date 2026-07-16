package com.hassan.tasknest.presentation.settings

import com.hassan.tasknest.presentation.tasklist.TaskFilter
import com.hassan.tasknest.presentation.tasklist.TaskSortOrder

data class SettingsUiState(
    val themeMode: String = "SYSTEM",
    val defaultSortOrder: TaskSortOrder = TaskSortOrder.DUE_DATE,
    val defaultFilter: TaskFilter = TaskFilter.ALL,
    val isNotificationPermissionGranted: Boolean = false,
    val isMicPermissionGranted: Boolean = false
)
