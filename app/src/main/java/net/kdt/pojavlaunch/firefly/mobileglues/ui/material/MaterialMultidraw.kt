package net.kdt.pojavlaunch.firefly.mobileglues.ui.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MGConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawBenchQuality
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawEntry
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawOrderItem
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawSettings
import net.kdt.pojavlaunch.firefly.mobileglues.settings.RankedItem
import net.kdt.pojavlaunch.firefly.mobileglues.ui.AppController
import net.kdt.pojavlaunch.firefly.mobileglues.ui.Responsive
import net.kdt.pojavlaunch.firefly.mobileglues.ui.DragReorderColumn
import androidx.compose.ui.unit.dp
import java.util.Locale

/** 折叠状态下的一句话摘要。 */
@Composable
fun multidrawSummary(settings: MultidrawSettings): String {
    val exceptionCount = settings.exceptions.size
    if (!settings.globalCustomized && exceptionCount == 0) {
        return stringResource(R.string.md_summary_default)
    }
    val global = if (settings.globalCustomized) {
        stringResource(R.string.md_summary_global_custom)
    } else {
        stringResource(R.string.md_summary_global_default)
    }
    return if (exceptionCount == 0) {
        global
    } else {
        "$global · ${stringResource(R.string.md_summary_exception_count, exceptionCount)}"
    }
}

/**
 * MultiDraw 排序设置：全局 8 项排序 + 每函数例外排序 + benchmark。
 * 放在「高级」组的可展开小节里。
 */
@Composable
fun ColumnScope.MultidrawOrderContent(controller: AppController, config: MGConfig) {
    val context = LocalContext.current
    val settings = config.multidraw

    SectionHint(stringResource(R.string.md_order_hint))

    DragReorderColumn(
        items = settings.globalOrder,
        onMove = controller::moveMultidrawGlobalItem,
    ) { index, item, dragging, handle ->
        OrderRow(
            position = index + 1,
            label = item.label(context).toString(),
            sublabel = if (item == MultidrawOrderItem.Native) {
                stringResource(R.string.md_item_native_desc)
            } else {
                null
            },
            dragging = dragging,
            handle = handle,
        )
    }

    AnimatedVisibility(visible = settings.globalCustomized, enter = fadeIn(), exit = fadeOut()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = controller::resetMultidrawGlobalOrder) {
                Text(stringResource(R.string.md_reset_default))
            }
        }
    }

    // ---- 例外函数 ----

    Text(
        text = stringResource(R.string.md_exceptions_group),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
    )

    // 跑分只测得出「这个函数上哪个方案快」，那就把结果按函数交出去，别硬合成一份全局顺序。
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        FilledTonalButton(
            onClick = { controller.runMultidrawBench(AppController.BenchTarget.AllEntries) },
        ) {
            Text(stringResource(R.string.md_bench_run_all))
        }
    }

    MultidrawEntry.entries.forEach { entry ->
        val hasException = settings.hasException(entry)
        SwitchPreferenceRow(
            title = entry.glFunction,
            summary = if (hasException) {
                stringResource(R.string.md_exception_on)
            } else {
                stringResource(R.string.md_exception_off)
            },
            checked = hasException,
            onCheckedChange = { controller.setMultidrawException(entry, it) },
        )
        AnimatedVisibility(
            visible = hasException,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                DragReorderColumn(
                    items = settings.effectiveOrderFor(entry),
                    onMove = { from, to ->
                        controller.moveMultidrawExceptionItem(entry, from, to)
                    },
                ) { index, backend, dragging, handle ->
                    OrderRow(
                        position = index + 1,
                        label = backend.label(context).toString(),
                        sublabel = null,
                        dragging = dragging,
                        handle = handle,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            controller.runMultidrawBench(AppController.BenchTarget.Entry(entry))
                        },
                    ) {
                        Text(stringResource(R.string.md_bench_run_entry))
                    }
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = settings.exceptionCustomized(entry),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        OutlinedButton(onClick = { controller.resetMultidrawExceptionOrder(entry) }) {
                            Text(stringResource(R.string.md_reset_default))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

/** 一行可排序项：序号 + 名称 + 拖动手柄。被拖起来的那行浮起来一层。 */
@Composable
private fun OrderRow(
    position: Int,
    label: String,
    sublabel: String?,
    dragging: Boolean,
    handle: Modifier,
) {
    val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "drag-elevation")
    val background by animateColorAsState(
        targetValue = if (dragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            Color.Transparent
        },
        label = "drag-background",
    )
    Surface(
        color = background,
        shadowElevation = elevation,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = handle.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.md_order_drag_handle),
                    tint = if (dragging) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ---- Benchmark 对话框 ----

/**
 * 选一个启动器把 ANGLE 借来。
 *
 * 这是把别的应用的原生代码载入本进程，所以选择必须是用户明确做出的，而且要把这句话
 * 当着他的面说清楚——不能因为「只有一个来源」就替他默认。
 */
@Composable
private fun AngleSourceDialog(controller: AppController) {
    val prompt by controller.angleSourcePrompt.collectAsStateWithLifecycle()
    val pending = prompt ?: return

    AlertDialog(
        onDismissRequest = controller::dismissAngleSourcePrompt,
        title = { Text(stringResource(R.string.md_angle_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = Responsive.dialogMaxContentHeight())
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.md_angle_intro))
                Spacer(Modifier.heightIn(min = 12.dp))
                if (pending.sources.isEmpty()) {
                    Text(stringResource(R.string.md_angle_none))
                } else {
                    Text(
                        text = AnnotatedString.fromHtml(stringResource(R.string.md_angle_trust)),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.heightIn(min = 8.dp))
                    pending.sources.forEach { source ->
                        Surface(
                            onClick = { controller.confirmAngleSource(source) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(source.label, style = MaterialTheme.typography.bodyLarge)
                                    if (source.packageName == pending.lastChosen) {
                                        Text(
                                            text = stringResource(R.string.md_angle_last_used),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = source.packageName +
                                        (source.versionName?.let {
                                            " · " + stringResource(R.string.md_angle_source_version, it)
                                        } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = controller::continueWithoutAngle) {
                Text(stringResource(R.string.md_angle_skip))
            }
        },
        dismissButton = {
            TextButton(onClick = controller::dismissAngleSourcePrompt) {
                Text(stringResource(R.string.dialog_negative))
            }
        },
    )
}

@Composable
fun MultidrawBenchDialogs(controller: AppController) {
    val context = LocalContext.current
    val state by controller.benchState.collectAsStateWithLifecycle()

    AngleSourceDialog(controller)

    when (val s = state) {
        is AppController.BenchState.Running -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.md_bench_running_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 进度是 native 那边的原子计数器；老渲染器没有，那就转圈。
                    val progress = s.progress
                    if (progress == null) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    } else {
                        val animated by animateFloatAsState(progress, label = "bench-progress")
                        CircularProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = when {
                            // 退让优先于「第几次测量」：上一趟整份作废了，说「第 2 次」
                            // 会让人以为前面那趟还算数。
                            s.retryingAtSections != null -> stringResource(
                                R.string.md_bench_running_smaller,
                                s.retryingAtSections,
                            )
                            s.attempt > 1 -> stringResource(
                                R.string.md_bench_running_retry,
                                s.attempt,
                                AppController.BENCH_MAX_ATTEMPTS,
                            )
                            else -> stringResource(R.string.md_bench_running_msg)
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
            confirmButton = {},
        )

        is AppController.BenchState.Done -> AlertDialog(
            onDismissRequest = controller::dismissBench,
            title = { Text(stringResource(R.string.md_bench_result_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = Responsive.dialogMaxContentHeight())
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = when (val target = s.target) {
                            is AppController.BenchTarget.AllEntries ->
                                stringResource(R.string.md_bench_result_all_intro)
                            is AppController.BenchTarget.Entry ->
                                stringResource(
                                    R.string.md_bench_result_entry_intro,
                                    target.entry.glFunction,
                                )
                        },
                    )
                    // 驱动错了就不只是「不够准」，是整份名次搬不过去，得说在最前面。
                    val angleNote = s.angleNote
                    if (angleNote != null) {
                        Spacer(Modifier.heightIn(min = 12.dp))
                        Text(
                            text = stringResource(angleNote.messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // 单个函数时函数名已经在开头那句里了，再标一次是废话。
                    val showHeadings = s.target is AppController.BenchTarget.AllEntries
                    s.rankings.forEach { (entry, ranking) ->
                        Spacer(Modifier.heightIn(min = 12.dp))
                        if (showHeadings) {
                            Text(
                                text = entry.glFunction,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ranking.forEachIndexed { index, ranked ->
                            RankedRow(index + 1, ranked.item.label(context).toString(), ranked.relativeCost)
                        }
                        // 只有一个方案测得出时，「排名」这个词就名不副实——没有可比较的对象，
                        // ×1.00 也失去含义。说破，免得用户把孤例当选择。
                        if (ranking.count { it.relativeCost != null } == 1) {
                            Text(
                                text = stringResource(R.string.md_bench_single_candidate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 成色跟着它描述的那份排名走：抖的是某个函数，不是整场跑分。
                        BenchQualityNote(s.quality[entry])
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = controller::adoptBenchResult) {
                    Text(
                        stringResource(
                            if (s.anyNoisy || s.driverMismatch) {
                                R.string.md_bench_adopt_anyway
                            } else {
                                R.string.md_bench_adopt
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = controller::dismissBench) {
                    Text(stringResource(R.string.md_bench_discard))
                }
            },
        )

        is AppController.BenchState.Failed -> MgTextDialog(
            title = stringResource(R.string.md_bench_result_title),
            text = s.message,
            positive = stringResource(R.string.ok),
            onPositive = controller::dismissBench,
            onDismiss = controller::dismissBench,
        )

        null -> Unit
    }
}

/**
 * 一份排名底下的一行小字：这个函数测了几轮、抖动多大。
 *
 * 抖动比相邻两名的差距还大时，这份排名就只能算「大致如此」，说出来比装作精确要好。
 * [MultidrawBenchQuality.noisy] 是这个函数反复加长重测到头也没压下来——要用户自己拍板。
 */
@Composable
private fun BenchQualityNote(quality: MultidrawBenchQuality?) {
    if (quality == null || quality.rounds <= 0) return
    val noise = String.format(Locale.US, "%.1f", quality.noise * 100)
    Text(
        text = stringResource(R.string.md_bench_quality, quality.rounds, noise),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (quality.noisy) {
        Text(
            text = stringResource(R.string.md_bench_noisy, quality.attempts, noise),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun RankedRow(position: Int, label: String, relativeCost: Double?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = "$position.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (relativeCost == null) {
                Text(
                    text = stringResource(R.string.md_bench_unmeasured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (relativeCost != null) {
            Text(
                text = stringResource(
                    R.string.md_bench_cost,
                    String.format(Locale.US, "%.2f", relativeCost),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}


