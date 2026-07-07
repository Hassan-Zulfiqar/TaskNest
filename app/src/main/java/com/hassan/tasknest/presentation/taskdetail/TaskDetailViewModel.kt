package com.hassan.tasknest.presentation.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Manages UI state for the task detail screen, including reactive task loading, deletion, and completion toggling. */
class TaskDetailViewModel(private val taskRepository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    /** Begins collecting the task with the given id, keeping state in sync with any changes while the screen is open. */
    fun loadTask(taskId: Long) {
        viewModelScope.launch {
            taskRepository.getTaskById(taskId).collect { task ->
                _uiState.value = _uiState.value.copy(task = task, isLoading = false)
            }
        }
    }

    /** Flips the completion state of the currently loaded task; does nothing if no task is loaded. */
    fun toggleTaskCompletion() {
        val task = _uiState.value.task ?: return
        viewModelScope.launch {
            taskRepository.toggleTaskCompletion(task)
        }
    }

    /** Deletes the currently loaded task and signals the Fragment to navigate back; does nothing if no task is loaded. */
    fun deleteTask() {
        val task = _uiState.value.task ?: return
        viewModelScope.launch {
            taskRepository.deleteTask(task)
            _uiState.value = _uiState.value.copy(isTaskDeleted = true)
        }
    }
}
