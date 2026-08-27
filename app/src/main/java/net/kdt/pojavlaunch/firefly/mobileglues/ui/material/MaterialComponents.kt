@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.ui.ConfirmRequest
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 页面左右留白。三个页面用同一个值，滚动时各组卡片的边缘才对得齐。 */
val ScreenPadding = 16.dp

/** MD3 皮肤主题：Android 12+ 用动态取色（Material You），以下回落到默认色板。 */
@Composable
fun MgMaterialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * 解析含 HTML 的警告文案。`@colorError` 是作者写在 strings.xml 里的占位符，
 * 在这里换成当前皮肤的 error 色——同一套文案两个皮肤各自上色。
 */
@Composable
fun rememberHtmlAnnotatedString(html: String, errorColor: Color): AnnotatedString {
    return remember(html, errorColor) {
        val hex = String.format("#%06X", 0xFFFFFF and errorColor.toArgb())
        AnnotatedString.fromHtml(html.replace("@colorError", hex))
    }
}

/**
 * 「先问再改」的确认对话框，可选倒计时（确认键读秒，结束后变警示色）。
 * 返回键算「否」，点遮罩不关闭——和旧界面的语义一致。
 */
@Composable
fun MgConfirmDialog(request: ConfirmRequest) {
    var secondsLeft by remember(request) { mutableIntStateOf(request.countdownSeconds) }
    LaunchedEffect(request) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val counting = secondsLeft > 0
    AlertDialog(
        onDismissRequest = { request.resolve(false) },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { Text(stringResource(request.titleRes)) },
        text = {
            // 警告文案可能很长（移除 MobileGlues、自定义 GL 版本），对话框内部要能滚。
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (request.messageIsHtml) {
                    Text(rememberHtmlAnnotatedString(request.message, MaterialTheme.colorScheme.error))
                } else {
                    Text(request.message)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { request.resolve(true) }, enabled = !counting) {
                Text(
                    text = if (counting) {
                        stringResource(R.string.ok_with_countdown, secondsLeft)
                    } else {
                        stringResource(request.positiveRes)
                    },
                    color = if (request.errorAccent && !counting) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { request.resolve(false) }) {
                Text(stringResource(R.string.dialog_negative))
            }
        },
    )
}

/** 通用标题 + 文案对话框。 */
@Composable
fun MgTextDialog(
    title: String,
    text: String,
    positive: String,
    onPositive: () -> Unit,
    negative: String? = null,
    onNegative: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    cancelable: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = cancelable,
            dismissOnClickOutside = cancelable,
        ),
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(text) }
        },
        confirmButton = { TextButton(onClick = onPositive) { Text(positive) } },
        dismissButton = if (negative != null) {
            { TextButton(onClick = { onNegative?.invoke() }) { Text(negative) } }
        } else {
            null
        },
    )
}

/** 页面大标题。三个页面共用，代替系统 ActionBar。 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = ScreenPadding + 8.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** 设置分组：小节标题 + 一张圆角卡片，卡片里是若干行。 */
@Composable
fun PreferenceGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    // 分组标签用主色；文档式的小标题（隐私政策）传 onSurface，那不是一组控件的名字。
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
        }
    }
}

/**
 * 一行设置：标题 +（可选）副标题，尾部可放图标。
 *
 * 副标题在标题下方而不是行尾：本 App 的选项名普遍很长（「Enable Incomplete
 * 'ARB_compute_shader' Extension」），挤在一行里两边都会被截断。
 */
@Composable
fun TextPreferenceRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
                    titleColor != Color.Unspecified -> titleColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 1f else DisabledAlpha),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}

/** 一行开关设置。 */
@Composable
fun SwitchPreferenceRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
                },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 一行滑块设置：标题 + 当前值 + 滑块。取值是「档位」，换算由调用方负责。 */
@Composable
fun SliderPreferenceRow(
    title: String,
    valueLabel: String,
    position: Int,
    steps: Int,
    onPositionChange: (Int) -> Unit,
    onDragFinished: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Slider(
            value = position.toFloat(),
            onValueChange = { onPositionChange(it.roundToInt()) },
            onValueChangeFinished = onDragFinished,
            valueRange = 0f..steps.toFloat(),
            // 档位太密，画刻度点只会糊成一条线：连续拖动、落点取整。
            steps = 0,
        )
    }
}

/** 可展开的一节：点标题行展开，内容带高度动画。 */
@Composable
fun ExpandableSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        TextPreferenceRow(
            title = title,
            summary = summary,
            onClick = onToggle,
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** 分段控件（界面风格切换）。 */
@Composable
fun SegmentedChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

/** 单选对话框：Spinner 的 Compose 替代。 */
@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                options.forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(index)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_negative)) }
        },
    )
}

/** 整页居中的加载指示。 */
@Composable
fun CenteredLoading(text: String? = null, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(32.dp),
    ) {
        CircularProgressIndicator()
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** 可选中的整段文本（GL 信息）。 */
@Composable
fun SelectableBody(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 页面底部留白：让最后一张卡片不会紧贴导航栏。 */
@Composable
fun BottomSpacer() {
    Spacer(Modifier.height(24.dp))
}

/** MD3 规范里「禁用」态的内容透明度。 */
const val DisabledAlpha = 0.38f


