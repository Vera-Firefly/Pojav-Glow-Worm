package net.kdt.pojavlaunch.firefly.mobileglues.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 横屏与竖屏的差别，在这个 App 里只体现为一件事：垂直方向变得很紧。
 *
 * 所以这里量的是高度而不是问朝向。一台折叠屏展开后是宽的，但高度依然充裕，它不该被当成
 * 横屏对待；而一台普通手机横过来只剩三百多 dp，无论朝向枚举怎么说，它就是紧。
 */
object Responsive {

    /** 低于这个高度就按「竖直方向紧张」处理：导航移到侧边，弹窗把内容区压到能放下按钮。 */
    private val CompactHeight = 480.dp

    /** 正文超过这个宽度就不再拉长：一行几十个字读起来比一行一百个字轻松。 */
    val MaxContentWidth = 720.dp

    @Composable
    @ReadOnlyComposable
    fun isHeightCompact(): Boolean = LocalConfiguration.current.screenHeightDp.dp < CompactHeight

    /**
     * 弹窗里那块可滚动内容最多能有多高。
     *
     * 之前这里写死 420.dp。竖屏下它小于可用高度，看不出问题；横屏可用高度只有三百多 dp，
     * 内容区自己就要 420，标题和按钮被挤出屏幕——用户看得到文字却按不到「确定」。
     *
     * 现在从真实窗口高度减去要留给标题、按钮和系统栏的余量，再夹在一个下限上：内容再长也
     * 至少给出一屏可读的量，而按钮永远在。
     */
    @Composable
    fun dialogMaxContentHeight(): Dp {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val available = LocalConfiguration.current.screenHeightDp.dp -
            insets.calculateTopPadding() - insets.calculateBottomPadding()
        // 标题 + 按钮 + 弹窗自身的内外边距，实测这一圈在两套皮肤上都不超过 200dp。
        val reserved = 200.dp
        val cap = available - reserved
        return if (cap < 160.dp) 160.dp else cap
    }
}


