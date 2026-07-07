package com.hassan.tasknest.data.repository

import com.hassan.tasknest.data.local.dao.TaskDao
import com.hassan.tasknest.data.local.entity.Task
import kotlinx.coroutines.flow.Flow

/** Mediates between the ViewModel layer and TaskDao for all task-related data operations. */
class TaskRepository(private val taskDao: TaskDao) {

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    fun getTaskById(taskId: Long): Flow<Task?> = taskDao.getTaskById(taskId)

    fun getTasksByCategory(categoryId: Long): Flow<List<Task>> = taskDao.getTasksByCategory(categoryId)

    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()

    suspend fun addTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    suspend fun toggleTaskCompletion(task: Task) = taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))

    suspend fun reassignTasksFromCategory(categoryId: Long) {
        taskDao.reassignTasksFromCategory(categoryId)
    }
}
