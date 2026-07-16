package com.hassan.tasknest.presentation.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.repository.PreferencesRepository
import com.hassan.tasknest.presentation.tasklist.TaskFilter
import com.hassan.tasknest.presentation.tasklist.TaskSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manages user preferences state and exposes setters for theme, sort order, filter, and notification permission. */
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val context: Context
) : ViewModel() {

    private val _notificationPermission = MutableStateFlow(checkNotificationPermission())
    private val _micPermission = MutableStateFlow(checkMicPermission())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.getThemeMode(),
        preferencesRepository.getDefaultSortOrder(),
        preferencesRepository.getDefaultFilter(),
        _notificationPermission,
        _micPermission
    ) { themeMode, sortOrder, filter, notifGranted, micGranted ->
        SettingsUiState(
            themeMode = themeMode,
            defaultSortOrder = sortOrder,
            defaultFilter = filter,
            isNotificationPermissionGranted = notifGranted,
            isMicPermissionGranted = micGranted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** Persists the selected theme mode string ("SYSTEM", "LIGHT", or "DARK"). */
    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    /** Persists the selected default sort order for the task list. */
    fun setDefaultSortOrder(sortOrder: TaskSortOrder) {
        viewModelScope.launch { preferencesRepository.setDefaultSortOrder(sortOrder) }
    }

    /** Persists the selected default filter for the task list. */
    fun setDefaultFilter(filter: TaskFilter) {
        viewModelScope.launch { preferencesRepository.setDefaultFilter(filter) }
    }

    /** Re-checks the app permission statuses and updates uiState; call this in Fragment.onResume. */
    fun refreshPermissionStatuses() {
        _notificationPermission.value = checkNotificationPermission()
        _micPermission.value = checkMicPermission()
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
