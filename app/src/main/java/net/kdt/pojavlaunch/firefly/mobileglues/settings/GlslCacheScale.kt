package net.kdt.pojavlaunch.firefly.mobileglues.settings

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * GLSL 缓存滑块的刻度换算。
 *
 * 滑块的取值是「档位」（0..[STEPS]），MiB 由档位换算，所以滑块自身的量程是常量——
 * 不会出现「上限低于当前值」这种 Slider 会抛异常的中间状态。
 *
 * 档位 0 是「关闭」；1..N 在 1 MiB 和上限之间按 [CURVE] 次幂分布：
 * 靠近 0 的一段一格只差一点点（16/32/64 这些真正要调的档位有足够行程），靠近上限一格跨几十 MiB。
 */
object GlslCacheScale {

    /** 滑块上限取总内存的几分之一。 */
    const val RAM_DIVISOR = 16L

    /** 内存特别小的设备上也要留出可用的调节范围。 */
    const val MIN_UPPER_BOUND_MIB = 64L
    const val MAX_UPPER_BOUND_MIB = 8192L

    /** 滑块的档位数（不含 0 号「关闭」档）。 */
    const val STEPS = 200

    /** 刻度的弯度：1 = 线性，越大越向低端倾斜（对数刻度相当于无穷大，低端过于夸张）。 */
    const val CURVE = 2.0

    /** 由设备总内存（字节）算出的基础上限（MiB）。 */
    fun baseCeiling(totalRamBytes: Long): Int =
        (totalRamBytes / (RAM_DIVISOR * 1024 * 1024))
            .coerceIn(MIN_UPPER_BOUND_MIB, MAX_UPPER_BOUND_MIB)
            .toInt()

    /**
     * 实际量程上限。配置里可能存着比设备内存 1/16 更大的值（旧配置或手工编辑）：
     * 把量程抬到它，宁可让滑块变长，也不能悄悄把用户的设置调低。
     */
    fun ceiling(totalRamBytes: Long, currentMebibytes: Int): Int =
        maxOf(baseCeiling(totalRamBytes), currentMebibytes)

    /** 档位 → MiB。 */
    fun mebibytesAt(position: Int, ceiling: Int): Int {
        if (position <= 0) return 0
        if (ceiling <= 1) return ceiling
        val ratio = (position - 1).toDouble() / (STEPS - 1)
        return (1 + (ceiling - 1) * ratio.pow(CURVE))
            .roundToInt()
            .coerceIn(1, ceiling)
    }

    /** [mebibytesAt] 的逆运算。 */
    fun positionFor(mebibytes: Int, ceiling: Int): Int {
        if (mebibytes <= 0) return 0
        if (ceiling <= 1) return STEPS
        val ratio = ((mebibytes - 1).toDouble() / (ceiling - 1)).coerceIn(0.0, 1.0)
        val position = ratio.pow(1.0 / CURVE) * (STEPS - 1)
        return (1 + position.roundToInt()).coerceIn(1, STEPS)
    }
}


