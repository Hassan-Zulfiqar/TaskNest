package com.hassan.tasknest.presentation.addedittask

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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

    private var _binding: AddEditTaskBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditTaskViewModel by viewModel()
    private val args: AddEditTaskBottomSheetArgs by navArgs()

    private val dateDisplayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeDisplayFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AddEditTaskBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.taskId != -1L && savedInstanceState == null) {
            viewModel.loadTaskForEdit(args.taskId)
        }

        setupListeners()

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
            viewModel.updateReminderEnabled(isChecked)
        }

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSaveTask.setOnClickListener { viewModel.saveTask() }
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
            viewModel.updateReminderEnabled(isChecked)
        }

        binding.btnSaveTask.isEnabled = uiState.isSaveEnabled

        if (uiState.isTaskSaved) dismiss()
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select due date")
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
            val millis = picker.hour * 3600000L + picker.minute * 60000L
            viewModel.updateDueTime(millis)
        }
        picker.show(parentFragmentManager, "timePicker")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
