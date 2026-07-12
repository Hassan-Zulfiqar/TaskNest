package com.hassan.tasknest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hassan.tasknest.data.local.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

	@Query("SELECT * FROM notes ORDER BY updatedAt DESC")
	fun getAllNotes(): Flow<List<Note>>

	@Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
	fun getNoteById(noteId: Long): Flow<Note?>

	@Insert
	suspend fun insertNote(note: Note): Long

	@Update
	suspend fun updateNote(note: Note)

	@Delete
	suspend fun deleteNote(note: Note)
}