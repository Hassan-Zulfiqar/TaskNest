package com.hassan.tasknest.presentation.noteslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.data.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manages the notes list screen state. */
class NotesListViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    val uiState: StateFlow<NotesListUiState> = noteRepository.getAllNotes()
        .map { notes -> NotesListUiState(notes = notes, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesListUiState())

    /** Deletes the given note. */
    fun deleteNote(note: Note) {
        viewModelScope.launch { noteRepository.deleteNote(note) }
    }
}