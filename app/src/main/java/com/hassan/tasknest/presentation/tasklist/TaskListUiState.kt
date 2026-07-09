package com.hassan.tasknest.presentation.tasklist

import com.hassan.tasknest.data.local.entity.Task

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val activeFilter: TaskFilter = TaskFilter.ALL,
    val sortOrder: TaskSortOrder = TaskSortOrder.DUE_DATE,
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)
