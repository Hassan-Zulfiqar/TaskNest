package com.hassan.tasknest.presentation.addeditnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manages the add/edit note screen state and save/delete actions. */
class AddEditNoteViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditNoteUiState())
    val uiState: StateFlow<AddEditNoteUiState> = _uiState.asStateFlow()

    /** Loads a note for edit mode. */
    fun loadNoteForEdit(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getNoteById(noteId).first() ?: return@launch
            _uiState.value = _uiState.value.copy(
                noteId = note.id,
                title = note.title,
                content = note.content,
                originalCreatedAt = note.createdAt,
                isEditMode = true
            )
        }
    }

    /** Updates the note title. */
    fun updateTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    /** Updates the note content. */
    fun updateContent(value: String) {
        _uiState.value = _uiState.value.copy(content = value)
    }

    /** Deletes the currently loaded note if one exists. */
    fun deleteNote() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val noteId = currentState.noteId ?: return@launch
            val createdAt = currentState.originalCreatedAt ?: System.currentTimeMillis()
            noteRepository.deleteNote(
                Note(
                    id = noteId,
                    title = currentState.title,
                    content = currentState.content,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Saves the current note contents if there is something to persist. */
    fun saveNote() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.title.isBlank() && currentState.content.isBlank()) return@launch

            if (currentState.isEditMode) {
                noteRepository.updateNote(
                    noteId = requireNotNull(currentState.noteId),
                    title = currentState.title,
                    content = currentState.content,
                    originalCreatedAt = requireNotNull(currentState.originalCreatedAt)
                )
            } else {
                noteRepository.addNote(currentState.title, currentState.content)
            }

            _uiState.value = _uiState.value.copy(isNoteSaved = true)
        }
    }
}