package com.hassan.tasknest.presentation.addeditnotes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.hassan.tasknest.databinding.FragmentAddEditNoteBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays the add/edit note form. */
class AddEditNoteFragment : Fragment() {

    private var _binding: FragmentAddEditNoteBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AddEditNoteViewModel by viewModel()
    private val args: AddEditNoteFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnSaveNote.setOnClickListener { viewModel.saveNote() }
        binding.btnDeleteNote.setOnClickListener {
            viewModel.deleteNote()
            findNavController().navigateUp()
        }

        binding.etNoteTitle.addTextChangedListener { text ->
            viewModel.updateTitle(text?.toString() ?: "")
        }
        binding.etNoteContent.addTextChangedListener { text ->
            viewModel.updateContent(text?.toString() ?: "")
        }

        if (args.noteId != -1L && savedInstanceState == null) {
            viewModel.loadNoteForEdit(args.noteId)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.toolbar.title = if (uiState.isEditMode) "Edit Note" else "New Note"
                    binding.btnDeleteNote.visibility = if (uiState.isEditMode) View.VISIBLE else View.GONE

                    if (binding.etNoteTitle.text?.toString() != uiState.title) {
                        binding.etNoteTitle.setText(uiState.title)
                    }
                    if (binding.etNoteContent.text?.toString() != uiState.content) {
                        binding.etNoteContent.setText(uiState.content)
                    }

                    if (uiState.isNoteSaved) {
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}