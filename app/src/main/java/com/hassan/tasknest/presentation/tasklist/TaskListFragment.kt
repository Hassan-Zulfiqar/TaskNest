package com.hassan.tasknest.presentation.tasklist

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.hassan.tasknest.R
import com.hassan.tasknest.data.local.entity.Task
import com.hassan.tasknest.databinding.FragmentTaskListBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays the filterable, sortable task list with an empty state and FAB for adding tasks. */
class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var loadingJob: Job? = null

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
            onCheckboxToggle = { task ->
                val wasCompleted = task.isCompleted
                viewModel.toggleTaskCompletion(task)
                showCompletionToggleSnackbar(task, wasCompleted)
            }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = taskAdapter

        val swipeCallback = TaskSwipeCallback(
            onSwipeLeft = { position ->
                val task = taskAdapter.getTaskAt(position)
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteTask(task)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        taskAdapter.notifyItemChanged(position)
                    }
                    .show()
            },
            onSwipeRight = { position ->
                val task = taskAdapter.getTaskAt(position)
                val wasCompleted = task.isCompleted
                viewModel.toggleTaskCompletion(task)
                showCompletionToggleSnackbar(task, wasCompleted)
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvTasks)

        binding.toolbar.inflateMenu(R.menu.menu_task_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_categories -> { onCategoriesClicked(); true }
                R.id.action_settings -> { onSettingsClicked(); true }
                else -> false
            }
        }

        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
            override fun onQueryTextSubmit(query: String?): Boolean = true
        })
        searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(180, 255, 255, 255))
        }
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

        binding.chipAll.setOnClickListener { viewModel.setFilter(TaskFilter.ALL) }
        binding.chipToday.setOnClickListener { viewModel.setFilter(TaskFilter.TODAY) }
        binding.chipUpcoming.setOnClickListener { viewModel.setFilter(TaskFilter.UPCOMING) }
        binding.chipCompleted.setOnClickListener { viewModel.setFilter(TaskFilter.COMPLETED) }

        binding.fabAddTask.setOnClickListener { onAddTaskClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.filterScrollView.visibility = if (!uiState.isLoading && uiState.hasAnyTasks) View.VISIBLE else View.GONE

                    taskAdapter.submitTasks(uiState.tasks)
                    //binding.progressLoading.visibility = if (uiState.isLoading) View.VISIBLE else View.GONE
                    if (uiState.isLoading) {
                        if (loadingJob == null) {
                            loadingJob = viewLifecycleOwner.lifecycleScope.launch {
                                delay(150) // only show spinner if loading takes longer than this
                                binding.progressLoading.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        loadingJob?.cancel()
                        loadingJob = null
                        binding.progressLoading.visibility = View.GONE
                    }

                    if (uiState.isLoading) {
                        binding.rvTasks.visibility = View.GONE
                        binding.emptyStateContainer.visibility = View.GONE
                    } else {
                        if (uiState.tasks.isNotEmpty()) {
                            binding.rvTasks.visibility = View.VISIBLE
                            binding.emptyStateContainer.visibility = View.GONE
                        } else {
                            binding.rvTasks.visibility = View.GONE
                            binding.emptyStateContainer.visibility = View.VISIBLE
                        }
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

    private fun showCompletionToggleSnackbar(task: Task, wasCompleted: Boolean) {
        val message = if (wasCompleted) {
            "Task marked incomplete"
        } else {
            "Task marked complete"
        }

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                // task.copy(isCompleted = !wasCompleted) reconstructs a task object representing the
                // CURRENT (post-original-toggle) state, so that toggleTaskCompletion's internal flip
                // correctly reverts it back to wasCompleted, rather than operating on a stale pre-toggle object.
                viewModel.toggleTaskCompletion(task.copy(isCompleted = !wasCompleted))
            }
            .show()
    }

    private fun onTaskClicked(task: Task) {
        findNavController().navigate(
            TaskListFragmentDirections.actionTaskListFragmentToTaskDetailFragment(taskId = task.id)
        )
    }

    private fun onCategoriesClicked() {
        findNavController().navigate(
            TaskListFragmentDirections.actionTaskListFragmentToCategoryFragment()
        )
    }

    private fun onSettingsClicked() {
        findNavController().navigate(
            TaskListFragmentDirections.actionTaskListFragmentToSettingsFragment()
        )
    }

    private fun onAddTaskClicked() {
        findNavController().navigate(
            TaskListFragmentDirections.actionTaskListFragmentToAddEditTaskBottomSheet(taskId = -1L)
        )
    }
}
