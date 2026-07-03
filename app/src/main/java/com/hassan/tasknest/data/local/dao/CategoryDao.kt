package com.hassan.tasknest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hassan.tasknest.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

/** Accesses category records in the Room database. */
@Dao
interface CategoryDao {
	@Query("SELECT * FROM categories ORDER BY name ASC")
	fun getAllCategories(): Flow<List<Category>>

	@Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
	fun getCategoryById(categoryId: Long): Flow<Category?>

	@Insert
	suspend fun insertCategory(category: Category): Long

	@Update
	suspend fun updateCategory(category: Category)

	@Delete
	suspend fun deleteCategory(category: Category)
}