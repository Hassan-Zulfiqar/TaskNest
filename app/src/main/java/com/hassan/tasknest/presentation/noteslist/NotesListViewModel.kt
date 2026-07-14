package com.hassan.tasknest.presentation.noteslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manages the notes list screen state. */
class NotesListViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<NotesListUiState> = combine(
        noteRepository.getAllNotes(),
        _searchQuery
    ) { notes, searchQuery ->
        val filteredNotes = if (searchQuery.isBlank()) {
            notes
        } else {
            notes.filter { note ->
                note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
            }
        }

        NotesListUiState(
            notes = filteredNotes,
            isLoading = false,
            searchQuery = searchQuery
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesListUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Deletes the given note. */
    fun deleteNote(note: Note) {
        viewModelScope.launch { noteRepository.deleteNote(note) }
    }
}