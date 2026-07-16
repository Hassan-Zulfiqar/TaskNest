package com.hassan.tasknest.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.hassan.tasknest.R
import com.hassan.tasknest.databinding.FragmentSettingsBinding
import com.hassan.tasknest.presentation.tasklist.TaskFilter
import com.hassan.tasknest.presentation.tasklist.TaskSortOrder
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Displays app preferences: theme, default sort/filter, notification permission, and legal links. */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var isApplyingThemeState: Boolean = false

    private val viewModel: SettingsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Static row labels — set once, never re-set inside the collector
        binding.rowDefaultSort.tvRowLabel.text = "Default Sort"
        binding.rowDefaultFilter.tvRowLabel.text = "Default Filter"
        binding.rowNotificationPermission.tvRowLabel.text = "Notification Permission"
        binding.rowMicPermission.tvRowLabel.text = "Microphone Permission"
        binding.rowPrivacyPolicy.tvRowLabel.text = "Privacy Policy"
        binding.rowTermsConditions.tvRowLabel.text = "Terms & Conditions"
        binding.rowAppVersion.tvRowLabel.text = "App Version"

        // App version row — non-interactive
        binding.rowAppVersion.tvRowValue.text =
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: ""
        binding.rowAppVersion.ivRowChevron.visibility = View.GONE
        binding.rowAppVersion.root.isClickable = false
        binding.rowAppVersion.root.isFocusable = false

        // Theme toggle
        binding.toggleGroupTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isApplyingThemeState) return@addOnButtonCheckedListener
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnThemeSystem -> "SYSTEM"
                R.id.btnThemeLight -> "LIGHT"
                R.id.btnThemeDark -> "DARK"
                else -> return@addOnButtonCheckedListener
            }
            viewModel.setThemeMode(mode)
        }

        // Default sort row
        binding.rowDefaultSort.root.setOnClickListener { showSortOrderDialog() }

        // Default filter row
        binding.rowDefaultFilter.root.setOnClickListener { showFilterDialog() }

        // Notification permission row — open system notification settings
        binding.rowNotificationPermission.root.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }

        // Microphone permission row — open the app's general system settings page
        binding.rowMicPermission.root.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().packageName, null)
            )
            startActivity(intent)
        }

        // Privacy policy row
        binding.rowPrivacyPolicy.root.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy")))
        }

        // Terms & Conditions row
        binding.rowTermsConditions.root.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms")))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    val targetThemeId = when (uiState.themeMode) {
                        "LIGHT" -> R.id.btnThemeLight
                        "DARK" -> R.id.btnThemeDark
                        else -> R.id.btnThemeSystem
                    }
                    if (binding.toggleGroupTheme.checkedButtonId != targetThemeId) {
                        isApplyingThemeState = true
                        binding.toggleGroupTheme.check(targetThemeId)
                        isApplyingThemeState = false
                    }

                    binding.rowDefaultSort.tvRowValue.text = sortOrderLabel(uiState.defaultSortOrder)
                    binding.rowDefaultFilter.tvRowValue.text = filterLabel(uiState.defaultFilter)

                    val notifGranted = uiState.isNotificationPermissionGranted
                    binding.rowNotificationPermission.tvRowValue.text =
                        if (notifGranted) "Enabled" else "Disabled"
                    binding.rowNotificationPermission.tvRowValue.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (notifGranted) R.color.status_success else R.color.status_error
                        )
                    )

                    val micGranted = uiState.isMicPermissionGranted
                    binding.rowMicPermission.tvRowValue.text =
                        if (micGranted) "Enabled" else "Disabled"
                    binding.rowMicPermission.tvRowValue.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (micGranted) R.color.status_success else R.color.status_error
                        )
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionStatuses()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showSortOrderDialog() {
        val options = TaskSortOrder.values()
        val labels = options.map { sortOrderLabel(it) }.toTypedArray()
        val current = viewModel.uiState.value.defaultSortOrder
        val checkedItem = options.indexOf(current)
        AlertDialog.Builder(requireContext())
            .setTitle("Default Sort Order")
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                viewModel.setDefaultSortOrder(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFilterDialog() {
        val options = TaskFilter.values()
        val labels = options.map { filterLabel(it) }.toTypedArray()
        val current = viewModel.uiState.value.defaultFilter
        val checkedItem = options.indexOf(current)
        AlertDialog.Builder(requireContext())
            .setTitle("Default Filter")
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                viewModel.setDefaultFilter(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sortOrderLabel(sortOrder: TaskSortOrder): String = when (sortOrder) {
        TaskSortOrder.DUE_DATE -> "Due Date"
        TaskSortOrder.PRIORITY -> "Priority"
        TaskSortOrder.CREATED_DATE -> "Created Date"
        TaskSortOrder.MANUAL -> "Manual"
    }

    private fun filterLabel(filter: TaskFilter): String = when (filter) {
        TaskFilter.ALL -> "All Tasks"
        TaskFilter.TODAY -> "Today"
        TaskFilter.UPCOMING -> "Upcoming"
        TaskFilter.COMPLETED -> "Completed"
    }
}
