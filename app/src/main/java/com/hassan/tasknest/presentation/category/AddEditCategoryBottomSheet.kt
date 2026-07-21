package com.hassan.tasknest.presentation.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hassan.tasknest.data.repository.CategoryRepository
import com.hassan.tasknest.databinding.AddEditCategoryBottomSheetBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Bottom sheet for creating a new category or editing an existing one. */
class AddEditCategoryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: AddEditCategoryBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CategoryViewModel by viewModel()
    private val categoryRepository: CategoryRepository by inject()
    private val args: AddEditCategoryBottomSheetArgs by navArgs()

    private var selectedColorHex: String = "#E08585"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AddEditCategoryBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
//        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
//        val behavior = BottomSheetBehavior.from(bottomSheet)
//        behavior.state = BottomSheetBehavior.STATE_EXPANDED
//        behavior.skipCollapsed = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swatches = listOf(
            Triple(binding.swatch1, binding.checkSwatch1, "#E25C5C"),
            Triple(binding.swatch2, binding.checkSwatch2, "#E8912D"),
            Triple(binding.swatch3, binding.checkSwatch3, "#42BA76"),
            Triple(binding.swatch4, binding.checkSwatch4, "#4D88FF"),
            Triple(binding.swatch5, binding.checkSwatch5, "#A862EA"),
            Triple(binding.swatch6, binding.checkSwatch6, "#22B0A6"),
        )

        fun updateSwatchSelection(selectedHex: String) {
            swatches.forEach { (_, check, hex) ->
                check.visibility = if (hex == selectedHex) View.VISIBLE else View.GONE
            }
        }

        swatches.forEach { (swatch, _, hex) ->
            swatch.setOnClickListener {
                selectedColorHex = hex
                updateSwatchSelection(hex)
            }
        }

        if (args.categoryId != -1L && savedInstanceState == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val category = categoryRepository.getCategoryById(args.categoryId).first()
                if (category != null) {
                    binding.etCategoryName.setText(category.name)
                    selectedColorHex = category.colorHex
                    binding.tvCategoryDialogTitle.text = "Edit Category"
                    binding.btnSaveCategory.text = "Update Category"
                    updateSwatchSelection(category.colorHex)
                }
            }
        }

        binding.etCategoryName.addTextChangedListener {
            viewModel.clearDuplicateNameError()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.tilCategoryName.error = if (uiState.duplicateNameError) {
                        "A category with this name already exists"
                    } else {
                        null
                    }
                    if (uiState.isSaved) dismiss()
                }
            }
        }

        binding.btnCloseDialog.setOnClickListener { dismiss() }

        binding.btnSaveCategory.setOnClickListener {
            val name = binding.etCategoryName.text?.toString() ?: ""
            if (args.categoryId == -1L) {
                viewModel.addCategory(name, selectedColorHex)
            } else {
                viewModel.updateCategory(args.categoryId, name, selectedColorHex)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
