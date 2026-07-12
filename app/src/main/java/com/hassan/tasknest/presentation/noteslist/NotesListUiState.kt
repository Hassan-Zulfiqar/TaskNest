package com.hassan.tasknest.presentation.noteslist

import com.hassan.tasknest.data.local.entity.Note

data class NotesListUiState(
	val notes: List<Note> = emptyList(),
	val isLoading: Boolean = true
)