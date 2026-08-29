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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.firefly.utils.ListUtils
import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.extra.ExtraConstants
import net.kdt.pojavlaunch.firefly.extra.ExtraCore
import net.kdt.pojavlaunch.firefly.multirt.MultiRTUtils
import net.kdt.pojavlaunch.firefly.multirt.RTSpinnerAdapter
import net.kdt.pojavlaunch.firefly.multirt.Runtime
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import net.kdt.pojavlaunch.firefly.version.PgwInstalledVersion
import net.kdt.pojavlaunch.firefly.version.PgwVersionConfig
import net.kdt.pojavlaunch.firefly.version.PgwVersionRepository
import net.kdt.pojavlaunch.firefly.version.VersionIsolationMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Edits launch options for one installed version as each value changes. */
class VersionSettingsPageFragment : Fragment() {
    companion object {
        private const val ARG_VERSION_ID = "version_id"

        fun newInstance(versionId: String) = VersionSettingsPageFragment().apply {
            arguments = Bundle().apply { putString(ARG_VERSION_ID, versionId) }
        }
    }

    private lateinit var versionId: String
    private lateinit var version: PgwInstalledVersion
    private lateinit var jvmArgs: EditText
    private lateinit var runtime: Spinner
    private lateinit var renderer: Spinner
    private lateinit var graphicsApi: Spinner
    private lateinit var isolation: Spinner
    private lateinit var control: TextView
    private lateinit var customPath: TextView
    private lateinit var modsCheck: CheckBox
    private lateinit var customPathButton: View
    private var rendererIds: List<String> = emptyList()
    private var selectorTarget: SelectorTarget? = null
    private var binding = false
    private var writeQueue: ExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        versionId = requireArguments().getString(ARG_VERSION_ID)
            ?: throw IllegalArgumentException("Missing installed version id")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_version_settings_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        jvmArgs = view.findViewById(R.id.version_settings_jvm_args)
        runtime = view.findViewById(R.id.version_settings_runtime)
        renderer = view.findViewById(R.id.version_settings_renderer)
        graphicsApi = view.findViewById(R.id.version_settings_graphics_api)
        isolation = view.findViewById(R.id.version_settings_isolation)
        control = view.findViewById(R.id.version_settings_control)
        customPath = view.findViewById(R.id.version_settings_path)
        modsCheck = view.findViewById(R.id.version_settings_mods_check)
        customPathButton = view.findViewById(R.id.version_settings_path_button)
        writeQueue = Executors.newSingleThreadExecutor()

        view.findViewById<View>(R.id.version_settings_control_button).setOnClickListener {
            openSelector(SelectorTarget.CONTROL)
        }
        customPathButton.setOnClickListener { openSelector(SelectorTarget.PATH) }
        installImmediateUpdates()
        load()
    }

    override fun onDestroyView() {
        writeQueue?.shutdown()
        writeQueue = null
        super.onDestroyView()
    }

    /** Places a barrier after all queued immediate saves so a directory rename cannot lose them. */
    fun flushPendingWrites(): Future<*>? = writeQueue?.submit { }

    override fun onResume() {
        super.onResume()
        val value = ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR) as? String ?: return
        when (selectorTarget) {
            SelectorTarget.CONTROL -> {
                control.text = value
                version.config.controlFile = value
                persist { it.controlFile = value }
            }
            SelectorTarget.PATH -> {
                customPath.text = value
                version.config.customGameDir = value
                persist { it.customGameDir = value }
            }
            null -> return
        }
        selectorTarget = null
    }

    private fun installImmediateUpdates() {
        jvmArgs.doAfterTextChanged { value ->
            if (!canPersist()) return@doAfterTextChanged
            val args = value?.toString().orEmpty().trim()
            version.config.jvmArgs = args
            persist { it.jvmArgs = args }
        }
        modsCheck.setOnCheckedChangeListener { _, checked ->
            if (!canPersist()) return@setOnCheckedChangeListener
            version.config.enableModsCheck = checked
            persist { it.enableModsCheck = checked }
        }
        runtime.onItemSelectedListener = SelectionListener {
            if (!canPersist()) return@SelectionListener
            val selected = runtime.selectedItem as? Runtime ?: return@SelectionListener
            val name = selected.name.takeUnless { it == "<Default>" }.orEmpty()
            version.config.runtimeName = name
            persist { it.runtimeName = name }
        }
        renderer.onItemSelectedListener = SelectionListener {
            if (!canPersist()) return@SelectionListener
            val selected = renderer.selectedItemPosition
            val name = rendererIds.getOrNull(selected).orEmpty()
            version.config.rendererName = name
            persist { it.rendererName = name }
        }
        graphicsApi.onItemSelectedListener = SelectionListener {
            if (!canPersist()) return@SelectionListener
            val value = arrayOf("", "default", "default_opengl", "vulkan")
                .getOrElse(graphicsApi.selectedItemPosition) { "" }
            version.config.graphicsApi = value
            persist { it.graphicsApi = value }
        }
        isolation.onItemSelectedListener = SelectionListener {
            if (!canPersist()) return@SelectionListener
            val mode = selectedIsolation()
            version.config.isolation = mode
            updatePathState()
            persist { it.isolation = mode }
        }
    }

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { PgwVersionRepository.get(versionId) }
            if (loaded == null) {
                Tools.backToMainMenu(requireActivity())
                return@launch
            }
            version = loaded
            bind(loaded)
        }
    }

    private fun bind(value: PgwInstalledVersion) {
        binding = true
        jvmArgs.setText(value.config.jvmArgs)
        modsCheck.isChecked = value.config.enableModsCheck

        val runtimes = MultiRTUtils.getRuntimes()
        runtime.adapter = RTSpinnerAdapter(requireContext(), runtimes)
        runtime.setSelection(runtimes.indexOf(Runtime(value.config.runtimeName)).takeIf { it >= 0 } ?: 0)

        val compatible = ListUtils.getCompatibleRenderers(requireContext())
        rendererIds = compatible.rendererIds
        renderer.adapter = ArrayAdapter(requireContext(), R.layout.item_version_settings_spinner,
            compatible.rendererDisplayNames.toMutableList().apply { add(getString(R.string.global_default)) })
        renderer.setSelection(rendererIds.indexOf(value.config.rendererName).takeIf { it >= 0 } ?: rendererIds.size)

        graphicsApi.adapter = ArrayAdapter(requireContext(), R.layout.item_version_settings_spinner, listOf(
            getString(R.string.global_default), getString(R.string.version_settings_graphics_default),
            "OpenGL", "Vulkan"
        ))
        graphicsApi.setSelection(when (value.config.graphicsApi) {
            "default" -> 1
            "default_opengl" -> 2
            "vulkan" -> 3
            else -> 0
        })

        isolation.adapter = ArrayAdapter(requireContext(), R.layout.item_version_settings_spinner, listOf(
            getString(R.string.version_settings_isolation_follow),
            getString(R.string.version_settings_isolation_enable),
            getString(R.string.version_settings_isolation_disable)
        ))
        isolation.setSelection(value.config.isolation.ordinal)
        control.text = value.config.controlFile
        customPath.text = value.config.customGameDir
        updatePathState()
        binding = false
    }

    private fun updatePathState() {
        if (!::version.isInitialized || !::isolation.isInitialized) return
        val mode = selectedIsolation()
        val isolated = mode == VersionIsolationMode.ENABLE ||
            (mode == VersionIsolationMode.FOLLOW_GLOBAL && LauncherPreferences.PREF_VERSION_ISOLATION)
        customPath.isEnabled = !isolated
        customPathButton.isEnabled = !isolated
        customPath.alpha = if (isolated) 0.65f else 1f
        customPathButton.alpha = if (isolated) 0.65f else 1f
        customPath.text = if (isolated) version.config.effectiveGameDirectory(versionId).absolutePath
        else version.config.customGameDir
    }

    private fun selectedIsolation(): VersionIsolationMode = VersionIsolationMode.entries[
        isolation.selectedItemPosition.coerceIn(0, VersionIsolationMode.entries.lastIndex)
    ]

    private fun openSelector(target: SelectorTarget) {
        if (target == SelectorTarget.PATH && !customPathButton.isEnabled) return
        selectorTarget = target
        val bundle = Bundle().apply {
            putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, target == SelectorTarget.PATH)
            putBoolean(FileSelectorFragment.BUNDLE_SHOW_FILE, target != SelectorTarget.PATH)
            putBoolean(FileSelectorFragment.BUNDLE_SHOW_FOLDER, true)
            putString(FileSelectorFragment.BUNDLE_ROOT_PATH,
                if (target == SelectorTarget.CONTROL) Tools.CTRLMAP_PATH else ProfilePathManager.getCurrentPath())
        }
        Tools.swapFragment(requireActivity(), FileSelectorFragment::class.java, FileSelectorFragment.TAG, bundle)
    }

    private fun canPersist(): Boolean = ::version.isInitialized && !binding

    private fun persist(change: (PgwVersionConfig) -> Unit) {
        val queue = writeQueue ?: return
        val id = versionId
        queue.execute {
            runCatching { PgwVersionRepository.updateConfig(id, change) }
                .onFailure { error ->
                    activity?.runOnUiThread {
                        if (isAdded) Tools.showError(requireContext(), error)
                    }
                }
        }
    }

    private enum class SelectorTarget { CONTROL, PATH }
}

private class SelectionListener(private val action: () -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action()
    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
