package com.hassan.tasknest.presentation.addeditnotes

data class AddEditNoteUiState(
	val noteId: Long? = null,
	val title: String = "",
	val content: String = "",
	val originalCreatedAt: Long? = null,
	val isEditMode: Boolean = false,
	val isNoteSaved: Boolean = false
)