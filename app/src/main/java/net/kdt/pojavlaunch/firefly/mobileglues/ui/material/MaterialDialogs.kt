package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AuthMethod
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.Responsive
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AuthPrompt
import net.kdt.pojavlaunch.firefly.mobileglues.ui.Farewell
import net.kdt.pojavlaunch.firefly.mobileglues.ui.PrivacySections
import net.kdt.pojavlaunch.firefly.mobileglues.ui.LinkEntry
import net.kdt.pojavlaunch.firefly.mobileglues.ui.SponsorChannels
import net.kdt.pojavlaunch.firefly.mobileglues.ui.sourceRepositories
import net.kdt.pojavlaunch.firefly.mobileglues.ui.SponsorPromptState
import net.kdt.pojavlaunch.firefly.mobileglues.utils.Constants

/**
 * 全部全局对话框的宿主。
 *
 * 挂在皮肤最外层而不是各页面里：确认框、授权引导、赞助弹窗都可能在切页之后才被解决，
 * 挂在页面上会随着页面一起被销毁。
 */
@Composable
fun MaterialDialogHost(controller: AppController) {
    val confirm by controller.confirmRequest.collectAsStateWithLifecycle()
    val corrupt by controller.corruptPrompt.collectAsStateWithLifecycle()
    val authPrompt by controller.authPrompt.collectAsStateWithLifecycle()
    val sponsor by controller.sponsorPrompt.collectAsStateWithLifecycle()
    val removing by controller.removing.collectAsStateWithLifecycle()
    val farewell by controller.farewell.collectAsStateWithLifecycle()
    val privacyConsentNeeded by controller.privacyConsentNeeded.collectAsStateWithLifecycle()
    val resetPrompt by controller.resetPrompt.collectAsStateWithLifecycle()
    val sponsorPicker by controller.sponsorPicker.collectAsStateWithLifecycle()
    val repoPicker by controller.repoPicker.collectAsStateWithLifecycle()
    val auth by controller.auth.state.collectAsStateWithLifecycle()

    // 道别排在最前：这时候隐私同意已经被收回了，别让同意弹窗抢在道别前面糊上来。
    farewell?.let { reason ->
        MgTextDialog(
            title = stringResource(
                if (reason == Farewell.Removed) {
                    R.string.remove_complete_title
                } else {
                    R.string.revoke_complete_title
                },
            ),
            text = stringResource(
                if (reason == Farewell.Removed) {
                    R.string.remove_complete_message
                } else {
                    R.string.revoke_complete_message
                },
            ),
            positive = stringResource(R.string.exit),
            onPositive = controller::exitAfterFarewell,
            cancelable = false,
        )
        return
    }

    // 首次启动：先讲清楚这个 App 会碰什么，再谈别的。
    if (privacyConsentNeeded) {
        PrivacyConsentDialog(
            onAccept = controller::acceptPrivacy,
            onDecline = controller::declinePrivacy,
        )
        return
    }

    confirm?.let { MgConfirmDialog(it) }

    corrupt?.let { result ->
        MgTextDialog(
            title = stringResource(R.string.dialog_config_corrupt_title),
            text = stringResource(
                R.string.dialog_config_corrupt_message,
                result.backupName ?: "-",
                result.cause.message ?: result.cause.javaClass.simpleName,
            ),
            positive = stringResource(R.string.dialog_config_corrupt_reset),
            onPositive = controller::resetCorruptConfig,
            negative = stringResource(R.string.dialog_negative),
            onNegative = controller::dismissCorruptConfig,
            onDismiss = controller::dismissCorruptConfig,
        )
    }

    when (authPrompt) {
        AuthPrompt.ChooseMethod -> AuthMethodDialog(
            onSelect = controller::onAuthMethodSelected,
            onDismiss = controller::dismissAuthPrompt,
        )

        AuthPrompt.AllFilesIntro -> MgTextDialog(
            title = stringResource(R.string.dialog_permission_title),
            text = stringResource(R.string.dialog_permission_msg_android_Q, Constants.MG_DIRECTORY),
            positive = stringResource(R.string.dialog_positive),
            onPositive = controller::proceedAllFiles,
            negative = stringResource(R.string.dialog_negative),
            onNegative = controller::dismissAuthPrompt,
            onDismiss = controller::dismissAuthPrompt,
        )

        AuthPrompt.SafGuide -> MgTextDialog(
            title = stringResource(R.string.auth_guide_saf_title),
            text = stringResource(R.string.auth_guide_saf_msg),
            positive = stringResource(R.string.dialog_positive),
            onPositive = controller::proceedSaf,
            negative = stringResource(R.string.dialog_negative),
            onNegative = controller::dismissAuthPrompt,
            onDismiss = controller::dismissAuthPrompt,
        )

        AuthPrompt.LegacyDenied -> MgTextDialog(
            title = stringResource(R.string.dialog_permission_title),
            text = stringResource(R.string.dialog_permission_msg),
            positive = stringResource(R.string.dialog_positive),
            onPositive = controller::proceedAppDetails,
            negative = stringResource(R.string.dialog_negative),
            onNegative = controller::dismissAuthPrompt,
            onDismiss = controller::dismissAuthPrompt,
        )

        null -> Unit
    }

    when (val state = sponsor) {
        is SponsorPromptState.Ask -> MgTextDialog(
            title = stringResource(R.string.sponsor_dialog_title),
            text = stringResource(R.string.sponsor_dialog_msg, state.launchCount),
            positive = stringResource(R.string.sponsor_action_donate),
            onPositive = controller::onSponsorDonate,
            negative = stringResource(R.string.sponsor_action_later),
            onNegative = controller::onSponsorLater,
            onDismiss = controller::onSponsorLater,
        )

        SponsorPromptState.Confirm -> MgTextDialog(
            title = stringResource(R.string.sponsor_confirm_title),
            text = stringResource(R.string.sponsor_confirm_msg),
            positive = stringResource(R.string.sponsor_action_donated),
            onPositive = controller::onSponsorDonated,
            negative = stringResource(R.string.sponsor_action_not_yet),
            onNegative = controller::onSponsorNotYet,
            onDismiss = controller::onSponsorNotYet,
        )

        null -> Unit
    }

    if (sponsorPicker) {
        LinkChoiceDialog(
            title = stringResource(R.string.dialog_sponsor),
            message = stringResource(R.string.sponsor_channels_msg),
            links = SponsorChannels,
            onSelect = controller::onSponsorChannelSelected,
            onDismiss = controller::dismissSponsorPicker,
        )
    }

    if (repoPicker) {
        LinkChoiceDialog(
            title = stringResource(R.string.dialog_github),
            message = null,
            links = sourceRepositories(),
            onSelect = controller::onRepositorySelected,
            onDismiss = controller::dismissRepoPicker,
        )
    }

    if (resetPrompt) {
        ResetDialog(
            canDelete = auth.granted,
            onRevoke = controller::revokeAuthorization,
            onRemove = controller::resetMobileGluesData,
            onDismiss = controller::dismissResetPrompt,
        )
    }

    if (removing) ProgressDialog(text = stringResource(R.string.removing_mobileglues))

}

/**
 * 首次启动的隐私政策同意框。
 *
 * 里面是政策全文而不是摘要——同意的那一刻用户看到的，就该是他事后能翻回去看的那些字。
 * 不可取消：返回键和点遮罩都不算表态。
 */
@Composable
private fun PrivacyConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.privacy_consent_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = Responsive.dialogMaxContentHeight())
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.privacy_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrivacySections.forEach { (title, body) ->
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.privacy_consent_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.privacy_consent_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.privacy_consent_decline))
            }
        },
    )
}

/** 授权方式二选一。两种方式最终写的是同一个 MG 目录，差别只在授权的方式。 */
@Composable
private fun AuthMethodDialog(onSelect: (AuthMethod) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_choose_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.auth_choose_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
                AuthMethodOption(
                    title = stringResource(R.string.auth_method_all_files),
                    description = stringResource(R.string.auth_method_all_files_desc),
                    onClick = { onSelect(AuthMethod.AllFiles) },
                )
                Spacer(Modifier.size(8.dp))
                AuthMethodOption(
                    title = stringResource(R.string.auth_method_saf),
                    description = stringResource(R.string.auth_method_saf_desc),
                    onClick = { onSelect(AuthMethod.Saf) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_negative)) }
        },
    )
}

@Composable
private fun AuthMethodOption(
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    titleColor: Color = Color.Unspecified,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
                    titleColor != Color.Unspecified -> titleColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = if (enabled) 1f else DisabledAlpha),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 一组外链，选一个打开。赞助渠道和三个仓库共用它。
 *
 * 每条都把网址写出来：爱发电有三个域名在用、收款方也各不相同，仓库也有三个，
 * 只写个名字的话用户点下去之前并不知道会去哪里。
 */
@Composable
private fun LinkChoiceDialog(
    title: String,
    message: String?,
    links: List<LinkEntry>,
    onSelect: (LinkEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = Responsive.dialogMaxContentHeight())
                    .verticalScroll(rememberScrollState()),
            ) {
                // 一句都没有也行：三个仓库的名字自己说得清楚，硬凑一句解释反而是噪音。
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                }
                links.forEachIndexed { index, link ->
                    if (index > 0) Spacer(Modifier.size(8.dp))
                    AuthMethodOption(
                        title = link.label,
                        description = link.url,
                        onClick = { onSelect(link) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_negative)) }
        },
    )
}

/** 撤销 / 重置的二选一。删文件那条在未授权时点不动——没有访问权就删不了。 */
@Composable
private fun ResetDialog(
    canDelete: Boolean,
    onRevoke: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_item_reset)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AuthMethodOption(
                    title = stringResource(R.string.reset_option_revoke),
                    description = stringResource(R.string.reset_option_revoke_desc),
                    onClick = onRevoke,
                )
                Spacer(Modifier.size(8.dp))
                AuthMethodOption(
                    title = stringResource(R.string.reset_option_remove),
                    description = if (canDelete) {
                        stringResource(R.string.reset_option_remove_desc)
                    } else {
                        stringResource(R.string.reset_option_remove_needs_auth)
                    },
                    onClick = onRemove,
                    enabled = canDelete,
                    titleColor = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_negative)) }
        },
    )
}

/** 不可取消的进度对话框（移除 MobileGlues 期间）。 */
@Composable
private fun ProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(20.dp))
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {},
    )
}


