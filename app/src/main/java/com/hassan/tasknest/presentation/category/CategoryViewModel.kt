package com.hassan.tasknest.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.local.entity.Category
import com.hassan.tasknest.data.repository.CategoryRepository
import com.hassan.tasknest.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Manages the list of categories with per-category task counts, and orchestrates safe category deletion. */
class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                categoryRepository.getAllCategories(),
                taskRepository.getAllTasks()
            ) { categories, tasks ->
                categories
                    .sortedBy { it.name }
                    .map { category ->
                        CategoryWithCount(
                            category = category,
                            taskCount = tasks.count { it.categoryId == category.id }
                        )
                    }
            }.collect { categoriesWithCount ->
                _uiState.value = _uiState.value.copy(
                    categories = categoriesWithCount,
                    isLoading = false
                )
            }
        }
    }

    /** Creates a new category if the name is non-blank. */
    fun addCategory(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.addCategory(Category(name = name, colorHex = colorHex))
        }
    }

    /** Updates an existing category's name and color if the name is non-blank. */
    fun updateCategory(categoryId: Long, name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.updateCategory(Category(id = categoryId, name = name, colorHex = colorHex))
        }
    }

    /** Deletes immediately if the category has no tasks; otherwise surfaces a blocked state for the Fragment to prompt confirmation. */
    fun attemptDeleteCategory(categoryWithCount: CategoryWithCount) {
        viewModelScope.launch {
            if (categoryWithCount.taskCount > 0) {
                _uiState.value = _uiState.value.copy(deleteBlockedCategory = categoryWithCount)
            } else {
                categoryRepository.deleteCategory(categoryWithCount.category)
            }
        }
    }

    /** Reassigns all tasks from the blocked category to Uncategorized, then deletes the category. */
    fun confirmDeleteAndReassign() {
        viewModelScope.launch {
            val blocked = _uiState.value.deleteBlockedCategory ?: return@launch
            taskRepository.reassignTasksFromCategory(blocked.category.id)
            categoryRepository.deleteCategory(blocked.category)
            _uiState.value = _uiState.value.copy(deleteBlockedCategory = null)
        }
    }

    /** Clears the blocked-delete state without performing any deletion or reassignment. */
    fun cancelDeleteCategory() {
        _uiState.value = _uiState.value.copy(deleteBlockedCategory = null)
    }
}
