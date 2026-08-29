package net.kdt.pojavlaunch.firefly.fragments

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.version.LocalVersionKind
import net.kdt.pojavlaunch.firefly.version.PgwInstalledVersion
import net.kdt.pojavlaunch.firefly.version.VersionIconCache

/** Binds locally scanned version directories without retaining stale profile entries. */
internal class InstalledVersionAdapter(
    private val onSelect: (PgwInstalledVersion) -> Unit,
    private val onSettings: (PgwInstalledVersion) -> Unit,
    private val onPin: (PgwInstalledVersion, Boolean) -> Unit,
    private val onMenu: (View, PgwInstalledVersion) -> Unit
) : RecyclerView.Adapter<InstalledVersionAdapter.Holder>() {
    private companion object {
        const val PIN_SCALE = 1.1f
        const val PIN_PHASE_DURATION_MS = 80L
        val PIN_INTERPOLATOR = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    }

    private var items: List<PgwInstalledVersion> = emptyList()
    private var selectedId: String? = null

    fun submit(values: List<PgwInstalledVersion>, selected: String?) {
        items = values
        selectedId = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.view_installed_version_item, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position], items[position].id == selectedId)

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.installed_version_icon)
        private val title: TextView = view.findViewById(R.id.installed_version_title)
        private val subtitle: TextView = view.findViewById(R.id.installed_version_subtitle)
        private val selected: TextView = view.findViewById(R.id.installed_version_selected)
        private val pin: ImageButton = view.findViewById(R.id.installed_version_pin)
        private val settings: ImageButton = view.findViewById(R.id.installed_version_settings)
        private val menu: ImageButton = view.findViewById(R.id.installed_version_menu)

        fun bind(version: PgwInstalledVersion, isSelected: Boolean) {
            pin.animate().cancel()
            pin.scaleX = 1f
            pin.scaleY = 1f
            pin.isEnabled = true
            icon.setImageDrawable(VersionIconCache.fetch(itemView.resources, version))
            title.text = version.config.summary.takeIf { it.isNotBlank() } ?: version.id
            val validity = if (version.valid) itemView.context.getString(R.string.version_manager_ready)
            else itemView.context.getString(R.string.version_manager_invalid)
            subtitle.text = "${kindLabel(version.local.kind)} · ${version.id} · $validity"
            selected.visibility = if (isSelected) View.VISIBLE else View.GONE
            pin.setImageResource(if (version.config.pinned) {
                R.drawable.ic_version_pin_selected
            } else {
                R.drawable.ic_version_pin
            })
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.bg_installed_version_card_selected
                else R.drawable.bg_installed_version_card
            )
            itemView.setOnClickListener { onSelect(version) }
            pin.setOnClickListener {
                pin.isEnabled = false
                onPin(version, animatePinPress())
            }
            settings.setOnClickListener { onSettings(version) }
            menu.setOnClickListener { onMenu(menu, version) }
        }

        private fun animatePinPress(): Boolean {
            pin.animate().cancel()
            pin.scaleX = 1f
            pin.scaleY = 1f
            if (!ValueAnimator.areAnimatorsEnabled()) return false
            pin.animate()
                .scaleX(PIN_SCALE)
                .scaleY(PIN_SCALE)
                .setDuration(PIN_PHASE_DURATION_MS)
                .setInterpolator(PIN_INTERPOLATOR)
                .withEndAction {
                    pin.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(PIN_PHASE_DURATION_MS)
                        .setInterpolator(PIN_INTERPOLATOR)
                        .start()
                }
                .start()
            return true
        }

        private fun kindLabel(kind: LocalVersionKind): String = when (kind) {
            LocalVersionKind.VANILLA -> itemView.context.getString(R.string.version_manager_kind_vanilla)
            LocalVersionKind.FORGE -> "Forge"
            LocalVersionKind.NEOFORGE -> "NeoForge"
            LocalVersionKind.FABRIC -> "Fabric"
            LocalVersionKind.QUILT -> "Quilt"
            LocalVersionKind.OPTIFINE -> "OptiFine"
            LocalVersionKind.CUSTOM -> itemView.context.getString(R.string.version_manager_kind_custom)
        }
    }
}
