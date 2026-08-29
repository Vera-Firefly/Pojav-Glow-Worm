/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firefly.utils.ToastUtils.Toast
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.VersionInstallController
import net.kdt.pojavlaunch.firefly.version.VersionInstallProgress
import net.kdt.pojavlaunch.firefly.version.VersionInstallRequest
import net.kdt.pojavlaunch.firefly.version.VersionInstallRules
import net.kdt.pojavlaunch.firefly.version.VersionInstallStage
import net.kdt.pojavlaunch.firefly.version.VersionInstallStep
import net.kdt.pojavlaunch.firefly.version.VersionInstallStepProgress
import net.kdt.pojavlaunch.firefly.version.VersionInstallStepStatus
import net.kdt.pojavlaunch.firefly.version.VersionPaths

class VersionAddonFragment : Fragment(R.layout.fragment_version_addon) {
    companion object {
        const val TAG = "VersionAddonFragment"
        const val ARG_MINECRAFT_VERSION = "minecraft_version"
    }

    private val addonModel: VersionAddonViewModel by viewModels()
    private val installController: VersionInstallController by activityViewModels {
        VersionInstallController.Factory(requireContext())
    }

    private lateinit var installName: EditText
    private lateinit var installButton: View
    private lateinit var addonList: RecyclerView
    private lateinit var adapter: AddonCardAdapter
    private var nameEdited = false
    private var settingAutomaticName = false
    private var progressDialog: AlertDialog? = null
    private var progressViews: ProgressViews? = null

    private val minecraftVersion: String
        get() = requireArguments().getString(ARG_MINECRAFT_VERSION)
            ?: error("Minecraft version is required")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.version_addon_title).text = getString(R.string.version_addon_select)
        view.findViewById<TextView>(R.id.version_addon_subtitle).text = getString(R.string.version_addon_for, minecraftVersion)
        installName = view.findViewById(R.id.version_install_name)
        installButton = view.findViewById(R.id.version_install_button)
        adapter = AddonCardAdapter(
            label = ::addonLabel,
            summary = ::addonSummary,
            clearLabel = getString(R.string.version_addon_none),
            onExpand = addonModel::setExpanded,
            onSelect = addonModel::select
        )
        addonList = view.findViewById<RecyclerView>(R.id.version_addon_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@VersionAddonFragment.adapter
        }
        installName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                if (!settingAutomaticName) nameEdited = true
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })
        installButton.setOnClickListener { beginInstall() }
        addonModel.initialize(minecraftVersion)
        observeState()
    }

    override fun onDestroyView() {
        progressDialog?.dismiss()
        progressDialog = null
        progressViews = null
        super.onDestroyView()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    addonModel.state.collect { state ->
                        adapter.submit(state)
                        if (!nameEdited) setAutomaticName(VersionInstallRules.generatedName(minecraftVersion, state.selection))
                    }
                }
                launch {
                    installController.progress.collect(::renderInstallProgress)
                }
            }
        }
    }

    private fun beginInstall() {
        val name = installName.text.toString().trim()
        val request = VersionInstallRequest(
            minecraftVersion = minecraftVersion,
            targetVersionName = name,
            addons = addonModel.state.value.selection
        )
        runCatching { VersionInstallRules.validate(request) }
            .onFailure {
                Toast(requireContext(), R.string.version_install_invalid_name)
                return
            }
        if (VersionPaths.versionDirectory(name).exists()) {
            installName.error = getString(R.string.version_install_name_exists)
            installName.requestFocus()
            return
        }
        installController.install(request)
    }

    private fun setAutomaticName(value: String) {
        if (installName.text.toString() == value) return
        settingAutomaticName = true
        installName.setText(value)
        installName.setSelection(value.length)
        settingAutomaticName = false
    }

    private fun renderInstallProgress(progress: VersionInstallProgress) {
        when (progress.stage) {
            VersionInstallStage.IDLE -> Unit
            VersionInstallStage.COMPLETED -> {
                dismissProgress()
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.version_install_complete_title)
                    .setMessage(getString(R.string.version_install_complete_message, progress.installedVersion))
                    .setPositiveButton(android.R.string.ok) { _, _ -> installController.clearResult() }
                    .show()
            }
            VersionInstallStage.CANCELLED -> {
                dismissProgress()
                Toast(requireContext(), R.string.version_install_cancelled)
                installController.clearResult()
            }
            VersionInstallStage.FAILED -> {
                dismissProgress()
                Tools.showError(requireContext(), progress.error ?: IllegalStateException(getString(R.string.version_manager_install_failed)))
                installController.clearResult()
            }
            else -> {
                showProgress()
                updateProgressViews(progress)
            }
        }
    }

    private fun showProgress() {
        if (progressDialog?.isShowing == true) return
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_version_install_progress, null)
        val container = content.findViewById<LinearLayout>(R.id.version_install_steps)
        val inflater = LayoutInflater.from(requireContext())
        val rows = VersionInstallStep.entries.associateWith { step ->
            val row = inflater.inflate(R.layout.view_version_install_step, container, false)
            container.addView(row)
            InstallStepViews(
                icon = row.findViewById(R.id.version_install_step_icon),
                label = row.findViewById(R.id.version_install_step_label),
                bar = row.findViewById(R.id.version_install_step_bar),
                detail = row.findViewById(R.id.version_install_step_detail)
            )
        }
        progressViews = ProgressViews(rows)
        progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.version_install_progress_title)
            .setView(content)
            .setNegativeButton(R.string.version_install_cancel, null)
            .create()
            .apply {
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                setOnKeyListener { _, code, _ -> code == KeyEvent.KEYCODE_BACK }
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { installController.cancel() }
                }
                show()
            }
    }

    private fun updateProgressViews(progress: VersionInstallProgress) {
        val views = progressViews ?: return
        val states = progress.steps.associateBy(VersionInstallStepProgress::step)
        VersionInstallStep.entries.forEach { step ->
            val state = states[step] ?: VersionInstallStepProgress(step)
            val row = views.rows.getValue(step)
            row.label.text = stepLabel(step, progress)
            row.icon.setImageResource(stepIcon(state.status))
            row.bar.isVisible = state.status == VersionInstallStepStatus.ACTIVE
            row.detail.isVisible = state.status == VersionInstallStepStatus.ACTIVE
            if (state.status == VersionInstallStepStatus.ACTIVE) {
                if (row.activeAnimation == null) {
                    row.activeAnimation = RotateAnimation(
                        0f,
                        360f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f
                    ).apply {
                        duration = 900L
                        repeatCount = Animation.INFINITE
                        interpolator = LinearInterpolator()
                    }
                    row.icon.startAnimation(row.activeAnimation)
                }
                row.bar.isIndeterminate = state.indeterminate
                if (!state.indeterminate) {
                    val progressValue = if (state.totalFiles > 0) {
                        state.downloadedFiles * 100 / state.totalFiles
                    } else {
                        0
                    }
                    row.bar.setProgress(progressValue, true)
                }
                row.detail.text = stepDetail(state)
            } else {
                row.activeAnimation?.cancel()
                row.activeAnimation = null
                row.icon.clearAnimation()
            }
        }
    }

    private fun stepLabel(step: VersionInstallStep, progress: VersionInstallProgress): String = when (step) {
        VersionInstallStep.CLEAR_CACHE -> getString(R.string.version_install_step_clear_cache)
        VersionInstallStep.MINECRAFT -> getString(
            R.string.version_install_step_minecraft,
            progress.plan?.minecraftVersion.orEmpty()
        )
        VersionInstallStep.LOADER_MAIN_FILE -> progress.plan?.loaderName?.let {
            getString(R.string.version_install_step_loader_main, it)
        } ?: getString(R.string.version_install_step_loader_main_skipped)
        VersionInstallStep.LOADER_LIBRARIES -> progress.plan?.loaderName?.let {
            getString(R.string.version_install_step_loader_libraries, it)
        } ?: getString(R.string.version_install_step_loader_libraries_skipped)
        VersionInstallStep.API_MAIN_FILE -> progress.plan?.apiName?.let {
            getString(R.string.version_install_step_api_main, it)
        } ?: getString(R.string.version_install_step_api_main_skipped)
        VersionInstallStep.INSTALL_FILES -> getString(R.string.version_install_step_install_files)
    }

    private fun stepIcon(status: VersionInstallStepStatus): Int = when (status) {
        VersionInstallStepStatus.COMPLETED -> R.drawable.ic_install_step_done
        VersionInstallStepStatus.ACTIVE -> R.drawable.ic_install_step_active
        VersionInstallStepStatus.PENDING -> R.drawable.ic_install_step_pending
        VersionInstallStepStatus.SKIPPED -> R.drawable.ic_install_step_skipped
    }

    private fun stepDetail(state: VersionInstallStepProgress): String {
        if (state.indeterminate || state.totalFiles <= 0) {
            return getString(R.string.version_install_step_working)
        }
        val downloaded = Formatter.formatFileSize(requireContext(), state.downloadedBytes.coerceAtLeast(0L))
        val total = if (state.totalBytes > 0) {
            Formatter.formatFileSize(requireContext(), state.totalBytes)
        } else {
            getString(R.string.version_install_unknown_size)
        }
        return getString(
            R.string.version_install_step_detail,
            state.downloadedFiles,
            state.totalFiles,
            downloaded,
            total,
            Formatter.formatFileSize(requireContext(), state.speedBytesPerSecond.coerceAtLeast(0L))
        )
    }

    private fun dismissProgress() {
        progressDialog?.dismiss()
        progressDialog = null
        progressViews = null
    }

    private fun addonLabel(type: AddonCardType): String = when (type) {
        AddonCardType.OPTIFINE -> getString(R.string.version_manager_loader_optifine)
        AddonCardType.FORGE -> getString(R.string.version_manager_loader_forge)
        AddonCardType.NEOFORGE -> getString(R.string.version_manager_loader_neoforge)
        AddonCardType.FABRIC -> getString(R.string.version_manager_loader_fabric)
        AddonCardType.FABRIC_API -> getString(R.string.version_manager_loader_fabric_api)
        AddonCardType.QUILT -> getString(R.string.version_manager_loader_quilt)
        AddonCardType.QUILTED_FABRIC_API -> getString(R.string.version_manager_loader_quilted_fabric_api)
    }

    private fun addonSummary(availability: AddonAvailability, selected: String?): String = selected?.let {
        getString(R.string.version_addon_current_selection, it)
    } ?: when (availability) {
        AddonAvailability.Loading -> getString(R.string.version_addon_loading)
        AddonAvailability.Unavailable -> getString(R.string.version_addon_unavailable)
        is AddonAvailability.Failure -> getString(R.string.version_addon_retry)
        is AddonAvailability.Ready -> getString(R.string.version_addon_available)
    }

    private data class ProgressViews(
        val rows: Map<VersionInstallStep, InstallStepViews>
    )

    private data class InstallStepViews(
        val icon: ImageView,
        val label: TextView,
        val bar: ProgressBar,
        val detail: TextView,
        var activeAnimation: Animation? = null
    )
}
