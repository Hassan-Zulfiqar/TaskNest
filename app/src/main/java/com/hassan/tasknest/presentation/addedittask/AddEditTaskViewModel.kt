package com.hassan.tasknest.presentation.addedittask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.local.entity.Category
import com.hassan.tasknest.data.local.entity.Priority
import com.hassan.tasknest.data.local.entity.Task
import com.hassan.tasknest.data.repository.CategoryRepository
import com.hassan.tasknest.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manages form state and save logic for the add/edit task bottom sheet. */
class AddEditTaskViewModel(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditTaskUiState())
    val uiState: StateFlow<AddEditTaskUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Loads an existing task into the form fields, switching the screen into edit mode. */
    fun loadTaskForEdit(taskId: Long) {
        viewModelScope.launch {
            val task = taskRepository.getTaskById(taskId).first() ?: return@launch
            _uiState.value = AddEditTaskUiState(
                taskId = task.id,
                title = task.title,
                description = task.description ?: "",
                dueDateMillis = task.dueDate,
                dueTimeMillis = null,
                priority = task.priority,
                categoryId = task.categoryId,
                isReminderEnabled = task.reminderTime != null,
                reminderLeadMinutes = task.reminderLeadMinutes,
                isEditMode = true,
                isSaveEnabled = task.title.isNotBlank(),
                isTaskSaved = false
            )
        }
    }

    /** Updates the task title and recomputes whether saving is allowed. */
    fun updateTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value, isSaveEnabled = value.isNotBlank(), duplicateTitleError = false)
    }

    /** Updates the task description. */
    fun updateDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    /** Updates the selected due date as epoch millis representing start-of-day. */
    fun updateDueDate(millis: Long) {
        _uiState.value = _uiState.value.copy(dueDateMillis = millis)
    }

    /** Updates the selected due time as milliseconds offset from midnight. */
    fun updateDueTime(millis: Long) {
        _uiState.value = _uiState.value.copy(dueTimeMillis = millis)
    }

    /** Updates the task priority. */
    fun updatePriority(priority: Priority) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    /** Updates the selected category, or clears it when null. */
    fun updateCategory(categoryId: Long?) {
        _uiState.value = _uiState.value.copy(categoryId = categoryId)
    }

    /** Updates whether a reminder is enabled for this task. */
    fun updateReminderEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isReminderEnabled = enabled)
    }

    /** Updates the selected reminder lead time in minutes. */
    fun updateReminderLeadMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(reminderLeadMinutes = minutes)
    }

    /** Persists the current form state as a new or updated task, then signals completion. */
    fun saveTask() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.title.isBlank()) return@launch

            val trimmedTitle = state.title.trim()
            val allTasks = taskRepository.getAllTasks().first()
            val isDuplicate = allTasks.any { task ->
                task.id != state.taskId && task.title.trim().equals(trimmedTitle, ignoreCase = true)
            }
            if (isDuplicate) {
                _uiState.value = _uiState.value.copy(duplicateTitleError = true)
                return@launch
            }

            val combinedDueDate = state.dueDateMillis?.let { date ->
                state.dueTimeMillis?.let { time -> date + time } ?: date
            }
            val reminderTime = if (state.isReminderEnabled) combinedDueDate else null

            if (state.isEditMode) {
                // TODO: Preserve original createdAt when revisiting edit-mode task loading
                taskRepository.updateTask(
                    Task(
                        id = state.taskId ?: 0L,
                        title = state.title,
                        description = state.description.ifBlank { null },
                        isCompleted = false,
                        priority = state.priority,
                        dueDate = combinedDueDate,
                        reminderTime = reminderTime,
                        reminderLeadMinutes = state.reminderLeadMinutes,
                        categoryId = state.categoryId,
                        createdAt = System.currentTimeMillis(),
                        isRecurring = false,
                        recurrenceRule = null
                    )
                )
            } else {
                taskRepository.addTask(
                    Task(
                        title = state.title,
                        description = state.description.ifBlank { null },
                        isCompleted = false,
                        priority = state.priority,
                        dueDate = combinedDueDate,
                        reminderTime = reminderTime,
                        reminderLeadMinutes = state.reminderLeadMinutes,
                        categoryId = state.categoryId,
                        createdAt = System.currentTimeMillis(),
                        isRecurring = false,
                        recurrenceRule = null
                    )
                )
            }

            _uiState.value = _uiState.value.copy(isTaskSaved = true)
        }
    }
}
