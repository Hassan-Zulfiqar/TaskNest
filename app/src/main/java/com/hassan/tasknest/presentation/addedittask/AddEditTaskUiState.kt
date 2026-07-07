package com.hassan.tasknest.presentation.addedittask

import com.hassan.tasknest.data.local.entity.Priority

data class AddEditTaskUiState(
    val taskId: Long? = null,
    val title: String = "",
    val description: String = "",
    val dueDateMillis: Long? = null,
    val dueTimeMillis: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val isReminderEnabled: Boolean = false,
    val isEditMode: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val isTaskSaved: Boolean = false
)
