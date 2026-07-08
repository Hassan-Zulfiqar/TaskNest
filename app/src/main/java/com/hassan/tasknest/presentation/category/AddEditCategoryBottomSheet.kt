package com.hassan.tasknest.presentation.category

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

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
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swatches = listOf(
            Triple(binding.swatch1, binding.swatch1, "#E08585"),
            Triple(binding.swatch2, binding.swatch2, "#E3A96B"),
            Triple(binding.swatch3, binding.swatch3, "#7FBF9E"),
            Triple(binding.swatch4, binding.swatch4, "#7DA6D9"),
            Triple(binding.swatch5, binding.swatch5, "#A594D1"),
            Triple(binding.swatch6, binding.swatch6, "#E397B5"),
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

        binding.btnCloseDialog.setOnClickListener { dismiss() }

        binding.btnSaveCategory.setOnClickListener {
            val name = binding.etCategoryName.text?.toString() ?: ""
            if (args.categoryId == -1L) {
                viewModel.addCategory(name, selectedColorHex)
            } else {
                viewModel.updateCategory(args.categoryId, name, selectedColorHex)
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
