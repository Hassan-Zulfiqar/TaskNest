package com.hassan.tasknest.data.repository

import com.hassan.tasknest.data.local.dao.CategoryDao
import com.hassan.tasknest.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

/** Mediates between the ViewModel layer and CategoryDao for all category-related data operations. */
class CategoryRepository(private val categoryDao: CategoryDao) {

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    fun getCategoryById(categoryId: Long): Flow<Category?> = categoryDao.getCategoryById(categoryId)

    suspend fun addCategory(category: Category): Long = categoryDao.insertCategory(category)

    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)
}
