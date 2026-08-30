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
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import net.kdt.pojavlaunch.firefly.R
import java.util.concurrent.Future

/** Hosts the pages used to edit one installed version. */
class VersionSettingsFragment : Fragment(R.layout.fragment_version_settings) {
    companion object {
        const val TAG = "VersionSettingsFragment"
        const val ARG_VERSION_ID = "version_id"
        private const val STATE_PAGE = "version_settings_page"
    }

    private lateinit var versionId: String
    private lateinit var pager: ViewPager2
    private lateinit var pageOneTab: TextView
    private lateinit var pageTwoTab: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        versionId = requireArguments().getString(ARG_VERSION_ID)
            ?: throw IllegalArgumentException("Missing installed version id")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pager = view.findViewById(R.id.version_settings_pager)
        pageOneTab = view.findViewById(R.id.version_settings_tab_one)
        pageTwoTab = view.findViewById(R.id.version_settings_tab_two)

        showVersion(versionId, savedInstanceState?.getInt(STATE_PAGE, 0)?.coerceIn(0, 1) ?: 0)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabs(position)
            }
        })
        pageOneTab.setOnClickListener { pager.setCurrentItem(0, true) }
        pageTwoTab.setOnClickListener { pager.setCurrentItem(1, true) }

    }

    fun onVersionRenamed(newVersionId: String) {
        versionId = newVersionId
        showVersion(newVersionId, 0)
    }

    fun flushPendingConfigurationWrites(): Future<*>? = childFragmentManager.fragments
        .filterIsInstance<VersionSettingsPageFragment>()
        .firstOrNull()
        ?.flushPendingWrites()

    override fun onSaveInstanceState(outState: Bundle) {
        if (::pager.isInitialized) outState.putInt(STATE_PAGE, pager.currentItem)
        super.onSaveInstanceState(outState)
    }

    private fun updateTabs(position: Int) {
        if (!::pageOneTab.isInitialized) return
        val firstSelected = position == 0
        pageOneTab.isSelected = firstSelected
        pageTwoTab.isSelected = !firstSelected
        pageOneTab.setTextColor(resources.getColor(
            if (firstSelected) R.color.primary_text else R.color.secondary_text,
            requireContext().theme
        ))
        pageTwoTab.setTextColor(resources.getColor(
            if (firstSelected) R.color.secondary_text else R.color.primary_text,
            requireContext().theme
        ))
    }

    private fun showVersion(id: String, page: Int) {
        pager.adapter = VersionSettingsPagerAdapter(this, id)
        pager.setCurrentItem(page, false)
        updateTabs(page)
    }
}

private class VersionSettingsPagerAdapter(
    fragment: Fragment,
    private val versionId: String
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> VersionSettingsOverviewFragment.newInstance(versionId)
        else -> VersionSettingsPageFragment.newInstance(versionId)
    }
}
