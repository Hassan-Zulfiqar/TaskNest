package com.hassan.tasknest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Stores a task and all of its scheduling fields. */
@Entity(tableName = "tasks")
data class Task(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0L,
	val title: String,
	val description: String? = null,
	val isCompleted: Boolean = false,
	val priority: Priority = Priority.MEDIUM,
	val dueDate: Long? = null,
	val reminderTime: Long? = null,
	val categoryId: Long? = null,
	val createdAt: Long = System.currentTimeMillis(),
	val isRecurring: Boolean = false,
	val recurrenceRule: String? = null,
	val position: Int = 0
)

/** Converts task priority values for Room storage. */
object PriorityConverter {
	@TypeConverter
	@JvmStatic
	fun fromPriority(priority: Priority): Int = when (priority) {
		Priority.LOW -> 0
		Priority.MEDIUM -> 1
		Priority.HIGH -> 2
	}

	@TypeConverter
	@JvmStatic
	fun toPriority(value: Int): Priority = when (value) {
		0 -> Priority.LOW
		1 -> Priority.MEDIUM
		2 -> Priority.HIGH
		else -> Priority.MEDIUM
	}
}