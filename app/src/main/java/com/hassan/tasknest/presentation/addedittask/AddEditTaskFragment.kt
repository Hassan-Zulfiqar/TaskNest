package com.hassan.tasknest.presentation.addedittask

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.hassan.tasknest.R
import com.hassan.tasknest.data.local.entity.Category
import com.hassan.tasknest.data.local.entity.Priority
import com.hassan.tasknest.databinding.FragmentAddEditTaskBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fragment for creating a new task (taskId == -1L) or editing an existing one. */
class AddEditTaskFragment : Fragment() {

    private enum class VoiceInputTarget { TITLE, DESCRIPTION }

    private var _binding: FragmentAddEditTaskBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AddEditTaskViewModel by viewModel()
    private val args: AddEditTaskFragmentArgs by navArgs()

    private val dateDisplayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeDisplayFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }

    private var pendingVoiceInputTarget: VoiceInputTarget? = null
    private var leadTimeConfirmed = false

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

    private val reminderLeadOptions = listOf(
        5 to "5 minutes before",
        15 to "15 minutes before",
        30 to "30 minutes before",
        60 to "1 hour before",
        1440 to "1 day before"
    )

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.taskId != -1L && savedInstanceState == null) {
            viewModel.loadTaskForEdit(args.taskId)
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

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
            findNavController().navigate(
                AddEditTaskFragmentDirections.actionAddEditTaskFragmentToAddEditCategoryBottomSheet()
            )
        }

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
            val dueTimeMillis = viewModel.uiState.value.dueTimeMillis
            if (dueDateMillis == null || dueTimeMillis == null) {
                Toast.makeText(
                    requireContext(),
                    "Please select both a due date and time before setting a reminder",
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

            val combinedDueMillis = dueDateMillis + dueTimeMillis
            val remainingMillis = combinedDueMillis - System.currentTimeMillis()
            val validOptions = reminderLeadOptions.filter { (leadMinutes, _) ->
                leadMinutes * 60_000L < remainingMillis
            }

            if (validOptions.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Not enough time remaining before the due date to set a reminder",
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

            viewModel.updateReminderEnabled(isChecked)
            requestNotificationPermissionIfNeeded()
            showReminderLeadTimeDialog(validOptions)
            return
        }

        viewModel.updateReminderEnabled(isChecked)
    }

    private fun showReminderLeadTimeDialog(validOptions: List<Pair<Int, String>>) {
        val currentLeadMinutes = viewModel.uiState.value.reminderLeadMinutes
        val checkedItem = validOptions.indexOfFirst { it.first == currentLeadMinutes }
            .takeIf { it >= 0 } ?: 0
        var selectedLeadMinutes = validOptions.first().first
        var checkedPosition = checkedItem
        leadTimeConfirmed = false

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Reminder Lead Time")
            .setSingleChoiceItems(
                validOptions.map { it.second }.toTypedArray(),
                checkedItem
            ) { _, which ->
                checkedPosition = which
                selectedLeadMinutes = validOptions[which].first
                leadTimeConfirmed = true
            }
            .setPositiveButton("OK") { _, _ ->
                leadTimeConfirmed = true
                selectedLeadMinutes = validOptions.getOrNull(checkedPosition)?.first ?: selectedLeadMinutes
                viewModel.updateReminderLeadMinutes(selectedLeadMinutes)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnDismissListener {
            if (!leadTimeConfirmed) {
                binding.switchReminder.setOnCheckedChangeListener(null)
                binding.switchReminder.isChecked = false
                binding.switchReminder.setOnCheckedChangeListener { _, newIsChecked ->
                    handleReminderToggleChange(newIsChecked)
                }
                viewModel.updateReminderEnabled(false)
            }
        }

        dialog.show()
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
        binding.toolbar.title = if (uiState.isEditMode) "Edit Task" else "Add Task"
        binding.btnSaveTask.text = if (uiState.isEditMode) "Update Task" else "Save Task"

        if (binding.etTaskTitle.text.toString() != uiState.title)
        {
            binding.etTaskTitle.setText(uiState.title)
        }
        if (binding.etDescription.text.toString() != uiState.description)
        {
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

        binding.tvReminderLeadTime.visibility = if (uiState.isReminderEnabled) View.VISIBLE else View.GONE
        binding.tvReminderLeadTime.text = if (uiState.isReminderEnabled) {
            when (uiState.reminderLeadMinutes) {
                5 -> "(5 minutes before)"
                15 -> "(15 minutes before)"
                30 -> "(30 minutes before)"
                60 -> "(1 hour before)"
                1440 -> "(1 day before)"
                else -> "(Reminder set)"
            }
        } else {
            ""
        }

        applyChipSelection(uiState.categoryId)

        binding.btnSaveTask.isEnabled = uiState.isSaveEnabled
        val saveButtonBackgroundColor = if (uiState.isSaveEnabled) {
            ContextCompat.getColor(requireContext(), R.color.brand_primary)
        } else {
            ContextCompat.getColor(requireContext(), R.color.divider)
        }
        binding.btnSaveTask.backgroundTintList = ColorStateList.valueOf(saveButtonBackgroundColor)
        binding.btnSaveTask.setTextColor(
            if (uiState.isSaveEnabled) {
                ContextCompat.getColor(requireContext(), R.color.white)
            } else {
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            }
        )

        binding.tilTaskTitle.error = if (uiState.duplicateTitleError) {
            "A task with this title already exists"
        } else {
            null
        }

        if (uiState.isTaskSaved) findNavController().navigateUp()
    }

    private fun showDatePicker() {
        val currentDueDateMillis = viewModel.uiState.value.dueDateMillis
        val calendar = Calendar.getInstance().apply {
            if (currentDueDateMillis != null) {
                timeInMillis = currentDueDateMillis
            }
        }
        val initialYear = calendar.get(Calendar.YEAR)
        val initialMonth = calendar.get(Calendar.MONTH)
        val initialDay = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            R.style.ThemeOverlay_TaskNest_DatePickerSpinner,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.updateDueDate(selectedCalendar.timeInMillis)
            },
            initialYear,
            initialMonth,
            initialDay
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.datePicker.calendarViewShown = false
        datePickerDialog.setOnShowListener {
            datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        }

        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val currentDueTimeMillis = viewModel.uiState.value.dueTimeMillis
        val calendar = Calendar.getInstance().apply {
            if (currentDueTimeMillis != null) {
                timeInMillis = currentDueTimeMillis
            }
        }
        val initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            R.style.ThemeOverlay_TaskNest_TimePickerSpinner,
            { _, hourOfDay, minute ->
                val pickedTimeMillis = hourOfDay * 3600000L + minute * 60000L
                val dueDateMillis = viewModel.uiState.value.dueDateMillis
                if (dueDateMillis != null) {
                    val today = Calendar.getInstance()
                    val selected = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
                    val isToday = selected.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            selected.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                    if (isToday && (dueDateMillis + pickedTimeMillis) <= System.currentTimeMillis()) {
                        Toast.makeText(requireContext(), "Please select a future time", Toast.LENGTH_SHORT).show()
                        return@TimePickerDialog
                    }
                }
                viewModel.updateDueTime(pickedTimeMillis)
            },
            initialHour,
            initialMinute,
            false
        )
        timePickerDialog.setOnShowListener {
            timePickerDialog.getButton(TimePickerDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            timePickerDialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        }

        timePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
