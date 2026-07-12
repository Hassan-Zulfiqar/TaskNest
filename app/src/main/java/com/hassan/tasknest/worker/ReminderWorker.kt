package com.hassan.tasknest.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hassan.tasknest.R
import com.hassan.tasknest.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val taskRepository: TaskRepository by inject()

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(TASK_ID_KEY, -1L)
        if (taskId == -1L) return Result.failure()

        val task = taskRepository.getTaskById(taskId).first() ?: return Result.success()

        val formattedLeadTime = when (task.reminderLeadMinutes) {
            5 -> "5 minutes"
            15 -> "15 minutes"
            30 -> "30 minutes"
            60 -> "1 hour"
            1440 -> "1 day"
            else -> "${task.reminderLeadMinutes} minutes"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Task Reminder")
            .setContentText("\"${task.title}\" is due in $formattedLeadTime")
            .setSmallIcon(R.drawable.ic_bell)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(taskId.toInt(), notification)
        } catch (e: SecurityException) {
            return Result.success()
        }

        return Result.success()
    }

    companion object {
        const val TASK_ID_KEY = "TASK_ID"
        private const val CHANNEL_ID = "tasknest_reminders"
    }
}
