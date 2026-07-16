package com.hassan.tasknest.presentation.addeditnotes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.hassan.tasknest.R
import com.hassan.tasknest.databinding.FragmentAddEditNoteBinding
import com.hassan.tasknest.voice.VoskDictationController
import com.hassan.tasknest.voice.VoskModelManager
import com.hassan.tasknest.voice.VoskModelState
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays the add/edit note form. */
class AddEditNoteFragment : Fragment() {

    private var _binding: FragmentAddEditNoteBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AddEditNoteViewModel by viewModel()
    private val args: AddEditNoteFragmentArgs by navArgs()

    private val voskModelManager: VoskModelManager by inject()

    private var dictationController: VoskDictationController? = null
    private var isRecording: Boolean = false
    private var baseContentText: String = ""
    private var initialTitle: String = ""
    private var initialContent: String = ""
    private var initialValuesCaptured: Boolean = false

    private val requestMicPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                prepareAndStartVoskDictation()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Microphone permission is needed for voice input",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

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

        binding.toolbar.setNavigationOnClickListener { handleBackNavigation() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        binding.btnSaveNote.setOnClickListener { viewModel.saveNote() }
        binding.btnDeleteNote.setOnClickListener {
            viewModel.deleteNote()
            findNavController().navigateUp()
        }
        binding.btnMicToggle.setOnClickListener { onMicToggleClicked() }
        updateMicButtonUi(false)

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
                    if (uiState.isEditMode && !initialValuesCaptured) {
                        initialTitle = uiState.title
                        initialContent = uiState.content
                        initialValuesCaptured = true
                    }

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
        dictationController?.stop()
        dictationController = null
        super.onDestroyView()
        _binding = null
    }

    private fun onMicToggleClicked() {
        if (isRecording) {
            stopVoskDictation()
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            prepareAndStartVoskDictation()
        } else {
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun stopVoskDictation() {
        dictationController?.stop()
        dictationController = null
        isRecording = false
        updateMicButtonUi(false)
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = binding.etNoteTitle.text?.toString() ?: ""
        val currentContent = binding.etNoteContent.text?.toString() ?: ""
        return currentTitle != initialTitle || currentContent != initialContent
    }

    private fun handleBackNavigation() {
        if (!hasUnsavedChanges()) {
            findNavController().navigateUp()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Unsaved changes")
            .setMessage("Do you want to save your changes before leaving?")
            .setPositiveButton("Save") { _, _ ->
                viewModel.saveNote()
            }
            .setNegativeButton("Discard") { _, _ ->
                findNavController().navigateUp()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun prepareAndStartVoskDictation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val shouldShowDownloadOverlay = voskModelManager.getLoadedModel() == null
            if (shouldShowDownloadOverlay) {
                binding.modelDownloadOverlay.visibility = View.VISIBLE
                binding.tvDownloadStatus.text = "Preparing speech model..."
            }
            val progressJob = launch {
                voskModelManager.modelState.collect { state ->
                    when (state) {
                        is VoskModelState.Downloading -> {
                            binding.progressModelDownload.progress = state.progressPercent
                            binding.tvDownloadStatus.text = "Downloading speech model... ${state.progressPercent}%"
                        }
                        VoskModelState.Unzipping -> {
                            binding.tvDownloadStatus.text = "Preparing speech model..."
                        }
                        else -> Unit
                    }
                }
            }

            val model = try {
                voskModelManager.ensureModelReady()
            } finally {
                progressJob.cancelAndJoin()
                binding.modelDownloadOverlay.visibility = View.GONE
            }

            if (model == null) {
                val modelState = voskModelManager.modelState.value
                val message = (modelState as? VoskModelState.Error)?.message
                    ?: "Speech recognition unavailable"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                return@launch
            }

            baseContentText = binding.etNoteContent.text?.toString() ?: ""
            dictationController = VoskDictationController(
                model = model,
                onPartialResult = { text ->
                    requireActivity().runOnUiThread { updateContentWithLiveText(text) }
                },
                onFinalResult = { text ->
                    requireActivity().runOnUiThread { finalizeUtterance(text) }
                },
                onError = { message ->
                    requireActivity().runOnUiThread { handleDictationError(message) }
                }
            )
            dictationController?.start()
            isRecording = true
            updateMicButtonUi(true)
        }
    }

    private fun updateContentWithLiveText(partialText: String) {
        val combinedText = listOf(baseContentText.trim(), partialText.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        binding.etNoteContent.setText(combinedText)
        binding.etNoteContent.setSelection(binding.etNoteContent.text?.length ?: 0)
        viewModel.updateContent(combinedText)
    }

    private fun finalizeUtterance(finalText: String) {
        if (finalText.isBlank()) {
            return
        }

        baseContentText = listOf(baseContentText.trim(), finalText.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        binding.etNoteContent.setText(baseContentText)
        binding.etNoteContent.setSelection(binding.etNoteContent.text?.length ?: 0)
        viewModel.updateContent(baseContentText)
    }

    private fun handleDictationError(message: String) {
        Toast.makeText(requireContext(), "Voice input encountered an error", Toast.LENGTH_SHORT).show()
        isRecording = false
        dictationController?.stop()
        dictationController = null
        updateMicButtonUi(false)
    }

    private fun updateMicButtonUi(active: Boolean) {
        binding.btnMicToggle.setImageResource(if (active) R.drawable.ic_stop else R.drawable.ic_mic)
        binding.btnMicToggle.imageTintList = ContextCompat.getColorStateList(
            requireContext(),
            R.color.white
        )
    }
}