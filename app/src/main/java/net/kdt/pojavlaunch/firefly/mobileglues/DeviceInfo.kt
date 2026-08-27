package net.kdt.pojavlaunch.firefly.mobileglues

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 首页要展示的设备信息。 */
data class DeviceInfo(
    val gpuRenderer: String?,
    val glesVersion: String?,
    val totalRamBytes: Long,
)

/**
 * 查 GPU 名字和 GLES 版本要创建一次 EGL 上下文（重活，放后台线程），
 * 结果在进程内不会变，查一次缓存住——首页展示和 Adreno 740 判定共用这份缓存。
 */
object DeviceInfoProvider {

    private val lock = Mutex()
    private var cached: DeviceInfo? = null

    suspend fun get(context: Context): DeviceInfo = lock.withLock {
        cached?.let { return it }
        val gl = withContext(Dispatchers.Default) { probeGl() }
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        DeviceInfo(
            gpuRenderer = gl?.first,
            glesVersion = gl?.second,
            totalRamBytes = memoryInfo.totalMem,
        ).also { cached = it }
    }

    suspend fun isAdreno740(context: Context): Boolean {
        val renderer = get(context).gpuRenderer ?: return false
        return renderer.contains("adreno", ignoreCase = true) &&
            renderer.contains("740")
    }

    /** GL_RENDERER + GL_VERSION；拿不到（模拟器异常等）返回 null。 */
    private fun probeGl(): Pair<String, String>? {
        val eglDisplay: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY ||
            !EGL14.eglInitialize(eglDisplay, IntArray(2), 0, IntArray(2), 1)
        ) return null

        var result: Pair<String, String>? = null
        val configAttributes =
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)

        if (EGL14.eglChooseConfig(
                eglDisplay, configAttributes, 0, eglConfigs, 0, 1, numConfigs, 0
            )
        ) {
            val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext: EGLContext = EGL14.eglCreateContext(
                eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0
            )

            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                val pbufferAttributes =
                    intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                val eglSurface: EGLSurface =
                    EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], pbufferAttributes, 0)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    if (EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
                        val version = GLES20.glGetString(GLES20.GL_VERSION)
                        if (renderer != null || version != null) {
                            result = (renderer ?: "") to (version ?: "")
                        }
                    }
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
        }
        EGL14.eglTerminate(eglDisplay)
        return result
    }
}


