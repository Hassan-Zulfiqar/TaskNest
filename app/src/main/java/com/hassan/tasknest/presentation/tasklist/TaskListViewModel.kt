package com.hassan.tasknest.presentation.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.local.entity.Priority
import com.hassan.tasknest.data.local.entity.Task
import com.hassan.tasknest.data.repository.PreferencesRepository
import com.hassan.tasknest.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Manages UI state for the task list screen, including filtering, sorting, and task actions. */
class TaskListViewModel(
    private val taskRepository: TaskRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(TaskFilter.ALL)
    private val _sortOrder = MutableStateFlow(TaskSortOrder.DUE_DATE)
    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _activeFilter.value = preferencesRepository.getDefaultFilter().first()
            _sortOrder.value = preferencesRepository.getDefaultSortOrder().first()
        }
    }

    private val allTasksFlow: Flow<List<Task>> = taskRepository.getAllTasks()

    val uiState: StateFlow<TaskListUiState> = combine(
        allTasksFlow,
        _activeFilter,
        _sortOrder,
        _searchQuery
    ) { tasks, filter, order, searchQuery ->
        val hasAnyTasks = tasks.isNotEmpty()
        val filtered = applyFilter(tasks, filter)
        val searched = if (searchQuery.isNotBlank()) {
            filtered.filter { it.title.contains(searchQuery, ignoreCase = true) }
        } else {
            filtered
        }
        val sorted = applySort(searched, order)
        TaskListUiState(
            tasks = sorted,
            activeFilter = filter,
            isLoading = false,
            sortOrder = order,
            searchQuery = searchQuery,
            hasAnyTasks = hasAnyTasks
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskListUiState()
    )

    /** Updates the live search query applied to the task list. */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Updates the active filter applied to the task list. */
    fun setFilter(filter: TaskFilter) {
        _activeFilter.value = filter
    }

    /** Updates the sort order applied to the task list. */
    fun setSortOrder(order: TaskSortOrder) {
        _sortOrder.value = order
    }

    /** Flips the completion state of the given task. */
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch { taskRepository.toggleTaskCompletion(task) }
    }

    /** Permanently removes the given task from the database. */
    fun deleteTask(task: Task) {
        viewModelScope.launch { taskRepository.deleteTask(task) }
    }

    private fun applyFilter(tasks: List<Task>, filter: TaskFilter): List<Task> {
        val calendar = Calendar.getInstance()

        val startOfDay = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return when (filter) {
            TaskFilter.ALL -> tasks.filter { !it.isCompleted }
            TaskFilter.TODAY -> tasks.filter { task ->
                !task.isCompleted && task.dueDate != null &&
                    task.dueDate >= startOfDay && task.dueDate <= endOfDay
            }
            TaskFilter.UPCOMING -> tasks.filter { task ->
                !task.isCompleted && task.dueDate != null && task.dueDate > endOfDay
            }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }
    }

    private fun applySort(tasks: List<Task>, order: TaskSortOrder): List<Task> = when (order) {
        TaskSortOrder.DUE_DATE -> tasks.sortedWith(compareBy(nullsLast()) { it.dueDate })
        TaskSortOrder.PRIORITY -> tasks.sortedWith(compareByDescending { priorityRank(it.priority) })
        TaskSortOrder.CREATED_DATE -> tasks.sortedByDescending { it.createdAt }
        TaskSortOrder.MANUAL -> tasks.sortedBy { it.position }
    }

    private fun priorityRank(priority: Priority): Int = when (priority) {
        Priority.HIGH -> 2
        Priority.MEDIUM -> 1
        Priority.LOW -> 0
    }
}
