package com.hassan.tasknest.presentation.noteslist

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.R
import com.hassan.tasknest.databinding.FragmentNotesListBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays the full notes list with empty state and add-note entry point. */
class NotesListFragment : Fragment() {

    private var _binding: FragmentNotesListBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: NotesListViewModel by viewModel()
    private lateinit var noteAdapter: NoteAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.inflateMenu(R.menu.menu_notes_list)

        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean = true})

            searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(180, 255, 255, 255))
        }
        binding.toolbar.menu.findItem(R.id.action_search).icon?.setTint(Color.WHITE)
        searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
            .setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
            .setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                binding.toolbar.navigationIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.toolbar.navigationIcon?.clearColorFilter()
                return true
            }
        })

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(
                        NotesListFragmentDirections.actionNotesListFragmentToSettingsFragment()
                    )
                    true
                }
                else -> false
            }
        }

        noteAdapter = NoteAdapter(
            onNoteClick = { note -> onNoteClicked(note) },
            onNoteLongClick = { note ->
                AlertDialog.Builder(requireContext())
                    .setMessage("Delete this note?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteNote(note) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvNotes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotes.adapter = noteAdapter

        binding.fabAddNote.setOnClickListener { onAddNoteClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    noteAdapter.submitNotes(uiState.notes)
                    binding.progressLoading.visibility = if (uiState.isLoading) View.VISIBLE else View.GONE

                    if (uiState.isLoading) {
                        binding.rvNotes.visibility = View.GONE
                        binding.emptyStateContainer.visibility = View.GONE
                    } else {
                        if (uiState.notes.isNotEmpty()) {
                            binding.rvNotes.visibility = View.VISIBLE
                            binding.emptyStateContainer.visibility = View.GONE
                        } else {
                            binding.rvNotes.visibility = View.GONE
                            binding.emptyStateContainer.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onNoteClicked(note: Note) {
        findNavController().navigate(
            NotesListFragmentDirections.actionNotesListFragmentToAddEditNoteFragment(
                noteId = note.id
            )
        )
    }

    private fun onAddNoteClicked() {
        findNavController().navigate(
            NotesListFragmentDirections.actionNotesListFragmentToAddEditNoteFragmentFromFab(
                noteId = -1L
            )
        )
    }
}