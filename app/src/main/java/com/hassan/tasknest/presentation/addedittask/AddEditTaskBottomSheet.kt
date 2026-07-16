package com.hassan.tasknest.presentation.addedittask

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import android.view.ContextThemeWrapper
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hassan.tasknest.R
import com.hassan.tasknest.data.local.entity.Category
import com.hassan.tasknest.data.local.entity.Priority
import com.hassan.tasknest.databinding.AddEditTaskBottomSheetBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Bottom sheet for creating a new task (taskId == -1L) or editing an existing one. */
class AddEditTaskBottomSheet : BottomSheetDialogFragment() {

    private enum class VoiceInputTarget { TITLE, DESCRIPTION }

    private var _binding: AddEditTaskBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditTaskViewModel by viewModel()
    private val args: AddEditTaskBottomSheetArgs by navArgs()

    private val dateDisplayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeDisplayFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }

    private var pendingVoiceInputTarget: VoiceInputTarget? = null

    private val requestMicPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchSpeechRecognizer()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Microphone permission is needed for voice input",
                    Toast.LENGTH_SHORT
                ).show()
                pendingVoiceInputTarget = null
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    requireContext(),
                    "Notifications permission is needed for reminders to appear",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (text != null) {
                    handleRecognizedText(text)
                }
            }
            pendingVoiceInputTarget = null
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.isFitToContents = false
                behavior.expandedOffset = resources.getDimensionPixelSize(R.dimen.bottom_sheet_top_offset)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AddEditTaskBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.taskId != -1L && savedInstanceState == null) {
            viewModel.loadTaskForEdit(args.taskId)
        }

        setupListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categories.collect { categories -> populateCategoryChips(categories) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState -> renderState(uiState) }
            }
        }
    }

    private fun setupListeners() {
        binding.etTaskTitle.addTextChangedListener { text ->
            viewModel.updateTitle(text?.toString() ?: "")
        }

        binding.etDescription.addTextChangedListener { text ->
            viewModel.updateDescription(text?.toString() ?: "")
        }

        binding.etDueDate.setOnClickListener { showDatePicker() }
        binding.etDueTime.setOnClickListener { showTimePicker() }

        binding.chipPriorityLow.setOnClickListener { viewModel.updatePriority(Priority.LOW) }
        binding.chipPriorityMedium.setOnClickListener { viewModel.updatePriority(Priority.MEDIUM) }
        binding.chipPriorityHigh.setOnClickListener { viewModel.updatePriority(Priority.HIGH) }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            handleReminderToggleChange(isChecked)
        }

        binding.tilTaskTitle.setEndIconOnClickListener {
            onMicButtonClicked(VoiceInputTarget.TITLE)
        }
        binding.tilDescription.setEndIconOnClickListener {
            onMicButtonClicked(VoiceInputTarget.DESCRIPTION)
        }

        binding.chipAddCategory.setOnClickListener {
            dismiss()
            findNavController().navigate(
                AddEditTaskBottomSheetDirections.actionAddEditTaskBottomSheetToCategoryFragment()
            )
        }

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSaveTask.setOnClickListener { viewModel.saveTask() }
    }

    private fun onMicButtonClicked(target: VoiceInputTarget) {
        pendingVoiceInputTarget = target
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchSpeechRecognizer()
        } else {
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleReminderToggleChange(isChecked: Boolean) {
        if (isChecked) {
            val dueDateMillis = viewModel.uiState.value.dueDateMillis
            if (dueDateMillis == null) {
                Toast.makeText(
                    requireContext(),
                    "Please select a due date and time before setting a reminder",
                    Toast.LENGTH_SHORT
                ).show()

                binding.switchReminder.setOnCheckedChangeListener(null)
                binding.switchReminder.isChecked = false
                binding.switchReminder.setOnCheckedChangeListener { _, newIsChecked ->
                    handleReminderToggleChange(newIsChecked)
                }
                viewModel.updateReminderEnabled(false)
                return
            }
        }

        viewModel.updateReminderEnabled(isChecked)
        if (isChecked) {
            requestNotificationPermissionIfNeeded()
            val reminderLeadOptions = listOf(
                5 to "5 minutes before",
                15 to "15 minutes before",
                30 to "30 minutes before",
                60 to "1 hour before",
                1440 to "1 day before"
            )
            AlertDialog.Builder(requireContext())
                .setItems(reminderLeadOptions.map { it.second }.toTypedArray()) { _, which ->
                    viewModel.updateReminderLeadMinutes(reminderLeadOptions[which].first)
                }
                .show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            speechRecognizerLauncher.launch(intent)
        } else {
            Toast.makeText(requireContext(), "Voice input isn't supported on this device", Toast.LENGTH_SHORT).show()
            pendingVoiceInputTarget = null
        }
    }

    // This function is the single seam for upgrading to NLP-based field parsing later — replace the body only, callers do not need to change.
    private fun handleRecognizedText(text: String) {
        when (pendingVoiceInputTarget) {
            VoiceInputTarget.TITLE -> {
                binding.etTaskTitle.setText(text)
                viewModel.updateTitle(text)
            }
            VoiceInputTarget.DESCRIPTION -> {
                binding.etDescription.setText(text)
                viewModel.updateDescription(text)
            }
            null -> Unit
        }
    }

    private fun populateCategoryChips(categories: List<Category>) {
        val chipGroup = binding.chipGroupCategory
        val addChip = binding.chipAddCategory
        chipGroup.setOnCheckedStateChangeListener(null)
        chipGroup.removeAllViews()
        chipGroup.isSingleSelection = true
        categories.forEach { category ->
            val chip = Chip(
                ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_TaskNest_FilterChip),
                null,
                com.google.android.material.R.attr.chipStyle
            ).apply {
                id = View.generateViewId()
                text = category.name
                tag = category.id
                isCheckable = true
                isCheckedIconVisible = true
                checkedIconTint = ColorStateList.valueOf(Color.WHITE)
                chipStrokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(category.colorHex))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
            chipGroup.addView(chip)
        }
        chipGroup.addView(addChip)
        applyChipSelection(viewModel.uiState.value.categoryId)
    }

    private fun applyChipSelection(categoryId: Long?) {
        val chipGroup = binding.chipGroupCategory
        chipGroup.setOnCheckedStateChangeListener(null)
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            if (chip.tag is Long) {
                chip.isChecked = chip.tag == categoryId
            }
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                viewModel.updateCategory(null)
            } else {
                val chip = group.findViewById<Chip>(checkedIds.first())
                val catId = chip?.tag as? Long
                viewModel.updateCategory(catId)
            }
        }
    }

    private fun renderState(uiState: AddEditTaskUiState) {
        binding.tvHeaderTitle.text = if (uiState.isEditMode) "Edit Task" else "Add Task"
        binding.btnSaveTask.text = if (uiState.isEditMode) "Update Task" else "Save Task"

        if (binding.etTaskTitle.text.toString() != uiState.title) {
            binding.etTaskTitle.setText(uiState.title)
        }
        if (binding.etDescription.text.toString() != uiState.description) {
            binding.etDescription.setText(uiState.description)
        }

        binding.etDueDate.setText(
            uiState.dueDateMillis?.let { dateDisplayFormat.format(Date(it)) } ?: ""
        )
        binding.etDueTime.setText(
            uiState.dueTimeMillis?.let { timeDisplayFormat.format(Date(it)) } ?: ""
        )

        val priorityChip = when (uiState.priority) {
            Priority.LOW -> binding.chipPriorityLow
            Priority.MEDIUM -> binding.chipPriorityMedium
            Priority.HIGH -> binding.chipPriorityHigh
        }
        if (!priorityChip.isChecked) {
            binding.chipPriorityLow.isChecked = (priorityChip == binding.chipPriorityLow)
            binding.chipPriorityMedium.isChecked = (priorityChip == binding.chipPriorityMedium)
            binding.chipPriorityHigh.isChecked = (priorityChip == binding.chipPriorityHigh)
        }

        binding.switchReminder.setOnCheckedChangeListener(null)
        binding.switchReminder.isChecked = uiState.isReminderEnabled
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            handleReminderToggleChange(isChecked)
        }

        applyChipSelection(uiState.categoryId)

        binding.btnSaveTask.isEnabled = uiState.isSaveEnabled

        binding.tilTaskTitle.error = if (uiState.duplicateTitleError) {
            "A task with this title already exists"
        } else {
            null
        }

        if (uiState.isTaskSaved) dismiss()
    }

    private fun showDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select due date")
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            viewModel.updateDueDate(calendar.timeInMillis)
        }
        picker.show(parentFragmentManager, "datePicker")
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setTitleText("Select due time")
            .build()
        picker.addOnPositiveButtonClickListener {
            val pickedTimeMillis = picker.hour * 3600000L + picker.minute * 60000L
            val dueDateMillis = viewModel.uiState.value.dueDateMillis
            if (dueDateMillis != null) {
                val today = Calendar.getInstance()
                val selected = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
                val isToday = selected.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    selected.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                if (isToday && (dueDateMillis + pickedTimeMillis) <= System.currentTimeMillis()) {
                    Toast.makeText(requireContext(), "Please select a future time", Toast.LENGTH_SHORT).show()
                    return@addOnPositiveButtonClickListener
                }
            }
            viewModel.updateDueTime(pickedTimeMillis)
        }
        picker.show(parentFragmentManager, "timePicker")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
