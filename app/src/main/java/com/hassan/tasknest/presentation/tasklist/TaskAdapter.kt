package com.hassan.tasknest.presentation.tasklist

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hassan.tasknest.R
import com.hassan.tasknest.data.local.entity.Priority
import com.hassan.tasknest.data.local.entity.Task
import com.hassan.tasknest.databinding.ItemTaskCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Binds a flat list of tasks to item_task_card views. */
class TaskAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onCheckboxToggle: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var tasks: List<Task> = emptyList()

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    fun submitTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    fun getTaskAt(position: Int): Task = tasks[position]

    override fun getItemCount(): Int = tasks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    inner class TaskViewHolder(private val binding: ItemTaskCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.tvTaskTitle.text = task.title
            binding.tvDueDate.text = task.dueDate?.let { dateFormat.format(Date(it)) } ?: ""

            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = task.isCompleted
            binding.checkbox.setOnCheckedChangeListener { _, _ -> onCheckboxToggle(task) }

            val priorityColorRes = when (task.priority) {
                Priority.HIGH -> R.color.priority_high
                Priority.MEDIUM -> R.color.warning
                Priority.LOW -> R.color.icon_tint
            }
            binding.priorityIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, priorityColorRes)
            )

            binding.root.setOnClickListener { onTaskClick(task) }
        }
    }
}
