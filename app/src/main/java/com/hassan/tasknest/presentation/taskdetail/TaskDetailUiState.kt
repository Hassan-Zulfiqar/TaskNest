package com.hassan.tasknest.presentation.taskdetail

import com.hassan.tasknest.data.local.entity.Task

data class TaskDetailUiState(
    val task: Task? = null,
    val isLoading: Boolean = true,
    val isTaskDeleted: Boolean = false
)
