package com.hassan.tasknest.presentation.addeditnotes

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.style.AbsoluteSizeSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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

    // "Format while typing" state: when true/non-null, applies to characters typed with no active selection.
    private var isBoldActive: Boolean = false
    private var isItalicActive: Boolean = false
    private var isUnderlineActive: Boolean = false
    private var activeSizeSp: Int? = null
    private var activeColorHex: String? = null

    // Captured from onTextChanged so afterTextChanged can format exactly the newly-inserted range.
    private var pendingInsertStart: Int = 0
    private var pendingInsertCount: Int = 0

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
        binding.btnDeleteNote.setOnClickListener { showDeleteConfirmationDialog() }
        binding.btnMicToggle.setOnClickListener { onMicToggleClicked() }
        updateMicButtonUi(false)

        binding.etNoteTitle.addTextChangedListener { text ->
            viewModel.updateTitle(text?.toString() ?: "")
        }
        binding.etNoteContent.addTextChangedListener(
            onTextChanged = { _, start, _, count ->
                pendingInsertStart = start
                pendingInsertCount = count
            },
            afterTextChanged = { text ->
                viewModel.updateContent(text?.toString() ?: "")
                applyActiveFormattingToInsertedRange(pendingInsertStart, pendingInsertCount)
            }
        )

        binding.btnFormatBold.setOnClickListener {
            toggleCharacterSpan(
                StyleSpan::class.java,
                { StyleSpan(Typeface.BOLD) },
                onNoSelection = {
                    isBoldActive = !isBoldActive
                    updateFormatButtonTint(binding.btnFormatBold, isBoldActive)
                },
                matcher = { (it as StyleSpan).style == Typeface.BOLD }
            )
        }
        binding.btnFormatItalic.setOnClickListener {
            toggleCharacterSpan(
                StyleSpan::class.java,
                { StyleSpan(Typeface.ITALIC) },
                onNoSelection = {
                    isItalicActive = !isItalicActive
                    updateFormatButtonTint(binding.btnFormatItalic, isItalicActive)
                },
                matcher = { (it as StyleSpan).style == Typeface.ITALIC }
            )
        }
        binding.btnFormatUnderline.setOnClickListener {
            toggleCharacterSpan(
                UnderlineSpan::class.java,
                { UnderlineSpan() },
                onNoSelection = {
                    isUnderlineActive = !isUnderlineActive
                    updateFormatButtonTint(binding.btnFormatUnderline, isUnderlineActive)
                }
            )
        }
        binding.btnFormatBullet.setOnClickListener { toggleBulletFormat() }
        binding.btnFormatSize.setOnClickListener { showSizePicker() }
        binding.btnFormatColor.setOnClickListener { showColorPicker() }

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

        if (voskModelManager.modelState.value is VoskModelState.Downloading ||
            voskModelManager.modelState.value is VoskModelState.Unzipping
        ) {
            Toast.makeText(
                requireContext(),
                "Please wait while your speech model is preparing...",
                Toast.LENGTH_SHORT
            ).show()
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

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteNote()
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            val model = voskModelManager.ensureModelReady()

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

    private fun getEffectiveCharRange(): Pair<Int, Int>? {
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        if (start == end) {
            return null
        }
        return Pair(minOf(start, end), maxOf(start, end))
    }

    private fun hasSpanFullyApplied(
        spanClass: Class<*>,
        start: Int,
        end: Int,
        matcher: (Any) -> Boolean = { true }
    ): Boolean {
        val editable = binding.etNoteContent.text ?: return false
        val spans = editable.getSpans(start, end, spanClass)
        return spans.any { span ->
            matcher(span as Any) &&
                editable.getSpanStart(span) <= start &&
                editable.getSpanEnd(span) >= end
        }
    }

    private fun toggleCharacterSpan(
        spanClass: Class<*>,
        createSpan: () -> Any,
        onNoSelection: () -> Unit,
        matcher: (Any) -> Boolean = { true }
    ) {
        val range = getEffectiveCharRange()
        if (range == null) {
            // No selection: this tap toggles "format while typing" instead of editing existing text.
            onNoSelection()
            return
        }
        val (start, end) = range
        val editable = binding.etNoteContent.text ?: return

        if (hasSpanFullyApplied(spanClass, start, end, matcher)) {
            editable.getSpans(start, end, spanClass).forEach { span ->
                if (matcher(span as Any)) {
                    editable.removeSpan(span)
                }
            }
        } else {
            editable.setSpan(createSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Tints a formatting toolbar icon to reflect whether its "format while typing" state is active. */
    private fun updateFormatButtonTint(imageView: ImageView, active: Boolean) {
        val colorRes = if (active) R.color.brand_primary else R.color.icon_tint
        imageView.imageTintList = ContextCompat.getColorStateList(requireContext(), colorRes)
    }

    /**
     * Applies any active "format while typing" spans (Bold/Italic/Underline/Size/Color) to a
     * range of characters just inserted by the user. Setting a span is metadata-only and does not
     * itself fire the TextWatcher, so no re-entrancy guard is needed here.
     */
    private fun applyActiveFormattingToInsertedRange(start: Int, count: Int) {
        if (count <= 0) {
            return
        }
        val editable = binding.etNoteContent.text ?: return
        val end = start + count
        if (end > editable.length) {
            return
        }

        if (isBoldActive) {
            editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isItalicActive) {
            editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isUnderlineActive) {
            editable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val sizeSp = activeSizeSp
        if (sizeSp != null) {
            editable.setSpan(AbsoluteSizeSpan(sizeSp, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val colorHex = activeColorHex
        if (colorHex != null) {
            editable.setSpan(ForegroundColorSpan(Color.parseColor(colorHex)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun toggleBulletFormat() {
        val editable = binding.etNoteContent.text ?: return
        val selectionStart = minOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)
        val selectionEnd = maxOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)

        val rangeStart = findLineStart(editable, selectionStart)
        val rangeEnd = findLineEnd(editable, selectionEnd)

        var lineStart = rangeStart
        while (lineStart <= rangeEnd) {
            val lineEnd = findLineEnd(editable, lineStart)

            if (hasSpanFullyApplied(BulletSpan::class.java, lineStart, lineEnd)) {
                editable.getSpans(lineStart, lineEnd, BulletSpan::class.java).forEach { span ->
                    editable.removeSpan(span)
                }
            } else {
                editable.setSpan(BulletSpan(), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            lineStart = lineEnd + 1
        }
    }

    private fun findLineStart(editable: Editable, position: Int): Int {
        var index = position
        while (index > 0 && editable[index - 1] != '\n') {
            index--
        }
        return index
    }

    private fun findLineEnd(editable: Editable, position: Int): Int {
        var index = position
        while (index < editable.length && editable[index] != '\n') {
            index++
        }
        return index
    }

    private fun showSizePicker() {
        // With a selection: apply directly to it, unchanged from before. With no selection: this
        // picks the "format while typing" size instead, with an extra "None" option to clear it.
        val range = getEffectiveCharRange()

        val sizeOptions = listOf(
            "Small" to 14,
            "Normal" to 18,
            "Large" to 24,
            "Extra Large" to 32
        )
        val noneItemId = sizeOptions.size

        val popupMenu = PopupMenu(requireContext(), binding.btnFormatSize)
        sizeOptions.forEachIndexed { index, (label, _) ->
            popupMenu.menu.add(0, index, index, label)
        }
        if (range == null) {
            popupMenu.menu.add(0, noneItemId, noneItemId, "None")
        }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (range != null) {
                val (start, end) = range
                val sizeSp = sizeOptions[menuItem.itemId].second
                applySizeSpan(start, end, sizeSp)
            } else {
                activeSizeSp = if (menuItem.itemId == noneItemId) null else sizeOptions[menuItem.itemId].second
            }
            true
        }
        popupMenu.show()
    }

    private fun applySizeSpan(start: Int, end: Int, sizeSp: Int) {
        val editable = binding.etNoteContent.text ?: return
        editable.getSpans(start, end, AbsoluteSizeSpan::class.java).forEach { span ->
            editable.removeSpan(span)
        }
        editable.setSpan(AbsoluteSizeSpan(sizeSp, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showColorPicker() {
        // With a selection: apply directly to it, unchanged from before. With no selection: this
        // picks the "format while typing" color instead, with an extra "None" option to clear it.
        val range = getEffectiveCharRange()

        val colorOptions = listOf(
            "#E25C5C", "#E8912D", "#42BA76", "#4D88FF", "#A862EA", "#22B0A6"
        )

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle("Text color")
            .setNegativeButton("Cancel", null)

        if (range == null) {
            dialogBuilder.setNeutralButton("None") { _, _ -> activeColorHex = null }
        }

        val dialog = dialogBuilder.create()

        val swatchRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val swatchSize = (32 * resources.displayMetrics.density).toInt()
        val swatchMargin = (8 * resources.displayMetrics.density).toInt()

        colorOptions.forEach { hex ->
            val swatch = View(requireContext())
            val params = LinearLayout.LayoutParams(swatchSize, swatchSize)
            params.marginStart = swatchMargin
            params.marginEnd = swatchMargin
            swatch.layoutParams = params
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(Color.parseColor(hex))
            swatch.background = drawable
            swatch.setOnClickListener {
                if (range != null) {
                    val (start, end) = range
                    applyColorSpan(start, end, hex)
                } else {
                    activeColorHex = hex
                }
                dialog.dismiss()
            }
            swatchRow.addView(swatch)
        }

        dialog.setView(swatchRow)
        dialog.show()
    }

    private fun applyColorSpan(start: Int, end: Int, colorHex: String) {
        val editable = binding.etNoteContent.text ?: return
        editable.getSpans(start, end, ForegroundColorSpan::class.java).forEach { span ->
            editable.removeSpan(span)
        }
        editable.setSpan(ForegroundColorSpan(Color.parseColor(colorHex)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}