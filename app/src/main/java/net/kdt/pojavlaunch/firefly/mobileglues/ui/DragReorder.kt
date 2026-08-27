package net.kdt.pojavlaunch.firefly.mobileglues.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * 拖动排序的运行时状态。
 *
 * 列表在 composition 里的物理顺序**始终**是 [DragReorderColumn] 收到的 items 顺序，
 * 拖动过程中一行也不重排——重排只发生在松手那一刻。拖动中看到的位移全部是绘制层的
 * translationY：被拖的那行跟手，被它挤开的那些行整体让出一个行高。这样既不用在拖动
 * 途中反复写配置，也避开了非 Lazy 布局里换位必然出现的跳变。
 */
@Stable
class DragReorderState {

    /** 每行的实测高度（px），下标即物理下标。 */
    private val heights = mutableStateListOf<Int>()

    /** 列表容器左上角在窗口里的 y，用来判断手指有没有贴到屏幕边缘。 */
    private var containerTopInWindow by mutableFloatStateOf(0f)

    /** 正在被拖的行；-1 表示没有。 */
    var dragIndex by mutableIntStateOf(-1)
        private set

    /** 松手后它会落到的位置。 */
    var targetIndex by mutableIntStateOf(-1)
        private set

    /** 被拖那行相对原位的位移（px）。 */
    var dragOffset by mutableFloatStateOf(0f)
        private set

    /** 行数，由 composition 同步过来；拖动中的边界判断要用最新值。 */
    var count by mutableIntStateOf(0)

    val isDragging: Boolean get() = dragIndex >= 0

    // 报告方是 onGloballyPositioned，拖动期间每帧都会来一次；写回相同的值也算改动，
    // 会把整列拖进重组，所以变了才写。
    fun setHeight(index: Int, height: Int) {
        while (heights.size <= index) heights.add(0)
        if (heights[index] != height) heights[index] = height
    }

    fun setContainerTop(y: Float) {
        containerTopInWindow = y
    }

    private fun heightOf(index: Int): Int = heights.getOrElse(index) { 0 }

    /** 第 [index] 行在未发生任何位移时的顶边。 */
    private fun topOf(index: Int): Float {
        var sum = 0f
        for (i in 0 until minOf(index, heights.size)) sum += heights[i]
        return sum
    }

    /** 第 [index] 行当前的绘制位移：让开被拖行的那些行整体挪一个行高。 */
    fun displacementOf(index: Int): Float {
        if (!isDragging || index == dragIndex) return 0f
        val dragHeight = heightOf(dragIndex).toFloat()
        return when {
            targetIndex > dragIndex && index in (dragIndex + 1)..targetIndex -> -dragHeight
            targetIndex < dragIndex && index in targetIndex..(dragIndex - 1) -> dragHeight
            else -> 0f
        }
    }

    private fun visualTopOf(index: Int): Float = topOf(index) + displacementOf(index)

    /**
     * 开始拖第 [index] 行；上一次的落位动画还没走完时返回 false。
     *
     * 落位动画只有一两百毫秒，中途抢跑得先把上一次提交掉，而提交会当场换掉行的顺序，
     * 新拖动手里的下标就对不上了。宁可放过这次触摸。
     */
    fun tryStartDrag(index: Int): Boolean {
        if (isDragging) return false
        dragIndex = index
        targetIndex = index
        dragOffset = 0f
        return true
    }

    /**
     * 手指移动 [delta] px 后重算落点。
     *
     * 判据是被拖行的中心压进了相邻那行的地界——盖住对方一半就换，拖满一整行才换会很迟钝。
     * 相邻行取的是它**当前看到的**位置（已经让开过的要算上位移），否则一口气跨好几行时
     * 阈值会一格格错开。返回 true 表示落点变了，调用方可以借此给一下振动。
     */
    fun onDrag(delta: Float): Boolean {
        dragOffset += delta
        val before = targetIndex
        val center = topOf(dragIndex) + dragOffset + heightOf(dragIndex) / 2f
        while (targetIndex < count - 1) {
            val over = if (targetIndex >= dragIndex) targetIndex + 1 else targetIndex
            if (center <= visualTopOf(over)) break
            targetIndex++
        }
        while (targetIndex > 0) {
            val over = if (targetIndex > dragIndex) targetIndex else targetIndex - 1
            if (center >= visualTopOf(over) + heightOf(over)) break
            targetIndex--
        }
        return targetIndex != before
    }

    /** 被拖行中心此刻在窗口里的 y，供边缘自动滚动使用。 */
    fun draggedCenterInWindow(): Float =
        containerTopInWindow + topOf(dragIndex) + dragOffset + heightOf(dragIndex) / 2f

    /** 自动滚动消耗掉的距离要补进位移里，手指没动，行相对内容却确实挪了。 */
    fun onAutoScrolled(consumed: Float) {
        onDrag(consumed)
    }

    /** 松手动画专用：只挪绘制位置，落点已经定下来了，不再跟着重算。 */
    fun setOffset(offset: Float) {
        dragOffset = offset
    }

    /** 被拖行落到 [targetIndex] 时应有的位移——松手动画的终点。 */
    fun settleOffset(): Float {
        val from = dragIndex
        val to = targetIndex
        return when {
            to > from -> topOf(to) + heightOf(to) - topOf(from) - heightOf(from)
            to < from -> topOf(to) - topOf(from)
            else -> 0f
        }
    }

    fun finish() {
        dragIndex = -1
        targetIndex = -1
        dragOffset = 0f
    }
}

/**
 * 一列可以按住手柄上下拖动来重排的行。
 *
 * [row] 收到的 handle 必须挂到行内某个抓手上（那个 ☰），拖动只从它开始——
 * 整行可拖会和外层设置页的竖向滚动打架。[onMove] 在松手时调用一次，参数是物理下标。
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, item: T, dragging: Boolean, handle: Modifier) -> Unit,
) {
    val state = remember { DragReorderState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()

    // 松手后到新顺序从配置那边绕回来之前，先照我们自己算出的顺序渲染。
    // 手势是一次性捕获的，读 items / onMove 都得走 rememberUpdatedState 才拿得到当前值。
    val pending = remember { mutableStateOf<List<T>?>(null) }
    val latestItems = rememberUpdatedState(items)
    val latestOnMove = rememberUpdatedState(onMove)
    val shown = pending.value ?: items

    SideEffect { state.count = shown.size }

    // 贴边自动滚动：离边越近滚得越快。滚的是外面那个设置页，但这里不去拿它的
    // ScrollState——把滚动量当作嵌套滚动事件抛出去，谁在滚谁接住，控件本身不用知道。
    val dispatcher = remember { NestedScrollDispatcher() }
    val noopConnection = remember { object : NestedScrollConnection {} }
    val edge = with(density) { 88.dp.toPx() }
    val maxStep = with(density) { 9.dp.toPx() }
    LaunchedEffect(state.isDragging) {
        if (!state.isDragging) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val y = state.draggedCenterInWindow()
            val step = when {
                y < edge -> -(edge - y) / edge * maxStep
                y > windowHeight - edge -> (y - (windowHeight - edge)) / edge * maxStep
                else -> 0f
            }
            if (abs(step) <= 0.5f) continue
            // 嵌套滚动的 y 是手指方向：内容要上滚，手指就是往上走。
            val consumed = dispatcher.dispatchPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, -step),
                source = NestedScrollSource.UserInput,
            )
            state.onAutoScrolled(-consumed.y)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(noopConnection, dispatcher)
            .onGloballyPositioned { state.setContainerTop(it.positionInWindow().y) },
    ) {
        shown.forEachIndexed { index, item ->
            // 按 item 认人，不按位置。Column 默认是位置复用，行一换位，「被拖起来」那套
            // 动效（底色、阴影）的动画状态就留在原来的位置上淡出，而 item 已经走了。
            // 这里的 items 是一个排列，item 本身就是唯一的 key。
            key(item) {
                val dragging = state.dragIndex == index
                val displacement by animateFloatAsState(
                    targetValue = state.displacementOf(index),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "reorder-displacement",
                )
                Box(
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            // 让位的位移只在拖动期间读。松手那一帧行已经换到新位置了，
                            // 这时候还去读正在往回走的动画值，看着就是闪一下。
                            translationY = when {
                                dragging -> state.dragOffset
                                state.isDragging -> displacement
                                else -> 0f
                            }
                        }
                        // onSizeChanged 在这里不够：认了 item 之后，行换位而尺寸不变就不会
                        // 回调，heights 会一直按旧位置记着。位置变了也得重新报一次。
                        .onGloballyPositioned { state.setHeight(index, it.size.height) },
                ) {
                    val handle = Modifier.pointerInput(state, index) {
                        detectDragGestures(
                            onDragStart = {
                                if (state.tryStartDrag(index)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, amount ->
                                if (state.dragIndex == index) {
                                    change.consume()
                                    if (state.onDrag(amount.y)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (state.dragIndex == index) {
                                    scope.launch { settle(state, haptic, latestItems, latestOnMove, pending) }
                                }
                            },
                            onDragCancel = {
                                if (state.dragIndex == index) {
                                    scope.launch { settle(state, haptic, latestItems, latestOnMove, pending) }
                                }
                            },
                        )
                    }
                    row(index, item, dragging, handle)
                }
            }
        }
    }
}

/**
 * 松手：先把被拖的行滑到落点，滑到位了再换位。
 *
 * 顺序反过来的话（先换位再收动画）会跳一下——动画结束时的绘制位置正好等于换位后的
 * 布局位置，这一帧交接才是无缝的。
 *
 * 换位却不能等配置绕回来：[onMove] 写进 config，新顺序要经 StateFlow 再经
 * collectAsStateWithLifecycle 才回到 composition，中间那一两帧是「旧顺序 + 位移已清零」，
 * 行会先弹回原位再跳到新位置。所以这里自己先把重排后的列表摆出来，等真的顺序到手了再让位。
 */
private suspend fun <T> settle(
    state: DragReorderState,
    haptic: HapticFeedback,
    items: State<List<T>>,
    onMove: State<(from: Int, to: Int) -> Unit>,
    pending: MutableState<List<T>?>,
) {
    if (!state.isDragging) return
    val from = state.dragIndex
    val to = state.targetIndex
    animate(
        initialValue = state.dragOffset,
        targetValue = state.settleOffset(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    ) { value, _ -> state.setOffset(value) }

    val current = pending.value ?: items.value
    if (from == to || from !in current.indices || to !in current.indices) {
        state.finish()
        return
    }

    // 这两行必须挨着：换位和清零位移落在同一帧，画面才不会闪。
    val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
    pending.value = reordered
    state.finish()

    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    onMove.value(from, to)

    // 配置若是拒了这次改动（顺序原样传回来），超时之后照配置说的显示，别一直挂着。
    withTimeoutOrNull(REORDER_COMMIT_TIMEOUT_MS) {
        snapshotFlow { items.value }.first { it == reordered }
    }
    pending.value = null
}

private const val REORDER_COMMIT_TIMEOUT_MS = 250L


