package com.hassan.tasknest.presentation.taskdetail

import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.hassan.tasknest.R
import com.hassan.tasknest.data.local.entity.Priority
import com.hassan.tasknest.databinding.FragmentTaskDetailBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Displays a read-only view of a single task with options to edit, delete, or toggle completion. */
class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskDetailViewModel by viewModel()
    private val args: TaskDetailFragmentArgs by navArgs()

    private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            viewModel.loadTask(args.taskId)
        }

        binding.toolbar.inflateMenu(R.menu.menu_task_detail)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    findNavController().navigate(
                        TaskDetailFragmentDirections.actionTaskDetailFragmentToAddEditTaskBottomSheet(
                            taskId = args.taskId
                        )
                    )
                    true
                }
                R.id.action_delete -> {
                    viewModel.deleteTask()
                    true
                }
                else -> false
            }
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.cbTaskComplete.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleTaskCompletion()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    if (!uiState.isLoading && (uiState.task == null || uiState.isTaskDeleted)) {
                        findNavController().navigateUp()
                        return@collect
                    }

                    val task = uiState.task ?: return@collect

                    binding.tvTaskTitle.text = task.title
                    if (task.isCompleted) {
                        binding.tvTaskTitle.paintFlags =
                            binding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        binding.tvTaskTitle.paintFlags =
                            binding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }

                    binding.cbTaskComplete.setOnCheckedChangeListener(null)
                    if (binding.cbTaskComplete.isChecked != task.isCompleted) {
                        binding.cbTaskComplete.isChecked = task.isCompleted
                    }
                    binding.cbTaskComplete.setOnCheckedChangeListener { _, _ ->
                        viewModel.toggleTaskCompletion()
                    }

                    val (priorityLabel, priorityColorRes, priorityBgColorRes) = when (task.priority) {
                        Priority.HIGH -> Triple("High Priority", R.color.priority_high, R.color.priority_high_bg)
                        Priority.MEDIUM -> Triple("Medium Priority", R.color.warning, R.color.priority_medium_bg)
                        Priority.LOW -> Triple("Low Priority", R.color.icon_tint, R.color.priority_low_bg)
                    }
                    val priorityColor = ContextCompat.getColor(requireContext(), priorityColorRes)
                    binding.chipPriority.text = priorityLabel
                    binding.chipPriority.setTextColor(priorityColor)
                    binding.chipPriority.chipIconTint = ColorStateList.valueOf(priorityColor)
                    binding.chipPriority.chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), priorityBgColorRes)
                    )

                    binding.tvDateTimeValue.text = task.dueDate?.let {
                        dateTimeFormat.format(Date(it))
                    } ?: ""

                    binding.tvDescriptionValue.text = task.description ?: ""

                    binding.tvReminderValue.text = if (task.reminderTime != null) {
                        "Reminder set"
                    } else {
                        "No reminder"
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
