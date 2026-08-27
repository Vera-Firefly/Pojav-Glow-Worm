package net.kdt.pojavlaunch.firefly.mobileglues.ui

import androidx.annotation.StringRes
import net.kdt.pojavlaunch.firefly.R

/** 隐私政策的一节。 */
data class PrivacySection(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

/**
 * 隐私政策全文的条目。
 *
 * 首启的同意弹窗和信息页里的隐私政策页读的是同一份：用户点「同意」时看到的，
 * 必须就是他事后能翻回去看的那些字，一字不差。
 */
val PrivacySections = listOf(
    PrivacySection(R.string.privacy_files_title, R.string.privacy_files_body),
    PrivacySection(R.string.privacy_permission_title, R.string.privacy_permission_body),
    PrivacySection(R.string.privacy_local_title, R.string.privacy_local_body),
    PrivacySection(R.string.privacy_links_title, R.string.privacy_links_body),
    PrivacySection(R.string.privacy_network_title, R.string.privacy_network_body),
)


