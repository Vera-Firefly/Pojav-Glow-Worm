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

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.supervisorScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.version.AddonSelection
import net.kdt.pojavlaunch.firefly.version.LoaderCatalog
import net.kdt.pojavlaunch.firefly.version.LoaderKind
import net.kdt.pojavlaunch.firefly.version.LoaderVersion
import net.kdt.pojavlaunch.firefly.version.MinecraftVersion
import net.kdt.pojavlaunch.firefly.version.ModrinthApiCatalog
import net.kdt.pojavlaunch.firefly.version.ModrinthApiVersion
import net.kdt.pojavlaunch.firefly.version.VersionInstallRules

internal class MinecraftVersionAdapter(
    private val typeLabel: (MinecraftVersion) -> String,
    private val onSelected: (MinecraftVersion) -> Unit
) : RecyclerView.Adapter<MinecraftVersionAdapter.Holder>() {
    private var items: List<MinecraftVersion> = emptyList()

    fun submit(next: List<MinecraftVersion>) {
        val previous = items
        items = next
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size
            override fun getNewListSize(): Int = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition].entry.id == next[newItemPosition].entry.id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition] == next[newItemPosition]
        }).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.view_version_catalog_item, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.version_card_id)
        private val meta: TextView = view.findViewById(R.id.version_card_meta)

        fun bind(item: MinecraftVersion) {
            title.text = item.entry.id
            meta.text = typeLabel(item)
            itemView.setOnClickListener { onSelected(item) }
        }
    }
}

internal enum class AddonCardType(val loaderKind: LoaderKind? = null) {
    OPTIFINE(LoaderKind.OPTIFINE),
    FORGE(LoaderKind.FORGE),
    NEOFORGE(LoaderKind.NEOFORGE),
    FABRIC(LoaderKind.FABRIC),
    FABRIC_API,
    QUILT(LoaderKind.QUILT),
    QUILTED_FABRIC_API
}

internal data class AddonOption(
    val id: String,
    val title: String,
    val summary: String = "",
    val loader: LoaderVersion? = null,
    val api: ModrinthApiVersion? = null
)

internal sealed interface AddonAvailability {
    data object Loading : AddonAvailability
    data object Unavailable : AddonAvailability
    data class Failure(val message: String) : AddonAvailability
    data class Ready(val options: List<AddonOption>) : AddonAvailability
}

internal data class AddonCardState(
    val type: AddonCardType,
    val availability: AddonAvailability = AddonAvailability.Loading
)

internal data class AddonPageState(
    val minecraftVersion: String = "",
    val cards: Map<AddonCardType, AddonCardState> = AddonCardType.entries.associateWith { AddonCardState(it) },
    val selection: AddonSelection = AddonSelection(),
    val expanded: AddonCardType? = null,
    val fabricApiCleared: Boolean = false,
    val quiltApiCleared: Boolean = false
)

/**
 * Keeps loader metadata and selections outside the fragment view so rotation does not restart
 * requests or discard an expanded card while the user is choosing a loader release.
 */
internal class VersionAddonViewModel : ViewModel() {
    private val _state = MutableStateFlow(AddonPageState())
    val state: StateFlow<AddonPageState> = _state.asStateFlow()

    fun initialize(minecraftVersion: String) {
        if (_state.value.minecraftVersion == minecraftVersion) return
        _state.value = AddonPageState(minecraftVersion = minecraftVersion)
        AddonCardType.entries.forEach(::reload)
    }

    fun reload(type: AddonCardType) {
        val minecraftVersion = _state.value.minecraftVersion
        if (minecraftVersion.isBlank()) return
        if (!supports(type, minecraftVersion)) {
            updateCard(type, AddonAvailability.Unavailable)
            return
        }
        updateCard(type, AddonAvailability.Loading)
        viewModelScope.launch {
            val result = runCatching { fetch(type, minecraftVersion) }
                .fold(
                    onSuccess = { values -> if (values.isEmpty()) AddonAvailability.Unavailable else AddonAvailability.Ready(values) },
                    onFailure = { AddonAvailability.Failure(it.localizedMessage ?: it.javaClass.simpleName) }
                )
            updateCard(type, result)
            autoSelectApi(type)
        }
    }

    fun setExpanded(type: AddonCardType) {
        val card = _state.value.cards.getValue(type)
        if (card.availability is AddonAvailability.Failure) {
            reload(type)
            return
        }
        if (card.availability !is AddonAvailability.Ready) return
        _state.update { it.copy(expanded = if (it.expanded == type) null else type) }
    }

    fun select(type: AddonCardType, option: AddonOption?) {
        val previous = _state.value
        val current = previous.selection
        var next = current
        var fabricCleared = previous.fabricApiCleared
        var quiltCleared = previous.quiltApiCleared
        when (type) {
            AddonCardType.OPTIFINE -> {
                val selected = option?.loader
                val retainedForge = current.forge?.takeIf { forge ->
                    selected == null || VersionInstallRules.isOptiFineCompatibleWithForge(selected, forge)
                }
                next = AddonSelection(forge = retainedForge, optiFine = selected)
            }
            AddonCardType.FORGE -> {
                val selected = option?.loader
                val retainedOptiFine = current.optiFine?.takeIf { optiFine ->
                    selected == null || VersionInstallRules.isOptiFineCompatibleWithForge(optiFine, selected)
                }
                next = AddonSelection(forge = selected, optiFine = retainedOptiFine)
            }
            AddonCardType.NEOFORGE -> next = AddonSelection(neoForge = option?.loader)
            AddonCardType.FABRIC -> {
                next = AddonSelection(fabric = option?.loader)
                fabricCleared = false
            }
            AddonCardType.QUILT -> {
                next = AddonSelection(quilt = option?.loader)
                quiltCleared = false
            }
            AddonCardType.FABRIC_API -> {
                next = current.copy(fabricApi = option?.api)
                fabricCleared = option == null
            }
            AddonCardType.QUILTED_FABRIC_API -> {
                next = current.copy(quiltedFabricApi = option?.api)
                quiltCleared = option == null
            }
        }
        _state.value = previous.copy(
            selection = next,
            fabricApiCleared = fabricCleared,
            quiltApiCleared = quiltCleared,
            expanded = null
        )
        autoSelectApi(AddonCardType.FABRIC_API)
        autoSelectApi(AddonCardType.QUILTED_FABRIC_API)
    }

    private fun autoSelectApi(type: AddonCardType) {
        _state.update { current ->
            val card = current.cards.getValue(type).availability as? AddonAvailability.Ready ?: return@update current
            when (type) {
                AddonCardType.FABRIC_API -> {
                    if (current.selection.fabric != null && current.selection.fabricApi == null && !current.fabricApiCleared) {
                        current.copy(selection = current.selection.copy(fabricApi = card.options.firstOrNull()?.api))
                    } else current
                }
                AddonCardType.QUILTED_FABRIC_API -> {
                    if (current.selection.quilt != null && current.selection.quiltedFabricApi == null && !current.quiltApiCleared) {
                        current.copy(selection = current.selection.copy(quiltedFabricApi = card.options.firstOrNull()?.api))
                    } else current
                }
                else -> current
            }
        }
    }

    private fun updateCard(type: AddonCardType, availability: AddonAvailability) {
        _state.update { current ->
            current.copy(cards = current.cards + (type to AddonCardState(type, availability)))
        }
    }

    private suspend fun fetch(type: AddonCardType, minecraftVersion: String): List<AddonOption> = supervisorScope {
        when (type) {
            AddonCardType.OPTIFINE -> LoaderCatalog.optifine(minecraftVersion).toOptions()
            AddonCardType.FORGE -> LoaderCatalog.forge(minecraftVersion).toOptions()
            AddonCardType.NEOFORGE -> LoaderCatalog.neoforge(minecraftVersion).toOptions()
            AddonCardType.FABRIC -> LoaderCatalog.fabric(minecraftVersion).toOptions()
            AddonCardType.QUILT -> LoaderCatalog.quilt(minecraftVersion).toOptions()
            AddonCardType.FABRIC_API -> ModrinthApiCatalog.fabricApi(minecraftVersion).toApiOptions()
            AddonCardType.QUILTED_FABRIC_API -> ModrinthApiCatalog.quiltedFabricApi(minecraftVersion).toApiOptions()
        }
    }

    private fun supports(type: AddonCardType, minecraftVersion: String): Boolean = when (type) {
        AddonCardType.NEOFORGE -> isAtLeast(minecraftVersion, 1, 20)
        AddonCardType.FABRIC,
        AddonCardType.FABRIC_API,
        AddonCardType.QUILT,
        AddonCardType.QUILTED_FABRIC_API -> isAtLeast(minecraftVersion, 1, 13, 2)
        else -> true
    }

    private fun isAtLeast(version: String, vararg minimum: Int): Boolean {
        val numbers = Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()
        if (numbers.isEmpty()) return false
        for (index in minimum.indices) {
            val current = numbers.getOrElse(index) { 0 }
            if (current != minimum[index]) return current > minimum[index]
        }
        return true
    }

    private fun List<LoaderVersion>.toOptions(): List<AddonOption> = map { loader ->
        AddonOption(
            id = loader.loaderVersion,
            title = loader.displayName,
            loader = loader
        )
    }

    private fun List<ModrinthApiVersion>.toApiOptions(): List<AddonOption> = map { api ->
        AddonOption(id = api.fileName, title = api.version, summary = api.fileName, api = api)
    }
}

internal class AddonCardAdapter(
    private val label: (AddonCardType) -> String,
    private val summary: (AddonAvailability, String?) -> String,
    private val clearLabel: String,
    private val onExpand: (AddonCardType) -> Unit,
    private val onSelect: (AddonCardType, AddonOption?) -> Unit
) : RecyclerView.Adapter<AddonCardAdapter.Holder>() {
    private var state = AddonPageState()

    fun submit(next: AddonPageState) {
        val previous = state
        state = next
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = AddonCardType.entries.size
            override fun getNewListSize(): Int = AddonCardType.entries.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                AddonCardType.entries[oldItemPosition] == AddonCardType.entries[newItemPosition]
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val type = AddonCardType.entries[oldItemPosition]
                return cardSignature(previous, type) == cardSignature(next, type)
            }
        }).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.view_version_addon_card, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(AddonCardType.entries[position])

    override fun getItemCount(): Int = AddonCardType.entries.size

    private fun selectedOption(page: AddonPageState, type: AddonCardType): String? = with(page.selection) {
        when (type) {
            AddonCardType.OPTIFINE -> optiFine?.loaderVersion
            AddonCardType.FORGE -> forge?.loaderVersion
            AddonCardType.NEOFORGE -> neoForge?.loaderVersion
            AddonCardType.FABRIC -> fabric?.loaderVersion
            AddonCardType.QUILT -> quilt?.loaderVersion
            AddonCardType.FABRIC_API -> fabricApi?.fileName
            AddonCardType.QUILTED_FABRIC_API -> quiltedFabricApi?.fileName
        }
    }

    private fun selectedDisplayName(page: AddonPageState, type: AddonCardType): String? = with(page.selection) {
        when (type) {
            AddonCardType.OPTIFINE -> optiFine?.loaderVersion
            AddonCardType.FORGE -> forge?.loaderVersion
            AddonCardType.NEOFORGE -> neoForge?.loaderVersion
            AddonCardType.FABRIC -> fabric?.loaderVersion
            AddonCardType.QUILT -> quilt?.loaderVersion
            AddonCardType.FABRIC_API -> fabricApi?.version
            AddonCardType.QUILTED_FABRIC_API -> quiltedFabricApi?.version
        }
    }

    private fun loaderIcon(type: AddonCardType): Int = when (type) {
        AddonCardType.OPTIFINE -> R.drawable.ic_optifine
        AddonCardType.FORGE -> R.drawable.ic_forge
        AddonCardType.NEOFORGE -> R.drawable.ic_neoforge
        AddonCardType.FABRIC,
        AddonCardType.FABRIC_API -> R.drawable.ic_fabric
        AddonCardType.QUILT,
        AddonCardType.QUILTED_FABRIC_API -> R.drawable.ic_quilt
    }

    private fun cardSignature(page: AddonPageState, type: AddonCardType): Any = listOf(
        page.cards.getValue(type),
        page.expanded == type,
        selectedOption(page, type)
    )

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: ViewGroup = view.findViewById(R.id.version_addon_card)
        private val header: View = view.findViewById(R.id.version_addon_card_header)
        private val loaderIcon: ImageView = view.findViewById(R.id.version_addon_icon)
        private val title: TextView = view.findViewById(R.id.version_addon_label)
        private val stateText: TextView = view.findViewById(R.id.version_addon_status)
        private val expandButton: ImageButton = view.findViewById(R.id.version_addon_expand)
        private val clearButton: ImageButton = view.findViewById(R.id.version_addon_clear)
        private val choices: RadioGroup = view.findViewById(R.id.version_addon_choices)
        private var boundType: AddonCardType? = null
        private var wasExpanded: Boolean? = null

        fun bind(type: AddonCardType) {
            val model = state.cards.getValue(type)
            title.text = label(type)
            val selected = selectedOption(type)
            stateText.text = summary(model.availability, selectedDisplayName(state, type))
            loaderIcon.setImageResource(loaderIcon(type))
            card.setBackgroundResource(
                if (selected != null) R.drawable.bg_version_addon_card_selected
                else R.drawable.bg_version_addon_card
            )
            val expanded = state.expanded == type
            val expansionChanged = boundType == type && wasExpanded != null && wasExpanded != expanded
            animateArrow(type, expanded)
            val canExpand = model.availability is AddonAvailability.Ready || model.availability is AddonAvailability.Failure
            header.isEnabled = canExpand
            expandButton.isEnabled = canExpand
            expandButton.alpha = if (canExpand) 1f else 0.45f
            expandButton.contentDescription = itemView.context.getString(
                if (expanded) R.string.version_addon_collapse else R.string.version_addon_expand,
                label(type)
            )
            clearButton.isVisible = selected != null
            if (expansionChanged && card.isLaidOut) {
                TransitionManager.beginDelayedTransition(
                    card,
                    AutoTransition().apply { duration = 180L }
                )
            }
            choices.removeAllViews()
            choices.isVisible = expanded && model.availability is AddonAvailability.Ready
            if (choices.isVisible) {
                if (type == AddonCardType.FABRIC_API || type == AddonCardType.QUILTED_FABRIC_API) {
                    addChoice(type, null, clearLabel, selected == null)
                }
                (model.availability as AddonAvailability.Ready).options.forEach { option ->
                    addChoice(type, option, option.title, option.id == selected)
                }
            }
            header.setOnClickListener { onExpand(type) }
            expandButton.setOnClickListener { onExpand(type) }
            clearButton.setOnClickListener { onSelect(type, null) }
        }

        private fun animateArrow(type: AddonCardType, expanded: Boolean) {
            val targetRotation = if (expanded) 180f else 0f
            val shouldAnimate = boundType == type && wasExpanded != null && wasExpanded != expanded
            expandButton.animate().cancel()
            if (shouldAnimate) {
                expandButton.animate()
                    .rotation(targetRotation)
                    .setDuration(180L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            } else {
                expandButton.rotation = targetRotation
            }
            boundType = type
            wasExpanded = expanded
        }

        private fun addChoice(type: AddonCardType, option: AddonOption?, text: String, checked: Boolean) {
            val radio = RadioButton(choices.context).apply {
                layoutParams = RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                this.text = if (option?.summary.isNullOrBlank()) text else "$text\n${option!!.summary}"
                setTextColor(context.getColor(R.color.primary_text))
                setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen._11ssp)
                )
                buttonTintList = AppCompatResources.getColorStateList(
                    context,
                    R.color.version_filter_tint
                )
                isChecked = checked
                setPadding(4, 8, 4, 8)
                setOnClickListener { onSelect(type, option) }
            }
            choices.addView(radio)
        }

        private fun selectedOption(type: AddonCardType): String? = selectedOption(state, type)
    }
}
