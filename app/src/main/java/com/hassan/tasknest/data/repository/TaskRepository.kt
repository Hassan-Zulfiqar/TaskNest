package com.hassan.tasknest.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hassan.tasknest.data.local.dao.TaskDao
import com.hassan.tasknest.data.local.entity.Task
import com.hassan.tasknest.worker.ReminderWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class TaskRepository(private val taskDao: TaskDao, private val context: Context) {

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    fun getTaskById(taskId: Long): Flow<Task?> = taskDao.getTaskById(taskId)

    fun getTasksByCategory(categoryId: Long): Flow<List<Task>> = taskDao.getTasksByCategory(categoryId)

    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()

    suspend fun addTask(task: Task): Long {
        val generatedId = taskDao.insertTask(task)
        scheduleOrCancelReminder(task.copy(id = generatedId))
        return generatedId
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
        scheduleOrCancelReminder(task)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        WorkManager.getInstance(context).cancelUniqueWork("reminder_task_${task.id}")
    }

    suspend fun toggleTaskCompletion(task: Task) = taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))

    suspend fun reassignTasksFromCategory(categoryId: Long) {
        taskDao.reassignTasksFromCategory(categoryId)
    }

    private fun scheduleOrCancelReminder(task: Task) {
        val workName = "reminder_task_${task.id}"
        val dueDate = task.dueDate
        if (task.reminderTime != null && dueDate != null) {
            val fireTimeMillis = dueDate - task.reminderLeadMinutes * 60 * 1000L
            if (fireTimeMillis > System.currentTimeMillis()) {
                val delayMillis = fireTimeMillis - System.currentTimeMillis()
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ReminderWorker.TASK_ID_KEY to task.id))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(workName)
            }
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(workName)
        }
    }
}
