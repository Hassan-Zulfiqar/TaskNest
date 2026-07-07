package com.hassan.tasknest.presentation.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hassan.tasknest.databinding.FragmentCategoryBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays the full list of categories with task counts, and FAB to add a new category. */
class CategoryFragment : Fragment() {

    private var _binding: FragmentCategoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CategoryViewModel by viewModel()
    private lateinit var categoryAdapter: CategoryAdapter
    private var deleteBlockedDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        categoryAdapter = CategoryAdapter(
            onEditClick = { item ->
                findNavController().navigate(
                    CategoryFragmentDirections.actionCategoryFragmentToAddEditCategoryBottomSheet(
                        categoryId = item.category.id
                    )
                )
            },
            onDeleteClick = { item -> viewModel.attemptDeleteCategory(item) }
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = categoryAdapter

        binding.fabAddCategory.setOnClickListener {
            findNavController().navigate(
                CategoryFragmentDirections.actionCategoryFragmentToAddEditCategoryBottomSheet(
                    categoryId = -1L
                )
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    categoryAdapter.submitCategories(uiState.categories)

                    if (uiState.categories.isNotEmpty()) {
                        binding.rvCategories.visibility = View.VISIBLE
                        binding.emptyStateContainer.visibility = View.GONE
                    } else {
                        binding.rvCategories.visibility = View.GONE
                        binding.emptyStateContainer.visibility = View.VISIBLE
                    }

                    val blocked = uiState.deleteBlockedCategory
                    if (blocked != null) {
                        if (deleteBlockedDialog?.isShowing != true) {
                            deleteBlockedDialog = AlertDialog.Builder(requireContext())
                                .setTitle("Delete Category")
                                .setMessage(
                                    "\"${blocked.category.name}\" has ${blocked.taskCount} tasks assigned. " +
                                        "Remove them from this category and delete it?"
                                )
                                .setPositiveButton("Remove & Delete") { _, _ ->
                                    viewModel.confirmDeleteAndReassign()
                                }
                                .setNegativeButton("Cancel") { _, _ ->
                                    viewModel.cancelDeleteCategory()
                                }
                                .show()
                        }
                    } else {
                        deleteBlockedDialog?.dismiss()
                        deleteBlockedDialog = null
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        deleteBlockedDialog?.dismiss()
        deleteBlockedDialog = null
        _binding = null
    }
}
