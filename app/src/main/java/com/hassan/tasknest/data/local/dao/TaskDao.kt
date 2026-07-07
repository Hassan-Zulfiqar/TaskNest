package com.hassan.tasknest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hassan.tasknest.data.local.entity.Task
import kotlinx.coroutines.flow.Flow

/** Accesses task records in the Room database. */
@Dao
interface TaskDao {
	@Query("SELECT * FROM tasks ORDER BY dueDate ASC")
	fun getAllTasks(): Flow<List<Task>>

	@Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
	fun getTaskById(taskId: Long): Flow<Task?>

	@Query("SELECT * FROM tasks WHERE categoryId = :categoryId")
	fun getTasksByCategory(categoryId: Long): Flow<List<Task>>

	@Query("SELECT * FROM tasks WHERE isCompleted = 1")
	fun getCompletedTasks(): Flow<List<Task>>

	@Insert
	suspend fun insertTask(task: Task): Long

	@Update
	suspend fun updateTask(task: Task)

	@Delete
	suspend fun deleteTask(task: Task)

	@Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :categoryId")
	suspend fun reassignTasksFromCategory(categoryId: Long): Int
}