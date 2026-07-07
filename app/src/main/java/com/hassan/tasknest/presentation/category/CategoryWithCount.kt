package com.hassan.tasknest.presentation.category

import com.hassan.tasknest.data.local.entity.Category

data class CategoryWithCount(
    val category: Category,
    val taskCount: Int
)
