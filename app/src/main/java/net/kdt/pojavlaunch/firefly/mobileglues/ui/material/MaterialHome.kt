package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppTab

/**
 * 首页：一个大字标，加几件真正值得一眼看到的小事——有没有授权、跑在什么 GPU 上、
 * 当前配置是什么。别的都在另外两页里。
 */
@Composable
fun MaterialHomePage(controller: AppController) {
    val auth by controller.auth.state.collectAsStateWithLifecycle()
    val deviceInfo by controller.deviceInfo.collectAsStateWithLifecycle()
    val config by controller.configStore.config.collectAsStateWithLifecycle()
    val untuned by controller.multidrawUntuned.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { controller.ensureDeviceInfo() }

    // 启动次数记在 MG 目录里，未授权时读不到；授权建立之后再问一次。
    LaunchedEffect(auth.granted) {
        if (auth.granted) controller.maybeShowSponsorPrompt()
    }

    // 进场：字标先到，其余元素依次跟上。visible 一开始为 false，组合完成后才翻成 true。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // 可滚动，并且只在放得下时才居中。这一页的内容高度是固定的（字标、授权状态、设备
    // 信息、配置摘要，加上它们之间的定距），竖屏放得下，横屏放不下——原来它既不滚动又
    // 强制居中，于是横屏下两端被裁掉且够不到。verticalScroll 加上 heightIn(min) 让它在
    // 高屏上照旧居中，在矮屏上变成一列可滚的内容。
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .heightIn(min = minHeight)
                .padding(horizontal = ScreenPadding),
        ) {
        EnterUp(entered, delayMillis = 0) { Wordmark(controller.appVersionName) }

        Spacer(Modifier.height(28.dp))

        EnterUp(entered, delayMillis = 90) {
            AuthPill(
                granted = auth.granted,
                onClick = { if (!auth.granted) controller.requestAccess() },
            )
        }

        Spacer(Modifier.height(28.dp))

        EnterUp(entered, delayMillis = 160) { DeviceInfoBlock(deviceInfo) }

        Spacer(Modifier.height(24.dp))

        EnterUp(entered, delayMillis = 230) {
            Crossfade(targetState = config, label = "summary") { current ->
                if (current != null) {
                    ConfigSummaryCard(
                        summary = controller.configSummary(current),
                        onClick = { controller.navigateTab(AppTab.Settings) },
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }

        // 排序还是出厂那份，没人量过这台设备。
        // 两层动画各管各的：外层跟着首页那串进场依次上来，内层负责跑完分采用之后自己收走。
        EnterUp(entered, delayMillis = 300) {
            AnimatedVisibility(
                visible = untuned && auth.granted,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    BenchmarkNudge(
                        onClick = {
                            controller.runMultidrawBench(AppController.BenchTarget.AllEntries)
                        },
                    )
                }
            }
        }

            // 视觉重心略高于几何中心：底部这段留白把整组内容往上顶。矮屏上它是滚动
            // 内容的一部分，不再是够不到的死区。
            Spacer(Modifier.height(72.dp))
        }
    }
}

/** 大号字标。「Glues」用主色，是整页唯一的一处强调。 */
@Composable
private fun Wordmark(version: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("Mobile") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Glues") }
            },
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                letterSpacing = (-0.5).sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "v$version",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 授权状态。未授权时是一个能直接发起授权流的按钮，授权后只是一枚状态标。 */
@Composable
private fun AuthPill(granted: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = tween(320),
        label = "pill-container",
    )
    val content by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = tween(320),
        label = "pill-content",
    )

    Surface(shape = CircleShape, color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = !granted, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(content),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(
                    if (granted) R.string.home_status_granted else R.string.home_status_denied,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

/** GPU / GLES / 内存。查询没回来之前留占位，不让布局跳一下。 */
@Composable
private fun DeviceInfoBlock(info: net.kdt.pojavlaunch.firefly.mobileglues.DeviceInfo?) {
    val unknown = stringResource(R.string.home_device_unknown)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        DeviceInfoRow(
            label = stringResource(R.string.home_device_gpu),
            value = info?.gpuRenderer?.takeIf { it.isNotBlank() } ?: unknown,
            loaded = info != null,
        )
        DeviceInfoRow(
            label = stringResource(R.string.home_device_gles),
            value = info?.glesVersion?.takeIf { it.isNotBlank() } ?: unknown,
            loaded = info != null,
        )
        DeviceInfoRow(
            label = stringResource(R.string.home_device_ram),
            value = info?.let {
                stringResource(R.string.home_ram_value, it.totalRamBytes / GIBIBYTE)
            } ?: unknown,
            loaded = info != null,
        )
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String, loaded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Crossfade(targetState = loaded, label = "device-value", modifier = Modifier.weight(1f)) {
            Text(
                text = if (it) value else "…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 当前配置的一行只读摘要，点进设置页。 */
@Composable
private fun ConfigSummaryCard(summary: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_config_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 「还没量过这台设备」的提示。
 *
 * 说一句、给一个按钮，不解释原理——首页不是讲道理的地方。一旦排序不再是默认（自己拖过，
 * 或采用了跑分结果），它自己就不出现了，所以不需要「不再提示」。
 */
@Composable
private fun BenchmarkNudge(onClick: () -> Unit) {
    // 用主题自己的 errorContainer 而不是手调一个红：动态取色下它会跟着壁纸走，
    // 深色模式也自带一份，比钉死一个色号稳当，也不会跟这一页其它颜色打架。
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.md_home_untuned),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.md_home_untuned_action),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 进场动画：淡入 + 上移，靠 [delayMillis] 排出先后。 */
@Composable
private fun EnterUp(visible: Boolean, delayMillis: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 420, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = 420,
                    delayMillis = delayMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) { it / 3 },
        exit = ExitTransition.None,
    ) {
        content()
    }
}

private const val GIBIBYTE = 1024.0 * 1024.0 * 1024.0


