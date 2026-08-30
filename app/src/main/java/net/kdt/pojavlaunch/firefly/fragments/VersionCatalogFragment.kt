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
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.MinecraftVersion
import net.kdt.pojavlaunch.firefly.version.MinecraftVersionType
import net.kdt.pojavlaunch.firefly.version.VersionCatalog

/** Lists remote Minecraft releases before the loader-selection installation step. */
class VersionCatalogFragment : Fragment(R.layout.fragment_version_catalog) {
    companion object { const val TAG = "VersionCatalogFragment" }

    private lateinit var adapter: MinecraftVersionAdapter
    private lateinit var search: EditText
    private lateinit var release: CheckBox
    private lateinit var snapshot: CheckBox
    private lateinit var aprilFools: CheckBox
    private lateinit var old: CheckBox
    private lateinit var loading: View
    private lateinit var empty: View
    private var entries: List<MinecraftVersion> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        search = view.findViewById(R.id.version_search)
        release = view.findViewById(R.id.version_filter_release)
        snapshot = view.findViewById(R.id.version_filter_snapshot)
        aprilFools = view.findViewById(R.id.version_filter_april_fools)
        old = view.findViewById(R.id.version_filter_old)
        loading = view.findViewById(R.id.version_loading)
        empty = view.findViewById(R.id.version_empty)

        adapter = MinecraftVersionAdapter(::typeLabel) { version ->
            Tools.swapFragment(
                requireActivity(),
                VersionAddonFragment::class.java,
                VersionAddonFragment.TAG,
                Bundle().apply { putString(VersionAddonFragment.ARG_MINECRAFT_VERSION, version.entry.id) }
            )
        }
        view.findViewById<RecyclerView>(R.id.version_online_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@VersionCatalogFragment.adapter
            setHasFixedSize(true)
        }
        view.findViewById<View>(R.id.version_refresh_button).setOnClickListener { loadVersions(true) }
        listOf(release, snapshot, aprilFools, old).forEach { filter ->
            filter.setOnCheckedChangeListener { _, _ -> updateList() }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = updateList()
            override fun afterTextChanged(value: Editable?) = Unit
        })
        loadVersions(false)
    }

    private fun loadVersions(force: Boolean) {
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { VersionCatalog.versions(force) } }
                .onSuccess {
                    entries = it
                    loading.visibility = View.GONE
                    updateList()
                }
                .onFailure {
                    loading.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                    Tools.showError(requireContext(), it)
                }
        }
    }

    private fun updateList() {
        if (!::adapter.isInitialized) return
        val query = search.text.toString().trim()
        val visible = entries.filter { version ->
            matchesType(version) && (query.isEmpty() || version.entry.id.contains(query, ignoreCase = true))
        }
        adapter.submit(visible)
        empty.visibility = if (entries.isNotEmpty() && visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun matchesType(version: MinecraftVersion): Boolean = when (version.type) {
        MinecraftVersionType.RELEASE -> release.isChecked
        MinecraftVersionType.SNAPSHOT -> snapshot.isChecked
        MinecraftVersionType.APRIL_FOOLS -> aprilFools.isChecked
        MinecraftVersionType.OLD_ALPHA, MinecraftVersionType.OLD_BETA -> old.isChecked
        MinecraftVersionType.UNKNOWN -> false
    }

    private fun typeLabel(version: MinecraftVersion): String = when (version.type) {
        MinecraftVersionType.RELEASE -> getString(R.string.version_filter_release)
        MinecraftVersionType.SNAPSHOT -> getString(R.string.version_filter_snapshot)
        MinecraftVersionType.APRIL_FOOLS -> getString(R.string.version_filter_april_fools)
        MinecraftVersionType.OLD_ALPHA, MinecraftVersionType.OLD_BETA -> getString(R.string.version_filter_old)
        MinecraftVersionType.UNKNOWN -> getString(R.string.version_manager_unknown)
    }
}
