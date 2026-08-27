package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.PrivacySections
import net.kdt.pojavlaunch.firefly.mobileglues.ui.ThirdPartyGroups

/**
 * GL 信息页：每次进来都重新查一次（渲染器的 .so 可能刚被游戏更新过），
 * 查询期间给进度指示，回来之后文本可选中、可一键复制。
 */
@Composable
fun MaterialGlInfoPage(controller: AppController) {
    val context = LocalContext.current
    val info by controller.glInfo.collectAsStateWithLifecycle()
    val loading by controller.glInfoLoading.collectAsStateWithLifecycle()
    val needsAngle by controller.glInfoNeedsAngle.collectAsStateWithLifecycle()
    val angleState by controller.glInfoAngle.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { controller.loadGlInfo() }

    SubPageScaffold(
        title = stringResource(R.string.dialog_mg_gl_info_title),
        onBack = { controller.navigateBack() },
        actions = {
            // 没有内容可复制的时候按钮不该在那儿等着被按。
            AnimatedVisibility(
                visible = !info.isNullOrBlank(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                IconButton(onClick = { copyGlInfo(context, controller, info.orEmpty()) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Crossfade(targetState = loading, label = "gl-info") { busy ->
            if (busy) {
                CenteredLoading(
                    text = stringResource(R.string.gl_info_loading),
                    modifier = Modifier.padding(top = 48.dp),
                )
            } else {
                Column {
                    // ANGLE 随启动器走，本 App 里没有；不借的话这一页讲的是系统驱动，
                    // 不是游戏里那个。借不借由用户点——不能因为他只想看一眼就自作主张
                    // 把别人的原生代码载进来。
                    if (needsAngle) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = ScreenPadding, vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.md_glinfo_needs_angle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(
                                    onClick = { controller.reloadGlInfoWithAngle() },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text(stringResource(R.string.md_glinfo_borrow))
                                }
                            }
                        }
                    } else if (angleState == AppController.GlInfoAngle.Borrowed) {
                        Text(
                            text = stringResource(R.string.md_glinfo_borrowed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                        )
                    } else if (angleState == AppController.GlInfoAngle.BorrowIneffective) {
                        Text(
                            text = stringResource(R.string.md_glinfo_borrow_ineffective),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    ) {
                        SelectableBody(
                            text = info.orEmpty(),
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
        BottomSpacer()
    }
}

private fun copyGlInfo(context: Context, controller: AppController, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(GL_INFO_CLIP_LABEL, text))
    // Android 13 起系统自己会弹一个复制提示，再来一条就是重复了。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        controller.snackbar(context.getString(R.string.copied))
    }
}

/**
 * 隐私政策页。
 *
 * 逐条写清楚碰哪些文件、为什么要存储权限、本地存了什么，最后给出一条可以自己核实的事实：
 * 清单里没有 INTERNET 权限，所以「上传」在技术上根本做不到。
 */
@Composable
fun MaterialPrivacyPage(controller: AppController) {
    SubPageScaffold(
        title = stringResource(R.string.info_privacy),
        onBack = { controller.navigateBack() },
    ) {
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 8.dp),
        )
        // 标题在卡片外、正文在卡片内——和设置页的分组是同一套语法。
        PrivacySections.forEach { (title, body) ->
            PreferenceGroup(
                title = stringResource(title),
                titleColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
        BottomSpacer()
    }
}

/**
 * 第三方开源项目。
 *
 * 分「渲染器」和「插件」两组：用户看到 SPIRV-Cross 的时候，应该同时知道它是被游戏里
 * 那个 .so 用的，而不是被这个设置界面用的。每一项都能点开自己的主页去看许可证原文——
 * 在这里抄一份许可证全文，既没人读，也保证不了和上游一致。
 */
@Composable
fun MaterialThirdPartyPage(controller: AppController) {
    SubPageScaffold(
        title = stringResource(R.string.third_party_title),
        onBack = { controller.navigateBack() },
    ) {
        Text(
            text = stringResource(R.string.third_party_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 8.dp),
        )
        ThirdPartyGroups.forEach { group ->
            PreferenceGroup(title = stringResource(group.title)) {
                group.components.forEach { component ->
                    TextPreferenceRow(
                        title = component.name,
                        summary = "${component.author} · ${component.license}",
                        onClick = { controller.openThirdPartyComponent(component) },
                    )
                }
            }
        }
        BottomSpacer()
    }
}

/** 子页面的骨架：返回键 + 标题 + 可选操作，下面是可滚动的内容。 */
@Composable
private fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.nav_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            actions()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

private const val GL_INFO_CLIP_LABEL = "MobileGlues GL info"


