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
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.LocalVersionKind
import net.kdt.pojavlaunch.firefly.version.PgwInstalledVersion
import net.kdt.pojavlaunch.firefly.version.PgwVersionRepository

/** Manages real version directories in the current game home. */
class VersionManagerFragment : Fragment(R.layout.fragment_version_manager) {
    companion object {
        const val TAG = "VersionManagerFragment"
        private const val ACTION_RENAME = 1
        private const val ACTION_COPY_METADATA = 2
        private const val ACTION_COPY_ALL = 3
        private const val ACTION_DELETE = 4
        private const val PIN_ANIMATION_DURATION_MS = 160L
    }

    private lateinit var adapter: InstalledVersionAdapter
    private var category: VersionCategory = VersionCategory.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = InstalledVersionAdapter(::selectVersion, ::openSettings, ::togglePin, ::openActions)
        view.findViewById<RecyclerView>(R.id.installed_version_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@VersionManagerFragment.adapter
            itemAnimator = null
        }
        view.findViewById<View>(R.id.installed_version_refresh).setOnClickListener { reload() }
        view.findViewById<View>(R.id.installed_version_add).setOnClickListener { openCatalog() }
        view.findViewById<View>(R.id.installed_version_clean).setOnClickListener { confirmClean() }
        view.findViewById<View>(R.id.installed_filter_all).setOnClickListener { switchCategory(VersionCategory.ALL) }
        view.findViewById<View>(R.id.installed_filter_vanilla).setOnClickListener { switchCategory(VersionCategory.VANILLA) }
        view.findViewById<View>(R.id.installed_filter_loader).setOnClickListener { switchCategory(VersionCategory.LOADER) }
        updateCategorySelection(view, category)
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) reload()
    }

    private fun reload() {
        view?.findViewById<View>(R.id.installed_version_loading)?.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { PgwVersionRepository.scan() } }
                .onSuccess { versions ->
                    val selected = withContext(Dispatchers.IO) { PgwVersionRepository.current()?.id }
                    val visible = when (category) {
                        VersionCategory.ALL -> versions
                        VersionCategory.VANILLA -> versions.filter { it.local.kind == LocalVersionKind.VANILLA }
                        VersionCategory.LOADER -> versions.filter { it.local.kind != LocalVersionKind.VANILLA }
                    }
                    adapter.submit(visible, selected)
                    view?.findViewById<View>(R.id.installed_version_loading)?.visibility = View.GONE
                    view?.findViewById<View>(R.id.installed_version_empty)?.visibility =
                        if (visible.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { error ->
                    view?.findViewById<View>(R.id.installed_version_loading)?.visibility = View.GONE
                    if (isAdded) Tools.showError(requireContext(), error)
                }
        }
    }

    private fun switchCategory(value: VersionCategory) {
        category = value
        view?.let { updateCategorySelection(it, value) }
        reload()
    }

    private fun updateCategorySelection(root: View, value: VersionCategory) {
        setFilterState(root.findViewById(R.id.installed_filter_all), value == VersionCategory.ALL)
        setFilterState(root.findViewById(R.id.installed_filter_vanilla), value == VersionCategory.VANILLA)
        setFilterState(root.findViewById(R.id.installed_filter_loader), value == VersionCategory.LOADER)
    }

    private fun setFilterState(button: View, selected: Boolean) {
        button.isSelected = selected
        button.setBackgroundResource(
            if (selected) R.drawable.background_line_selected
            else R.drawable.bg_version_filter_unselected
        )
        button.refreshDrawableState()
    }

    private fun openCatalog() {
        Tools.swapFragment(requireActivity(), VersionCatalogFragment::class.java, VersionCatalogFragment.TAG, null)
    }

    private fun selectVersion(version: PgwInstalledVersion) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { PgwVersionRepository.select(version.id) } }
                .onSuccess { reload() }
                .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
        }
    }

    private fun openSettings(version: PgwInstalledVersion) {
        Tools.swapFragment(
            requireActivity(), VersionSettingsFragment::class.java, VersionSettingsFragment.TAG,
            Bundle().apply { putString(VersionSettingsFragment.ARG_VERSION_ID, version.id) }
        )
    }

    private fun togglePin(version: PgwInstalledVersion, animated: Boolean) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    PgwVersionRepository.updateConfig(version.id) { it.pinned = !it.pinned }
                }
            }.onSuccess {
                reloadAfterPinAnimation(animated)
            }.onFailure {
                reloadAfterPinAnimation(animated)
                if (isAdded) Tools.showError(requireContext(), it)
            }
        }
    }

    private fun reloadAfterPinAnimation(animated: Boolean) {
        if (!animated) {
            reload()
            return
        }
        view?.postDelayed({ if (isAdded) reload() }, PIN_ANIMATION_DURATION_MS) ?: reload()
    }

    private fun openActions(anchor: View, version: PgwInstalledVersion) {
        androidx.appcompat.widget.PopupMenu(requireContext(), anchor).apply {
            menu.add(0, ACTION_RENAME, 0, R.string.version_manager_rename)
            menu.add(0, ACTION_COPY_METADATA, 1, R.string.version_manager_copy_metadata)
            menu.add(0, ACTION_COPY_ALL, 2, R.string.version_manager_copy_all)
            menu.add(0, ACTION_DELETE, 3, R.string.version_manager_delete_version)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_RENAME -> promptName(version, R.string.version_manager_rename) { name -> rename(version, name) }
                    ACTION_COPY_METADATA -> promptName(version, R.string.version_manager_copy_metadata) { name -> copy(version, name, false) }
                    ACTION_COPY_ALL -> promptName(version, R.string.version_manager_copy_all) { name -> copy(version, name, true) }
                    ACTION_DELETE -> confirmDelete(version)
                }
                true
            }
            show()
        }
    }

    private fun promptName(version: PgwInstalledVersion, title: Int, onConfirm: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            setText(if (title == R.string.version_manager_rename) version.id else "${version.id}-copy")
            selectAll()
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm(input.text.toString().trim()) }
            .show()
    }

    private fun rename(version: PgwInstalledVersion, name: String) = runOperation {
        PgwVersionRepository.rename(version.id, name)
    }

    private fun copy(version: PgwInstalledVersion, name: String, allFiles: Boolean) = runOperation {
        PgwVersionRepository.copy(version.id, name, allFiles)
    }

    private fun confirmDelete(version: PgwInstalledVersion) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.version_manager_delete_title, version.id))
            .setMessage(R.string.version_manager_delete_instance_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.global_delete) { _, _ -> runOperation { PgwVersionRepository.delete(version.id) } }
            .show()
    }

    private fun confirmClean() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.version_manager_clean_assets)
            .setMessage(R.string.version_manager_clean_assets_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> runOperation { PgwVersionRepository.clearUnreferencedAssets() } }
            .show()
    }

    private fun runOperation(operation: () -> Any?) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { operation() } }
                .onSuccess { reload() }
                .onFailure { if (isAdded) Tools.showError(requireContext(), it) }
        }
    }

    private enum class VersionCategory { ALL, VANILLA, LOADER }

}
