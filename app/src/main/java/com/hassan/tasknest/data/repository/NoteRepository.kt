package com.hassan.tasknest.data.repository

import com.hassan.tasknest.data.local.dao.NoteDao
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.presentation.addeditnotes.formatting.NoteImageStorage
import com.hassan.tasknest.presentation.addeditnotes.formatting.SpannableConverter
import kotlinx.coroutines.flow.Flow

/** Wraps NoteDao for note data operations. */
class NoteRepository(
	private val noteDao: NoteDao,
	private val noteImageStorage: NoteImageStorage
) {

	fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

	fun getNoteById(noteId: Long): Flow<Note?> = noteDao.getNoteById(noteId)

	suspend fun addNote(title: String, content: String): Long {
		val now = System.currentTimeMillis()
		return noteDao.insertNote(
			Note(
				title = title,
				content = content,
				createdAt = now,
				updatedAt = now
			)
		)
	}

	suspend fun updateNote(noteId: Long, title: String, content: String, originalCreatedAt: Long) {
		noteDao.updateNote(
			Note(
				id = noteId,
				title = title,
				content = content,
				createdAt = originalCreatedAt,
				updatedAt = System.currentTimeMillis()
			)
		)
	}

	suspend fun deleteNote(note: Note) {
		val imagePaths = SpannableConverter.extractImagePaths(note.content)
		imagePaths.forEach { path -> noteImageStorage.deleteImage(path) }
		noteDao.deleteNote(note)
	}
}

