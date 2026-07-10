package com.hassan.tasknest.presentation.category

data class CategoryUiState(
    val categories: List<CategoryWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val deleteBlockedCategory: CategoryWithCount? = null,
    val duplicateNameError: Boolean = false,
    val isSaved: Boolean = false
)
