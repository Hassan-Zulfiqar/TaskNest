package com.hassan.tasknest.presentation.category

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hassan.tasknest.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onEditClick: (CategoryWithCount) -> Unit,
    private val onDeleteClick: (CategoryWithCount) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var items: List<CategoryWithCount> = emptyList()

    fun submitCategories(newItems: List<CategoryWithCount>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CategoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryWithCount) {
            binding.tvCategoryName.text = item.category.name
            binding.tvTaskCount.text = "${item.taskCount} tasks"
            binding.viewCategoryColor.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(item.category.colorHex))
            binding.btnEditCategory.setOnClickListener { onEditClick(item) }
            binding.btnDeleteCategory.setOnClickListener { onDeleteClick(item) }
        }
    }
}
