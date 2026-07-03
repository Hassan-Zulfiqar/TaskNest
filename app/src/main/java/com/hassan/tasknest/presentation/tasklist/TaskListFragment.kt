package com.hassan.tasknest.presentation.tasklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hassan.tasknest.R
import com.hassan.tasknest.data.local.entity.Task
import com.hassan.tasknest.databinding.FragmentTaskListBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays the filterable, sortable task list with an empty state and FAB for adding tasks. */
class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskListViewModel by viewModel()

    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskAdapter = TaskAdapter(
            onTaskClick = { task -> onTaskClicked(task) },
            onCheckboxToggle = { task -> viewModel.toggleTaskCompletion(task) }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = taskAdapter

        binding.toolbar.inflateMenu(R.menu.menu_task_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> { onSearchClicked(); true }
                R.id.action_settings -> { onSettingsClicked(); true }
                else -> false
            }
        }

        binding.chipAll.setOnClickListener { viewModel.setFilter(TaskFilter.ALL) }
        binding.chipToday.setOnClickListener { viewModel.setFilter(TaskFilter.TODAY) }
        binding.chipUpcoming.setOnClickListener { viewModel.setFilter(TaskFilter.UPCOMING) }
        binding.chipCompleted.setOnClickListener { viewModel.setFilter(TaskFilter.COMPLETED) }

        binding.fabAddTask.setOnClickListener { onAddTaskClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    taskAdapter.submitTasks(uiState.tasks)

                    if (uiState.tasks.isNotEmpty()) {
                        binding.rvTasks.visibility = View.VISIBLE
                        binding.emptyStateContainer.visibility = View.GONE
                    } else {
                        binding.rvTasks.visibility = View.GONE
                        binding.emptyStateContainer.visibility = View.VISIBLE
                    }

                    val targetChip = when (uiState.activeFilter) {
                        TaskFilter.ALL -> binding.chipAll
                        TaskFilter.TODAY -> binding.chipToday
                        TaskFilter.UPCOMING -> binding.chipUpcoming
                        TaskFilter.COMPLETED -> binding.chipCompleted
                    }
                    if (!targetChip.isChecked) targetChip.isChecked = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onTaskClicked(task: Task) {
        // TODO: Navigate to TaskDetailFragment
    }

    private fun onSearchClicked() {
        // TODO: Open search UI
    }

    private fun onSettingsClicked() {
        // TODO: Navigate to SettingsFragment
    }

    private fun onAddTaskClicked() {
        // TODO: Open AddEditTaskBottomSheet
    }
}
