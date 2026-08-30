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

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.utils.CropperUtils
import net.kdt.pojavlaunch.firefly.version.PgwInstalledVersion
import net.kdt.pojavlaunch.firefly.version.PgwVersionRepository
import net.kdt.pojavlaunch.firefly.version.VersionIconCache

/** Presents the identity and destructive actions for one installed version. */
class VersionSettingsOverviewFragment : Fragment(), CropperUtils.CropperListener {
    companion object {
        private const val ARG_VERSION_ID = "version_id"

        fun newInstance(versionId: String) = VersionSettingsOverviewFragment().apply {
            arguments = Bundle().apply { putString(ARG_VERSION_ID, versionId) }
        }
    }

    private lateinit var versionId: String
    private lateinit var version: PgwInstalledVersion
    private lateinit var icon: ImageView
    private lateinit var name: TextView
    private lateinit var summary: TextView
    private lateinit var resetIcon: View
    private val cropperLauncher: ActivityResultLauncher<*> = CropperUtils.registerCropper(this, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        versionId = requireArguments().getString(ARG_VERSION_ID)
            ?: throw IllegalArgumentException("Missing installed version id")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_version_settings_overview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        icon = view.findViewById(R.id.version_overview_icon)
        name = view.findViewById(R.id.version_overview_name)
        summary = view.findViewById(R.id.version_overview_summary)
        resetIcon = view.findViewById(R.id.version_overview_reset_icon)

        icon.setOnClickListener { CropperUtils.startCropper(cropperLauncher) }
        resetIcon.setOnClickListener { confirmResetIcon() }
        view.findViewById<View>(R.id.version_overview_edit_name).setOnClickListener { editName() }
        view.findViewById<View>(R.id.version_overview_edit_summary).setOnClickListener { editSummary() }
        view.findViewById<View>(R.id.version_overview_delete).setOnClickListener { confirmDelete() }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { PgwVersionRepository.get(versionId) }
            if (loaded == null) {
                Tools.backToMainMenu(requireActivity())
                return@launch
            }
            version = loaded
            icon.setImageDrawable(VersionIconCache.fetch(resources, loaded))
            name.text = loaded.id
            summary.text = loaded.config.summary.takeIf { it.isNotBlank() }
                ?: getString(R.string.version_overview_no_summary)
            resetIcon.visibility = if (PgwVersionRepository.iconFile(loaded.id).isFile) View.VISIBLE else View.GONE
        }
    }

    private fun editName() {
        val input = EditText(requireContext()).apply {
            setText(versionId)
            selectAll()
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.version_overview_edit_name)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> rename(input.text.toString().trim()) }
            .show()
    }

    private fun rename(newVersionId: String) {
        if (newVersionId == versionId) return
        val pendingWrites = (parentFragment as? VersionSettingsFragment)?.flushPendingConfigurationWrites()
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    pendingWrites?.get()
                    PgwVersionRepository.rename(versionId, newVersionId)
                }
            }
                .onSuccess {
                    VersionIconCache.drop(versionId)
                    versionId = newVersionId
                    (parentFragment as? VersionSettingsFragment)?.onVersionRenamed(newVersionId)
                }
                .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
        }
    }

    private fun editSummary() {
        val input = EditText(requireContext()).apply {
            setText(if (::version.isInitialized) version.config.summary else "")
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.version_overview_edit_summary)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> updateSummary(input.text.toString().trim()) }
            .show()
    }

    private fun updateSummary(value: String) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    PgwVersionRepository.updateConfig(versionId) { it.summary = value }
                }
            }.onSuccess { load() }
                .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
        }
    }

    private fun confirmResetIcon() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.version_overview_reset_icon)
            .setMessage(R.string.version_overview_reset_icon_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    runCatching { withContext(Dispatchers.IO) { PgwVersionRepository.clearCustomIcon(versionId) } }
                        .onSuccess {
                            VersionIconCache.drop(versionId)
                            load()
                        }
                        .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
                }
            }
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.version_manager_delete_title, versionId))
            .setMessage(R.string.version_manager_delete_instance_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.global_delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching { withContext(Dispatchers.IO) { PgwVersionRepository.delete(versionId) } }
                        .onSuccess { Tools.backToMainMenu(requireActivity()) }
                        .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
                }
            }
            .show()
    }

    override fun onCropped(contentBitmap: Bitmap) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { PgwVersionRepository.writeCustomIcon(versionId, contentBitmap) } }
                .onSuccess {
                    VersionIconCache.drop(versionId)
                    load()
                }
                .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
        }
    }

    override fun onFailed(exception: Exception) {
        if (isAdded) Tools.showError(requireContext(), exception)
    }
}
