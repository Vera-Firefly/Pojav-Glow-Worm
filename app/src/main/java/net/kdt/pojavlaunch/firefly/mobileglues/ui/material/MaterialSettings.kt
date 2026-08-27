@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AngleConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.DepthClearFixMode
import net.kdt.pojavlaunch.firefly.mobileglues.settings.GlVersion
import net.kdt.pojavlaunch.firefly.mobileglues.settings.GlslCacheScale
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MGConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.NoErrorConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.SpinnerOption
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.SettingsLoadState

/**
 * 设置页。
 *
 * 权限门只拦渲染器配置那一段：「Plugin 配置」（界面风格）是本 App 自己的偏好，
 * 和 MG 目录无关，所以放在门之上——未授权时这一页也不是一片空白。
 */
@Composable
fun MaterialSettingsPage(controller: AppController) {
    val auth by controller.auth.state.collectAsStateWithLifecycle()
    val loadState by controller.loadState.collectAsStateWithLifecycle()
    val config by controller.configStore.config.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { controller.ensureDeviceInfo() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PageTitle(stringResource(R.string.nav_settings))

        Crossfade(
            targetState = auth.granted to (loadState == SettingsLoadState.Ready && config != null),
            label = "settings-gate",
        ) { (granted, ready) ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    !granted -> PermissionGate(onGrant = controller::requestAccess)
                    ready -> ConfigSections(controller, config ?: MGConfig.Default)
                    else -> CenteredLoading(modifier = Modifier.padding(top = 48.dp))
                }
            }
        }

        BottomSpacer()
    }
}

/** 未授权时的权限门。 */
@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = stringResource(R.string.settings_gate_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_gate_msg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onGrant, modifier = Modifier.padding(top = 20.dp)) {
                Text(stringResource(R.string.settings_gate_grant))
            }
        }
    }
}

/** 权限门之内：渲染 / 着色器缓存 / 扩展 / 高级。 */
@Composable
private fun ConfigSections(controller: AppController, config: MGConfig) {
    val context = LocalContext.current
    val deviceInfo by controller.deviceInfo.collectAsStateWithLifecycle()
    val cacheBytes by controller.configStore.glslCacheBytes.collectAsStateWithLifecycle()

    var choice by remember { mutableStateOf<ChoiceTarget?>(null) }
    var multidrawExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PreferenceGroup(title = stringResource(R.string.settings_group_render)) {
            TextPreferenceRow(
                title = stringResource(R.string.option_angle),
                summary = config.angle.label(context).toString(),
                onClick = { choice = ChoiceTarget.Angle },
            )
            TextPreferenceRow(
                title = stringResource(R.string.option_no_error),
                summary = config.noError.label(context).toString(),
                onClick = { choice = ChoiceTarget.NoError },
            )
            TextPreferenceRow(
                title = stringResource(R.string.option_angle_clear_workaround),
                summary = config.depthClearFix.label(context).toString(),
                onClick = { choice = ChoiceTarget.DepthClear },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.option_enable_fsr1),
                checked = config.fsr1Enabled,
                onCheckedChange = controller::setFsr1,
            )
        }

        PreferenceGroup(title = stringResource(R.string.settings_group_cache)) {
            GlslCacheSlider(controller, config, deviceInfo?.totalRamBytes)
            // 没有缓存文件时不摆一个删不了东西的按钮：它按需浮现，删完收回。
            AnimatedVisibility(
                visible = cacheBytes != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                TextPreferenceRow(
                    title = stringResource(
                        R.string.option_glsl_cache_delete,
                        controller.formatCacheSize(cacheBytes ?: 0L),
                    ),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = controller::deleteGlslCache,
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.settings_group_ext)) {
            SwitchPreferenceRow(
                title = stringResource(R.string.option_ext_cs),
                checked = config.extComputeShader,
                onCheckedChange = controller::setExtComputeShader,
            )
            SwitchPreferenceRow(
                // 磁盘上记的是「启用」，界面上问的是「禁用」，取反只发生在这一行。
                title = stringResource(R.string.option_ext_timer_query),
                checked = !config.extTimerQuery,
                onCheckedChange = controller::setExtTimerQueryDisabled,
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.option_ext_direct_state_access),
                checked = config.extDirectStateAccess,
                onCheckedChange = controller::setExtDirectStateAccess,
            )
        }

        PreferenceGroup(title = stringResource(R.string.settings_group_advanced)) {
            TextPreferenceRow(
                title = stringResource(R.string.option_custom_gl_version),
                summary = config.glVersion.label(context).toString(),
                onClick = { choice = ChoiceTarget.GlVersion },
            )

            ExpandableSection(
                title = stringResource(R.string.option_multidraw),
                summary = multidrawSummary(config.multidraw),
                expanded = multidrawExpanded,
                onToggle = { multidrawExpanded = !multidrawExpanded },
            ) {
                MultidrawOrderContent(controller, config)
            }
        }
    }


    // ---- 选项对话框 ----

    when (choice) {
        ChoiceTarget.Angle -> OptionDialog(
            title = stringResource(R.string.option_angle),
            options = AngleConfig.entries,
            selected = config.angle,
            onSelect = controller::selectAngle,
            onDismiss = { choice = null },
        )

        ChoiceTarget.NoError -> OptionDialog(
            title = stringResource(R.string.option_no_error),
            options = NoErrorConfig.entries,
            selected = config.noError,
            onSelect = controller::selectNoError,
            onDismiss = { choice = null },
        )

        ChoiceTarget.DepthClear -> OptionDialog(
            title = stringResource(R.string.option_angle_clear_workaround),
            options = DepthClearFixMode.entries,
            selected = config.depthClearFix,
            onSelect = controller::selectDepthClearFix,
            onDismiss = { choice = null },
        )

        ChoiceTarget.GlVersion -> OptionDialog(
            title = stringResource(R.string.option_custom_gl_version),
            options = GlVersion.entries,
            selected = config.glVersion,
            onSelect = controller::selectGlVersion,
            onDismiss = { choice = null },
        )

        null -> Unit
    }

}

/**
 * 缓存上限滑块。
 *
 * 拖动期间用本地档位，松手才交还给配置：档位 → MiB → 档位 的换算有取整，
 * 直接跟着配置画的话手指底下的滑块会自己抖。
 */
@Composable
private fun GlslCacheSlider(controller: AppController, config: MGConfig, totalRamBytes: Long?) {
    val mebibytes = config.glslCache.mebibytesOrZero
    // 内存还没查回来时先按最小量程画，查到之后量程只会变大，滑块位置不会倒退。
    val base = totalRamBytes?.let { GlslCacheScale.baseCeiling(it) }
        ?: GlslCacheScale.MIN_UPPER_BOUND_MIB.toInt()
    val ceiling = maxOf(base, mebibytes)
    var dragPosition by remember { mutableStateOf<Int?>(null) }

    SliderPreferenceRow(
        title = stringResource(R.string.option_glsl_cache),
        valueLabel = if (mebibytes > 0) {
            stringResource(R.string.option_glsl_cache_value, mebibytes)
        } else {
            stringResource(R.string.option_glsl_cache_off)
        },
        position = dragPosition ?: GlslCacheScale.positionFor(mebibytes, ceiling),
        steps = GlslCacheScale.STEPS,
        onPositionChange = { position ->
            dragPosition = position
            controller.setGlslCacheSliderPosition(position, ceiling)
        },
        onDragFinished = { dragPosition = null },
    )
}

/** Spinner 的替代：枚举 → 单选对话框，选项顺序就是枚举的声明顺序。 */
@Composable
private fun <T : SpinnerOption> OptionDialog(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    SingleChoiceDialog(
        title = title,
        options = options.map { it.label(context).toString() },
        selectedIndex = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
        onDismiss = onDismiss,
    )
}

private enum class ChoiceTarget { Angle, NoError, DepthClear, GlVersion }


