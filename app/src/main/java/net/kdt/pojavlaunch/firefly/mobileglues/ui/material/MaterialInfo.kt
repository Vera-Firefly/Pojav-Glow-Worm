package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppSubPage

/**
 * 信息页：应用信息、可核实的东西（GL 信息、隐私政策）、以及危险区域。
 *
 * 「移除 MobileGlues」从旧界面的溢出菜单搬到了这里——一个会删掉用户全部配置的操作
 * 不该藏在三个点后面。
 */
@Composable
fun MaterialInfoPage(controller: AppController) {
    val auth by controller.auth.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PageTitle(stringResource(R.string.nav_info))

        PreferenceGroup(title = stringResource(R.string.info_section_app)) {
            TextPreferenceRow(
                title = stringResource(R.string.info_version_label),
                summary = controller.appVersionName,
            )
            TextPreferenceRow(
                title = stringResource(R.string.dialog_github),
                summary = stringResource(R.string.repo_summary),
                onClick = controller::openSourceRepositories,
            )
            TextPreferenceRow(
                title = stringResource(R.string.dialog_sponsor),
                summary = stringResource(R.string.sponsor_channels_summary),
                onClick = controller::openSponsorChannels,
            )
        }

        PreferenceGroup(title = stringResource(R.string.info_section_about)) {
            TextPreferenceRow(
                title = label(R.string.view_author),
                summary = stringResource(R.string.info_author),
            )
            TextPreferenceRow(
                title = label(R.string.view_copyright),
                summary = stringResource(R.string.info_copyright),
            )
            TextPreferenceRow(
                title = label(R.string.view_launcher),
                summary = stringResource(R.string.info_launcher),
            )
            TextPreferenceRow(
                title = label(R.string.view_logo),
                summary = stringResource(R.string.info_logo),
            )
            TextPreferenceRow(
                title = stringResource(R.string.third_party_title),
                summary = stringResource(R.string.third_party_summary),
                onClick = { controller.openSubPage(AppSubPage.ThirdParty) },
            )
        }

        PreferenceGroup(title = stringResource(R.string.info_section_details)) {
            TextPreferenceRow(
                title = stringResource(R.string.info_mg_info),
                onClick = { controller.openSubPage(AppSubPage.GlInfo) },
            )
            TextPreferenceRow(
                title = stringResource(R.string.info_privacy),
                onClick = { controller.openSubPage(AppSubPage.Privacy) },
            )
        }

        PreferenceGroup(title = stringResource(R.string.info_danger_zone)) {
            // 撤销和删除是包含关系，摆成两个平级按钮会互相锁死：撤了就删不动，
            // 删完了也没什么可撤。所以入口只有一个，进去再选做到哪一步。
            TextPreferenceRow(
                title = stringResource(R.string.menu_item_reset),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = controller::openResetPrompt,
            )
        }

        // 致谢摆在最后：它是这一页读到底之后的落款，不是一个要跳过去的功能。
        MaterialContributorsSection(controller)

        BottomSpacer()
    }
}

@Composable
private fun ExternalLinkIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_open_in_new),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/** 「版本：」这类文案自带冒号，当行标题用的时候要去掉。 */
@Composable
private fun label(@StringRes id: Int): String = stringResource(id).trimEnd(' ', ':', '：')


