package com.hassan.tasknest.presentation.addeditnotes

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import com.hassan.tasknest.MainAppActivity
import com.hassan.tasknest.R
import com.hassan.tasknest.databinding.FragmentAddEditNoteBinding
import com.hassan.tasknest.presentation.addeditnotes.formatting.SpannableConverter
import com.hassan.tasknest.voice.VoskDictationController
import com.hassan.tasknest.voice.VoskModelManager
import com.hassan.tasknest.voice.VoskModelState
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
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

    // Range currently occupied by the in-progress (not-yet-finalized) dictation partial text.
    // -1 means no dictation session is active.
    private var livePartialStart: Int = -1
    private var livePartialEnd: Int = -1

    // Throttles onPartialResult UI updates, since Vosk can fire many times per second.
    private var lastPartialUpdateTime: Long = 0L

    // Which per-line list style (if any) continues onto a new line when the user presses Enter.
    // One of "BULLET", "DASH", "NUMBERED", "LETTERED", or null when no list is active.
    private var activeListStyle: String? = null

    private val numberedPrefixRegex = Regex("^\\d+\\. ")
    private val letteredPrefixRegex = Regex("^[a-z]\\. ")

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

        binding.btnSaveNote.setOnClickListener { performSave() }
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
                // Capture these before any nested edits (e.g. inserting a continued list prefix)
                // can overwrite the shared pendingInsertStart/pendingInsertCount fields below.
                val insertStart = pendingInsertStart
                val insertCount = pendingInsertCount
                viewModel.updateContent(text?.toString() ?: "")
                applyActiveFormattingToInsertedRange(insertStart, insertCount)
                continueListStyleAfterNewline(insertStart, insertCount)
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
        binding.btnFormatBullet.setOnClickListener { showBulletStylePicker() }
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

                        // One-time load: deserialize the persisted JSON content back into a real
                        // Spannable and set it directly, instead of the ongoing plain-text guarded
                        // comparison used below (which would strip all formatting via toString()).
                        val spannableResult = SpannableConverter.jsonToSpannable(uiState.content)
                        binding.etNoteContent.setText(spannableResult, TextView.BufferType.SPANNABLE)
                        // Capture the just-set Spannable's own JSON form (not the raw uiState.content
                        // string) so hasUnsavedChanges() compares apples-to-apples at exit time.
                        initialContent = SpannableConverter.spannableToJson(spannableResult)

                        initialValuesCaptured = true
                    }

                    binding.toolbar.title = if (uiState.isEditMode) "Edit Note" else "New Note"
                    binding.btnDeleteNote.visibility = if (uiState.isEditMode) View.VISIBLE else View.GONE

                    if (binding.etNoteTitle.text?.toString() != uiState.title) {
                        binding.etNoteTitle.setText(uiState.title)
                    }

                    if (uiState.isNoteSaved) {
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainAppActivity)?.hideBottomNav()
    }

    override fun onDestroyView() {
        dictationController?.stop()
        dictationController = null
        livePartialStart = -1
        livePartialEnd = -1
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
        livePartialStart = -1
        livePartialEnd = -1
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
        // Compare JSON-serialized content (not plain .toString()) against initialContent, which was
        // itself captured as JSON at load time, so formatting-only edits also count as unsaved.
        val currentTitle = binding.etNoteTitle.text?.toString() ?: ""
        return currentTitle != initialTitle || currentContentAsJson() != initialContent
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
                performSave()
            }
            .setNegativeButton("Discard") { _, _ ->
                findNavController().navigateUp()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    /** Converts the EditText's live Spanned content to JSON and hands it to the ViewModel to persist. */
    private fun performSave() {
        // saveNote() reads content from the ViewModel's own uiState, which the TextWatcher keeps in
        // sync as plain text for validation purposes; overwrite it with the JSON-serialized form
        // right before saving so the persisted content actually carries formatting.
        viewModel.updateContent(currentContentAsJson())
        viewModel.saveNote()
    }

    /** Serializes etNoteContent's current Spanned text (including all formatting) to JSON. */
    private fun currentContentAsJson(): String {
        val spanned = binding.etNoteContent.text as? Spanned ?: return ""
        return SpannableConverter.spannableToJson(spanned)
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

            val insertionPoint = binding.etNoteContent.text?.length ?: 0
            livePartialStart = insertionPoint
            livePartialEnd = insertionPoint
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

    /**
     * A partial/final callback can arrive from Vosk's capture coroutine after it was already
     * posted to the main thread, even once stop()/onDestroyView() have reset livePartialStart and
     * livePartialEnd to -1 (or the note's text has otherwise shrunk since they were captured). If
     * the current range is no longer valid, fall back to a fresh, zero-length range at the current
     * end of the text so replace() never receives out-of-bounds indices.
     */
    private fun ensureValidLivePartialRange(editable: Editable) {
        val isInvalid = livePartialStart == -1 ||
                livePartialEnd == -1 ||
                livePartialStart > editable.length ||
                livePartialEnd > editable.length
        if (isInvalid) {
            livePartialStart = editable.length
            livePartialEnd = editable.length
        }
    }

    private fun updateContentWithLiveText(partialText: String) {
        // Vosk can fire onPartialResult many times per second; skipping some updates only reduces
        // UI refresh frequency, since finalizeUtterance() always applies the complete, authoritative
        // transcript regardless of how many partials were skipped beforehand.
        if (System.currentTimeMillis() - lastPartialUpdateTime < 120) {
            return
        }

        val editable = binding.etNoteContent.text ?: return
        ensureValidLivePartialRange(editable)
        editable.replace(livePartialStart, livePartialEnd, partialText)
        livePartialEnd = livePartialStart + partialText.length
        clearFormattingSpansInRange(livePartialStart, livePartialEnd)
        applyActiveFormattingToRange(livePartialStart, livePartialEnd)
        binding.etNoteContent.setSelection(livePartialEnd)
        viewModel.updateContent(binding.etNoteContent.text?.toString() ?: "")
        lastPartialUpdateTime = System.currentTimeMillis()
    }

    /**
     * Removes any existing Bold/Italic/Underline/Size/Color spans from [start, end) before a fresh
     * span is applied to the same range. Without this, repeated partial-result updates on the same
     * live range would accumulate spans indefinitely, causing increasingly expensive relayout.
     */
    private fun clearFormattingSpansInRange(start: Int, end: Int) {
        val editable = binding.etNoteContent.text ?: return
        editable.getSpans(start, end, StyleSpan::class.java).forEach { editable.removeSpan(it) }
        editable.getSpans(start, end, UnderlineSpan::class.java).forEach { editable.removeSpan(it) }
        editable.getSpans(start, end, AbsoluteSizeSpan::class.java).forEach { editable.removeSpan(it) }
        editable.getSpans(start, end, ForegroundColorSpan::class.java).forEach { editable.removeSpan(it) }
    }

    private fun finalizeUtterance(finalText: String) {
        if (finalText.isBlank()) {
            return
        }

        val editable = binding.etNoteContent.text ?: return
        ensureValidLivePartialRange(editable)

        // Restore the space lost between segments when pausing/resuming dictation, unless we're
        // at the very start of the note or the preceding character is already whitespace.
        val precedingChar = livePartialStart.takeIf { it > 0 }?.let { editable[it - 1] }
        val textToInsert = if (precedingChar != null && !precedingChar.isWhitespace()) {
            " $finalText"
        } else {
            finalText
        }

        editable.replace(livePartialStart, livePartialEnd, textToInsert)
        livePartialEnd = livePartialStart + textToInsert.length
        applyActiveFormattingToRange(livePartialStart, livePartialEnd)
        // This utterance is done: the next partial update starts fresh from here.
        livePartialStart = livePartialEnd
        binding.etNoteContent.setSelection(livePartialEnd)
        viewModel.updateContent(binding.etNoteContent.text?.toString() ?: "")
    }

    private fun handleDictationError(message: String) {
        Toast.makeText(requireContext(), "Voice input encountered an error", Toast.LENGTH_SHORT).show()
        isRecording = false
        dictationController?.stop()
        dictationController = null
        livePartialStart = -1
        livePartialEnd = -1
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
        applyActiveFormattingToRange(start, end)
    }

    /** Applies whichever "format while typing" spans are currently active to [start, end). */
    private fun applyActiveFormattingToRange(start: Int, end: Int) {
        val editable = binding.etNoteContent.text ?: return

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

    /** Lets the user pick which per-line list marker style to apply to the affected lines. */
    private fun showBulletStylePicker() {
        val styles = arrayOf("Bullet (•)", "Dash (–)", "Numbered (1. 2. 3.)", "Lettered (a. b. c.)")
        AlertDialog.Builder(requireContext())
            .setTitle("List style")
            .setItems(styles) { _, which ->
                when (which) {
                    0 -> applyBulletPrefixStyle()
                    1 -> applyDashPrefixStyle()
                    2 -> applyLinePrefixStyle(lettered = false)
                    3 -> applyLinePrefixStyle(lettered = true)
                }
            }
            .show()
    }

    /** Removes a literal "– " prefix from the start of [lineStart, lineEnd) if present; returns the length delta. */
    private fun stripDashPrefixIfPresent(editable: Editable, lineStart: Int, lineEnd: Int): Int {
        val hasDashPrefix = lineEnd - lineStart >= 2 &&
                editable.subSequence(lineStart, lineStart + 2).toString() == "– "
        if (!hasDashPrefix) {
            return 0
        }
        editable.replace(lineStart, lineStart + 2, "")
        return -2
    }

    /** Removes a literal "• " prefix from the start of [lineStart, lineEnd) if present; returns the length delta. */
    private fun stripBulletPrefixIfPresent(editable: Editable, lineStart: Int, lineEnd: Int): Int {
        val hasBulletPrefix = lineEnd - lineStart >= 2 &&
                editable.subSequence(lineStart, lineStart + 2).toString() == "• "
        if (!hasBulletPrefix) {
            return 0
        }
        editable.replace(lineStart, lineStart + 2, "")
        return -2
    }

    /**
     * Bullet is implemented as a literal "• " text prefix per line, mirroring Dash's "– " exactly
     * (BulletSpan was removed: as a paragraph-level span it required an unreliable zero-length-span-
     * growth trick to work with a cursor-only line, and duplicated on Enter). Uses the same
     * no-selection-defaults-to-current-line boundary logic as Dash/Numbered/Lettered.
     */
    private fun applyBulletPrefixStyle() {
        val editable = binding.etNoteContent.text ?: return
        val selectionStart = minOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)
        val selectionEnd = maxOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)

        val rangeStart = findLineStart(editable, selectionStart)
        var rangeEnd = findLineEnd(editable, selectionEnd)

        var lineStart = rangeStart
        var turnedOnForLastLine = false
        while (lineStart <= rangeEnd) {
            var lineEnd = findLineEnd(editable, lineStart)
            // Switching from Dash: strip the literal dash prefix before inserting the bullet prefix.
            val dashDelta = stripDashPrefixIfPresent(editable, lineStart, lineEnd)
            lineEnd += dashDelta
            rangeEnd += dashDelta

            val hasBulletPrefix = lineEnd - lineStart >= 2 &&
                    editable.subSequence(lineStart, lineStart + 2).toString() == "• "

            if (hasBulletPrefix) {
                editable.replace(lineStart, lineStart + 2, "")
                lineEnd -= 2
                rangeEnd -= 2
                turnedOnForLastLine = false
            } else {
                editable.replace(lineStart, lineStart, "• ")
                lineEnd += 2
                rangeEnd += 2
                turnedOnForLastLine = true
            }

            lineStart = lineEnd + 1
        }

        activeListStyle = if (turnedOnForLastLine) "BULLET" else null
    }

    /** Dash is a literal "– " text prefix per line. Toggles off if that exact line already has the prefix. */
    private fun applyDashPrefixStyle() {
        val editable = binding.etNoteContent.text ?: return
        val selectionStart = minOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)
        val selectionEnd = maxOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)

        val rangeStart = findLineStart(editable, selectionStart)
        var rangeEnd = findLineEnd(editable, selectionEnd)

        var lineStart = rangeStart
        var turnedOnForLastLine = false
        while (lineStart <= rangeEnd) {
            var lineEnd = findLineEnd(editable, lineStart)
            // Switching from Bullet: strip the literal bullet prefix before inserting the dash prefix.
            val bulletDelta = stripBulletPrefixIfPresent(editable, lineStart, lineEnd)
            lineEnd += bulletDelta
            rangeEnd += bulletDelta

            val hasDashPrefix = lineEnd - lineStart >= 2 &&
                    editable.subSequence(lineStart, lineStart + 2).toString() == "– "

            if (hasDashPrefix) {
                editable.replace(lineStart, lineStart + 2, "")
                lineEnd -= 2
                rangeEnd -= 2
                turnedOnForLastLine = false
            } else {
                editable.replace(lineStart, lineStart, "– ")
                lineEnd += 2
                rangeEnd += 2
                turnedOnForLastLine = true
            }

            lineStart = lineEnd + 1
        }

        activeListStyle = if (turnedOnForLastLine) "DASH" else null
    }

    /**
     * Numbered/Lettered markers are plain text inserted once per affected line — they do not
     * auto-renumber if lines are later added/removed/reordered. Each line is checked independently
     * against this style's prefix pattern and toggled off if already present, matching Bullet/Dash.
     */
    private fun applyLinePrefixStyle(lettered: Boolean) {
        val editable = binding.etNoteContent.text ?: return
        val selectionStart = minOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)
        val selectionEnd = maxOf(binding.etNoteContent.selectionStart, binding.etNoteContent.selectionEnd)

        val rangeStart = findLineStart(editable, selectionStart)
        var rangeEnd = findLineEnd(editable, selectionEnd)
        val prefixRegex = if (lettered) letteredPrefixRegex else numberedPrefixRegex

        var lineStart = rangeStart
        var lineNumber = 1
        var turnedOnForLastLine = false
        while (lineStart <= rangeEnd) {
            var lineEnd = findLineEnd(editable, lineStart)

            // Switching from Bullet/Dash: clear their literal markers before inserting the new prefix.
            val bulletDelta = stripBulletPrefixIfPresent(editable, lineStart, lineEnd)
            lineEnd += bulletDelta
            rangeEnd += bulletDelta
            val dashDelta = stripDashPrefixIfPresent(editable, lineStart, lineEnd)
            lineEnd += dashDelta
            rangeEnd += dashDelta

            val lineText = editable.subSequence(lineStart, lineEnd).toString()
            val existingMatch = prefixRegex.find(lineText)

            if (existingMatch != null) {
                // This exact line already has a matching prefix: toggle it off instead of doubling up.
                val prefixLength = existingMatch.value.length
                editable.replace(lineStart, lineStart + prefixLength, "")
                lineEnd -= prefixLength
                rangeEnd -= prefixLength
                turnedOnForLastLine = false
            } else {
                // Lettered caps at 26 lines ("a." through "z."); beyond that, fall back to numbers.
                val prefix = if (lettered && lineNumber <= 26) {
                    "${'a' + (lineNumber - 1)}. "
                } else {
                    "$lineNumber. "
                }
                editable.replace(lineStart, lineStart, prefix)
                lineEnd += prefix.length
                rangeEnd += prefix.length
                turnedOnForLastLine = true
            }

            lineStart = lineEnd + 1
            lineNumber++
        }

        activeListStyle = if (turnedOnForLastLine) (if (lettered) "LETTERED" else "NUMBERED") else null
    }

    /**
     * When a lone "\n" is inserted (the user pressed Enter) and a list style is active, continue
     * that style onto the new line. If the line just completed contains ONLY the active style's
     * empty marker (no other text), remove that marker instead and exit list mode — the standard
     * "double-enter to exit a list" behavior.
     */
    private fun continueListStyleAfterNewline(insertStart: Int, insertCount: Int) {
        val style = activeListStyle ?: return
        if (insertCount != 1) return
        val editable = binding.etNoteContent.text ?: return
        if (insertStart !in 0 until editable.length || editable[insertStart] != '\n') return

        val previousLineStart = findLineStart(editable, insertStart)
        val previousLineEnd = insertStart
        val previousLineText = editable.subSequence(previousLineStart, previousLineEnd).toString()
        val newLineStart = insertStart + 1

        when (style) {
            "BULLET" -> {
                if (previousLineText == "• ") {
                    editable.replace(previousLineStart, previousLineEnd, "")
                    activeListStyle = null
                    return
                }
                editable.replace(newLineStart, newLineStart, "• ")
            }
            "DASH" -> {
                if (previousLineText == "– ") {
                    editable.replace(previousLineStart, previousLineEnd, "")
                    activeListStyle = null
                    return
                }
                editable.replace(newLineStart, newLineStart, "– ")
            }
            "NUMBERED" -> {
                if (numberedPrefixRegex.matches(previousLineText)) {
                    editable.replace(previousLineStart, previousLineEnd, "")
                    activeListStyle = null
                    return
                }
                val previousNumber = numberedPrefixRegex.find(previousLineText)
                    ?.value?.trim()?.removeSuffix(".")?.toIntOrNull()
                editable.replace(newLineStart, newLineStart, "${(previousNumber ?: 0) + 1}. ")
            }
            "LETTERED" -> {
                if (letteredPrefixRegex.matches(previousLineText)) {
                    editable.replace(previousLineStart, previousLineEnd, "")
                    activeListStyle = null
                    return
                }
                val previousLetter = letteredPrefixRegex.find(previousLineText)?.value?.getOrNull(0)
                val nextLetter = if (previousLetter != null && previousLetter < 'z') previousLetter + 1 else 'a'
                editable.replace(newLineStart, newLineStart, "$nextLetter. ")
            }
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
        // picks the "format while typing" size instead, with a "None" option to clear it.
        val range = getEffectiveCharRange()
        val editable = binding.etNoteContent.text

        val sizeValues = listOf(10, 12, 14, 16, 18, 20, 24, 28, 32, 36)
        val sizeLabels = sizeValues.map { "${it}pt" }.toTypedArray()

        // Pre-select whichever size is already applied at the cursor/selection start, if any.
        val checkPosition = range?.first ?: binding.etNoteContent.selectionStart
        val currentSize = if (editable != null && checkPosition in 0..editable.length) {
            editable.getSpans(checkPosition, checkPosition, AbsoluteSizeSpan::class.java).firstOrNull()?.size
        } else {
            null
        } ?: activeSizeSp
        val preselectedIndex = sizeValues.indexOf(currentSize)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle("Font size")
            .setSingleChoiceItems(sizeLabels, preselectedIndex) { dialog, which ->
                val selectedSize = sizeValues[which]
                if (range != null) {
                    val (start, end) = range
                    applySizeSpan(start, end, selectedSize)
                } else {
                    activeSizeSp = selectedSize
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)

        if (range == null) {
            dialogBuilder.setNeutralButton("None") { _, _ -> activeSizeSp = null }
        }

        dialogBuilder.show()
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
        // picks the "format while typing" color instead.
        val range = getEffectiveCharRange()

        // "Default" stays a quick, separate option alongside the full-spectrum picker rather than
        // being just another color inside it, since it means "clear the color span" not "apply one".
        AlertDialog.Builder(requireContext())
            .setTitle("Text color")
            .setItems(arrayOf("Default", "Choose Color...")) { _, which ->
                if (which == 0) {
                    applyChosenColor(range, null)
                } else {
                    showFullSpectrumColorPicker(range)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFullSpectrumColorPicker(range: Pair<Int, Int>?) {
        ColorPickerDialog.Builder(requireContext())
            .setTitle("Choose Color")
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)
            .setPositiveButton(
                "Select",
                ColorEnvelopeListener { colorEnvelope, _ ->
                    val hexCode = colorEnvelope.hexCode
                    val hex = if (hexCode.startsWith("#")) hexCode else "#$hexCode"
                    applyChosenColor(range, hex)
                }
            )
            .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
            .show()
    }

    // hex == null means "Default": remove any color instead of applying a new one.
    private fun applyChosenColor(range: Pair<Int, Int>?, hex: String?) {
        if (range != null) {
            val (start, end) = range
            if (hex != null) {
                applyColorSpan(start, end, hex)
            } else {
                removeColorSpan(start, end)
            }
        } else {
            activeColorHex = hex
        }
    }

    private fun applyColorSpan(start: Int, end: Int, colorHex: String) {
        val editable = binding.etNoteContent.text ?: return
        editable.getSpans(start, end, ForegroundColorSpan::class.java).forEach { span ->
            editable.removeSpan(span)
        }
        editable.setSpan(ForegroundColorSpan(Color.parseColor(colorHex)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** "Default": genuinely removes any ForegroundColorSpan from the range, reverting to the natural text color. */
    private fun removeColorSpan(start: Int, end: Int) {
        val editable = binding.etNoteContent.text ?: return
        editable.getSpans(start, end, ForegroundColorSpan::class.java).forEach { span ->
            editable.removeSpan(span)
        }
    }
}