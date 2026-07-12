package com.hassan.tasknest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hassan.tasknest.data.local.dao.CategoryDao
import com.hassan.tasknest.data.local.dao.NoteDao
import com.hassan.tasknest.data.local.dao.TaskDao
import com.hassan.tasknest.data.local.entity.Category
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.data.local.entity.PriorityConverter
import com.hassan.tasknest.data.local.entity.Task

/** Provides the TaskNest Room database and DAO entry points. */
@Database(entities = [Task::class, Category::class, Note::class], version = 3, exportSchema = false)
@TypeConverters(PriorityConverter::class)
abstract class AppDatabase : RoomDatabase() {

	abstract fun taskDao(): TaskDao

	abstract fun categoryDao(): CategoryDao

	abstract fun noteDao(): NoteDao

	companion object {
		@Volatile
		private var INSTANCE: AppDatabase? = null

		fun getInstance(context: Context): AppDatabase {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: Room.databaseBuilder(
					context.applicationContext,
					AppDatabase::class.java,
					"tasknest_database"
				).fallbackToDestructiveMigration().build().also { INSTANCE = it }
			}
		}
	}
}