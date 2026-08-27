package net.kdt.pojavlaunch.firefly.mobileglues.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import net.kdt.pojavlaunch.firefly.BuildConfig
import net.kdt.pojavlaunch.firefly.mobileglues.DeviceInfo
import net.kdt.pojavlaunch.firefly.mobileglues.DeviceInfoProvider
import net.kdt.pojavlaunch.firefly.mobileglues.MGApplication
import net.kdt.pojavlaunch.firefly.mobileglues.MGBench
import net.kdt.pojavlaunch.firefly.mobileglues.MGInfoGetter
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AngleConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AngleProvider
import net.kdt.pojavlaunch.firefly.mobileglues.MgQuery
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AngleSource
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AuthController
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AuthMethod
import net.kdt.pojavlaunch.firefly.mobileglues.settings.ConfigLoadResult
import net.kdt.pojavlaunch.firefly.mobileglues.settings.ConfigStoreEvent
import net.kdt.pojavlaunch.firefly.mobileglues.settings.DepthClearFixMode
import net.kdt.pojavlaunch.firefly.mobileglues.settings.Fsr1Preset
import net.kdt.pojavlaunch.firefly.mobileglues.settings.GlVersion
import net.kdt.pojavlaunch.firefly.mobileglues.settings.GlslCacheScale
import net.kdt.pojavlaunch.firefly.mobileglues.settings.GlslCacheSize
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MGConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MgStats
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawBackend
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawBenchAnalyzer
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawBenchQuality
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawBenchReport
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawEntry
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawOrderItem
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MultidrawSettings
import net.kdt.pojavlaunch.firefly.mobileglues.settings.NoErrorConfig
import net.kdt.pojavlaunch.firefly.mobileglues.settings.RankedItem
import net.kdt.pojavlaunch.firefly.mobileglues.settings.SponsorPrompt
import net.kdt.pojavlaunch.firefly.mobileglues.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** 底部导航的三个页。 */
enum class AppTab { Home, Settings, Info }

/** 从信息页进入的子页面（带返回）。 */
enum class AppSubPage { GlInfo, Privacy, ThirdParty }

/** 配置加载状态：权限门之内的内容区按它决定显示什么。 */
enum class SettingsLoadState { NotLoaded, Loading, Ready }

/** 授权流程的对话框状态机。由皮肤渲染，按钮回调进 [AppController]。 */
sealed interface AuthPrompt {
    /** 选择授权方式（Android 11+：所有文件访问 / SAF）。 */
    data object ChooseMethod : AuthPrompt

    /** 「所有文件访问」的说明，确认后跳系统设置。 */
    data object AllFilesIntro : AuthPrompt

    /** SAF 引导：提示用户新建/选择 MG 目录。 */
    data object SafGuide : AuthPrompt

    /** 旧版权限被永久拒绝后引导去应用详情页。 */
    data object LegacyDenied : AuthPrompt
}

/** 收尾道别：两条路走到最后都要谢一声，然后退出。 */
enum class Farewell {
    /** 只收回了授权，MG 目录原样留着。 */
    Revoked,

    /** 连文件一起删了。 */
    Removed,
}

/** 赞助弹窗的两步。 */
sealed interface SponsorPromptState {
    /** 第一步：温和地询问（报出具体启动次数）。 */
    data class Ask(val launchCount: Int) : SponsorPromptState

    /** 第二步：跳转捐赠页之后，委婉地确认是否已捐赠。 */
    data object Confirm : SponsorPromptState
}

/**
 * 一次「先问再改」的确认。倒计时结束后 [errorAccent] 让确认键变成警示色——
 * 和旧界面自定义 GL 版本 / 移除 MobileGlues 的行为一致。
 */
class ConfirmRequest(
    @param:StringRes val titleRes: Int,
    val message: String,
    /** message 是 HTML（含 @colorError 占位符），皮肤用自己的 error 色替换后解析。 */
    val messageIsHtml: Boolean = false,
    @param:StringRes val positiveRes: Int = R.string.dialog_positive,
    val countdownSeconds: Int = 0,
    val errorAccent: Boolean = false,
    private val onResult: (Boolean) -> Unit,
) {
    private val resolved = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 按钮、返回键、对话框关闭可能各触发一次，只有第一次算数。 */
    fun resolve(result: Boolean) {
        if (resolved.compareAndSet(false, true)) onResult(result)
    }
}

/** 由 MainActivity 实现：只有 Activity 能发起系统授权页 / 退出应用。 */
interface AuthFlowLauncher {
    fun openAllFilesSettings()
    fun openSafPicker()
    fun requestLegacyPermission()
    fun openAppDetailsSettings()
    fun exitApp()
}

/**
 * 两套皮肤（MD3 / Miuix）共用的全部 UI 逻辑：导航、授权流、配置加载、
 * 设置动作（含全部警告与倒计时）、赞助弹窗、移除流程、GL 信息查询。
 * 皮肤只负责把这些状态画出来，并把用户操作回调进来——操作逻辑因此只有一份。
 */
class AppController(
    private val app: MGApplication,
    private val context: Context,
    private val launcher: AuthFlowLauncher,
) {

    /** 渲染器查询的一次性进程通道；跑分与 GL 信息共用（互斥地）。 */
    private val mgQuery = MgQuery(context)

    val pluginConfig = app.pluginConfigStore
    val configStore = app.configStore
    val auth = app.authController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- 导航 ----

    private val mutableTab = MutableStateFlow(AppTab.Home)
    val tab: StateFlow<AppTab> = mutableTab.asStateFlow()

    private val mutableSubPage = MutableStateFlow<AppSubPage?>(null)
    val subPage: StateFlow<AppSubPage?> = mutableSubPage.asStateFlow()

    fun navigateTab(target: AppTab) {
        mutableSubPage.value = null
        mutableTab.value = target
    }

    fun openSubPage(page: AppSubPage) {
        mutableSubPage.value = page
    }

    /** 返回键：有子页面先退子页面。返回 true 表示已消费。 */
    fun navigateBack(): Boolean {
        return if (mutableSubPage.value != null) {
            mutableSubPage.value = null
            true
        } else {
            false
        }
    }

    // ---- Snackbar ----

    private val mutableSnackbar = MutableSharedFlow<CharSequence>(extraBufferCapacity = 8)
    val snackbar: MutableSharedFlow<CharSequence> = mutableSnackbar

    fun snackbar(text: CharSequence) {
        mutableSnackbar.tryEmit(text)
    }

    // ---- 确认对话框 ----

    private val mutableConfirm = MutableStateFlow<ConfirmRequest?>(null)
    val confirmRequest: StateFlow<ConfirmRequest?> = mutableConfirm.asStateFlow()

    /**
     * 挂起直到用户做出选择。取消、返回键、点遮罩都算「否」。
     * 「先问再改」的流程因此是一条直线，不需要拆 onConfirm/onCancel 两条回调。
     */
    suspend fun confirm(
        @StringRes messageRes: Int,
        countdownSeconds: Int = 0,
        messageIsHtml: Boolean = false,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        mutableConfirm.value = ConfirmRequest(
            titleRes = R.string.dialog_title_warning,
            message = context.getString(messageRes),
            messageIsHtml = messageIsHtml,
            positiveRes = if (countdownSeconds > 0) R.string.ok else R.string.dialog_positive,
            countdownSeconds = countdownSeconds,
            errorAccent = countdownSeconds > 0,
        ) { result ->
            mutableConfirm.value = null
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { mutableConfirm.value = null }
    }

    // ---- 隐私政策（首次启动）----

    /**
     * 还没同意过隐私政策。
     *
     * 直接读偏好而不是拍一张快照：同意之后这个值翻成 false，弹窗自己就退场了。
     */
    val privacyConsentNeeded: StateFlow<Boolean> = pluginConfig.privacyAccepted
        .map { !it }
        .stateIn(scope, SharingStarted.Eagerly, !pluginConfig.privacyAccepted.value)

    fun acceptPrivacy() = pluginConfig.markPrivacyAccepted()

    /** 不同意就没有可谈的了：这个 App 的全部用途都需要读写 MG 目录。 */
    fun declinePrivacy() = launcher.exitApp()

    // ---- 授权流 ----

    private val mutableAuthPrompt = MutableStateFlow<AuthPrompt?>(null)
    val authPrompt: StateFlow<AuthPrompt?> = mutableAuthPrompt.asStateFlow()

    /** 「去授权」的总入口：Android 11+ 先选方式，以下直接走旧版运行时权限。 */
    fun requestAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            mutableAuthPrompt.value = AuthPrompt.ChooseMethod
        } else {
            launcher.requestLegacyPermission()
        }
    }

    fun dismissAuthPrompt() {
        mutableAuthPrompt.value = null
    }

    fun onAuthMethodSelected(method: AuthMethod) {
        mutableAuthPrompt.value = when (method) {
            AuthMethod.AllFiles -> AuthPrompt.AllFilesIntro
            AuthMethod.Saf -> AuthPrompt.SafGuide
            AuthMethod.Legacy -> {
                launcher.requestLegacyPermission()
                null
            }
        }
    }

    /** AllFilesIntro 的确认键：跳系统的所有文件访问设置页。 */
    fun proceedAllFiles() {
        mutableAuthPrompt.value = null
        launcher.openAllFilesSettings()
    }

    /** SafGuide 的确认键：打开系统目录选择器。 */
    fun proceedSaf() {
        mutableAuthPrompt.value = null
        launcher.openSafPicker()
    }

    /** LegacyDenied 的确认键：跳应用详情页。 */
    fun proceedAppDetails() {
        mutableAuthPrompt.value = null
        launcher.openAppDetailsSettings()
    }

    // ---- 由 MainActivity 回填的授权结果 ----

    fun onAllFilesResult() {
        if (AuthController.hasAllFilesAccess()) {
            auth.grantAllFiles()
        } else {
            snackbar(context.getString(R.string.permission_failed))
        }
    }

    fun onSafResult(uri: Uri?) {
        if (uri == null) return
        when (auth.grantSaf(uri)) {
            AuthController.SafGrantResult.Success -> Unit
            AuthController.SafGrantResult.WrongFolder ->
                snackbar(context.getString(R.string.auth_wrong_folder))

            AuthController.SafGrantResult.Inaccessible ->
                snackbar(context.getString(R.string.permission_failed))
        }
    }

    fun onLegacyPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        when {
            granted -> auth.grantLegacy()
            permanentlyDenied -> mutableAuthPrompt.value = AuthPrompt.LegacyDenied
            else -> snackbar(context.getString(R.string.permission_failed))
        }
    }

    // ---- 配置加载 ----

    private val mutableLoadState = MutableStateFlow(SettingsLoadState.NotLoaded)
    val loadState: StateFlow<SettingsLoadState> = mutableLoadState.asStateFlow()

    private val mutableCorruptPrompt = MutableStateFlow<ConfigLoadResult.Corrupt?>(null)
    val corruptPrompt: StateFlow<ConfigLoadResult.Corrupt?> = mutableCorruptPrompt.asStateFlow()

    /** 正在进行的一次加载。授权状态翻转不能在它完成之前把界面打回未加载。 */
    private var loadJob: Job? = null

    init {
        // 授权状态是唯一事实来源：授权建立 → 接入存储并加载；失效 → 摘掉存储退回未加载。
        scope.launch {
            auth.state.collect { state ->
                val storage = state.storage
                if (state.granted && storage != null) {
                    configStore.attachStorage(storage)
                    ensureConfigLoaded()
                } else {
                    configStore.detachStorage()
                    mutableLoadState.value = SettingsLoadState.NotLoaded
                }
            }
        }
        scope.launch {
            configStore.events.collect { event ->
                when (event) {
                    is ConfigStoreEvent.SaveFailed -> snackbar(
                        context.getString(
                            R.string.config_save_failed,
                            event.cause.message ?: event.cause.javaClass.simpleName,
                        ),
                    )
                }
            }
        }
    }

    /** 前台恢复时调用：重新核验授权（系统设置里可能刚改完）。 */
    fun refreshAuthorization() = auth.refresh()

    private fun ensureConfigLoaded() {
        if (mutableLoadState.value == SettingsLoadState.Ready) return
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            mutableLoadState.value = SettingsLoadState.Loading
            when (val result = configStore.load()) {
                ConfigLoadResult.Missing -> {
                    // 先建立 config.json 再亮出设置：危险区域的「移除」按授权+加载状态启用。
                    configStore.flush()
                    mutableLoadState.value = SettingsLoadState.Ready
                }

                is ConfigLoadResult.Loaded -> mutableLoadState.value = SettingsLoadState.Ready

                is ConfigLoadResult.Corrupt -> {
                    mutableLoadState.value = SettingsLoadState.NotLoaded
                    mutableCorruptPrompt.value = result
                }
            }
        }
    }

    /** 配置文件损坏 → 用户选择重置。 */
    fun resetCorruptConfig() {
        mutableCorruptPrompt.value = null
        scope.launch {
            configStore.resetToDefaults()
            mutableLoadState.value = SettingsLoadState.Ready
        }
    }

    /** 配置文件损坏 → 用户选择不重置：回首页，不碰那个文件。 */
    fun dismissCorruptConfig() {
        mutableCorruptPrompt.value = null
        navigateTab(AppTab.Home)
    }

    // ---- 设备信息 ----

    private val mutableDeviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = mutableDeviceInfo.asStateFlow()

    fun ensureDeviceInfo() {
        if (mutableDeviceInfo.value != null) return
        scope.launch { mutableDeviceInfo.value = DeviceInfoProvider.get(context) }
    }

    // ---- 设置动作（皮肤回调的唯一入口）----

    private fun update(transform: (MGConfig) -> MGConfig) = configStore.update(transform)

    private fun confirmThenUpdate(
        @StringRes messageRes: Int,
        countdownSeconds: Int = 0,
        messageIsHtml: Boolean = false,
        transform: (MGConfig) -> MGConfig,
    ) {
        scope.launch {
            if (confirm(messageRes, countdownSeconds, messageIsHtml)) update(transform)
            // 取消时什么都不做：单向数据流下界面从未离开当前配置，不需要「撤销」。
        }
    }

    fun selectAngle(target: AngleConfig) {
        val current = configStore.config.value ?: return
        if (target == current.angle) return
        scope.launch {
            val approved = target != AngleConfig.ForceEnable ||
                !DeviceInfoProvider.isAdreno740(context) ||
                confirm(R.string.warning_adreno_740_angle)
            if (!approved) return@launch
            update { it.copy(angle = target) }
            // 换了驱动，之前那份排序是在另一个驱动上量出来的。只有用户自己调过或跑过分
            // 才值得说这句——默认顺序本来就不是量出来的，换驱动也谈不上过期。
            if (current.multidraw != MultidrawSettings.Default) {
                mutableBenchOutdated.tryEmit(Unit)
            }
        }
    }

    /**
     * ANGLE 模式变了，而手上这份 MultiDraw 排序是在旧驱动上定的。
     *
     * 用 SharedFlow 而不是状态位：这是一次「刚刚发生了什么」的通知，用户看过就过去了，
     * 不该在重组或返回这一页时再冒出来一次。
     */
    private val mutableBenchOutdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val benchOutdated: MutableSharedFlow<Unit> = mutableBenchOutdated

    fun selectNoError(target: NoErrorConfig) = update { it.copy(noError = target) }

    fun selectGlVersion(target: GlVersion) {
        val current = configStore.config.value ?: return
        if (target == current.glVersion) return
        if (current.glVersion == GlVersion.Default) {
            // 只在「从不启用切到某个具体版本」时才需要冷静期。
            confirmThenUpdate(
                R.string.warning_enabling_custom_gl_version,
                CUSTOM_GL_VERSION_COOLDOWN_SECONDS,
                messageIsHtml = true,
            ) { it.copy(glVersion = target) }
        } else {
            update { it.copy(glVersion = target) }
        }
    }

    fun selectDepthClearFix(target: DepthClearFixMode) {
        val current = configStore.config.value ?: return
        if (target == current.depthClearFix) return
        if (target == DepthClearFixMode.Disabled) {
            update { it.copy(depthClearFix = target) }
        } else {
            confirmThenUpdate(R.string.warning_enabling_angle_clear_workaround) {
                it.copy(depthClearFix = target)
            }
        }
    }

    fun setExtComputeShader(enabled: Boolean) {
        val current = configStore.config.value ?: return
        if (enabled == current.extComputeShader) return
        if (enabled) {
            confirmThenUpdate(R.string.warning_ext_cs_enable) { it.copy(extComputeShader = true) }
        } else {
            update { it.copy(extComputeShader = false) }
        }
    }

    /** 开关文案是「禁用 timer_query」，勾上等于磁盘上写 0。 */
    fun setExtTimerQueryDisabled(disabled: Boolean) = update { it.copy(extTimerQuery = !disabled) }

    fun setExtDirectStateAccess(enabled: Boolean) = update { it.copy(extDirectStateAccess = enabled) }

    fun setFsr1(enabled: Boolean) {
        val current = configStore.config.value ?: return
        if (enabled == current.fsr1Enabled) return
        if (enabled) {
            confirmThenUpdate(R.string.warning_fsr1_enable) { it.copy(fsr1 = Fsr1Preset.UltraQuality) }
        } else {
            update { it.copy(fsr1 = Fsr1Preset.Disabled) }
        }
    }

    /** 滑块档位 → MiB → 配置。关掉缓存只是改配置，不动已有的缓存文件。 */
    fun setGlslCacheSliderPosition(position: Int, ceiling: Int) {
        update { it.copy(glslCache = GlslCacheSize.ofMebibytes(GlslCacheScale.mebibytesAt(position, ceiling))) }
    }

    fun deleteGlslCache() {
        scope.launch {
            configStore.clearGlslCache().onFailure { cause ->
                snackbar(
                    context.getString(
                        R.string.option_glsl_cache_delete_failed,
                        cause.message ?: cause.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    // ---- MultiDraw 排序 ----

    /** 全局排序里把第 [from] 项拖到第 [to] 位。 */
    fun moveMultidrawGlobalItem(from: Int, to: Int) {
        update {
            val order = it.multidraw.globalOrder.toMutableList()
            if (!order.moveItem(from, to)) return@update it
            it.copy(multidraw = it.multidraw.withGlobalOrder(order))
        }
    }

    fun resetMultidrawGlobalOrder() {
        update { it.copy(multidraw = it.multidraw.withGlobalOrder(MultidrawOrderItem.DefaultOrder)) }
    }

    fun setMultidrawException(entry: MultidrawEntry, enabled: Boolean) {
        update { it.copy(multidraw = it.multidraw.withException(entry, enabled)) }
    }

    /** 把某个函数的例外排序退回它的默认值——全局排序在这个函数上的展开。 */
    fun resetMultidrawExceptionOrder(entry: MultidrawEntry) {
        update {
            it.copy(
                multidraw = it.multidraw
                    .withExceptionOrder(entry, it.multidraw.globalOrderFor(entry)),
            )
        }
    }

    /** 某函数的例外排序里把第 [from] 项拖到第 [to] 位。 */
    fun moveMultidrawExceptionItem(entry: MultidrawEntry, from: Int, to: Int) {
        update {
            val order = it.multidraw.effectiveOrderFor(entry).toMutableList()
            if (!order.moveItem(from, to)) return@update it
            it.copy(multidraw = it.multidraw.withExceptionOrder(entry, order))
        }
    }

    /** 拖动排序是「抽出来再插进去」，不是相邻交换——跨多位时两者结果不一样。 */
    private fun <T> MutableList<T>.moveItem(from: Int, to: Int): Boolean {
        if (from == to || from !in indices || to !in indices) return false
        add(to, removeAt(from))
        return true
    }

    // ---- MultiDraw 跑分 ----

    /**
     * 跑分对象：所有函数各测各的，或只测某一个函数。
     *
     * 没有「测出一份全局排序」这回事了：全局排序要一个次序同时适配五个函数，而跑分本来就是
     * 分函数测的，硬合成一份反而把每个函数上都不是最优的顺序说成最优。
     */
    sealed interface BenchTarget {
        data object AllEntries : BenchTarget
        data class Entry(val entry: MultidrawEntry) : BenchTarget
    }

    /**
     * 跑分流程状态：null（无）→ Running →（Done | Failed），弹窗由 UI 依此渲染。
     *
     * [Running.progress] 是 0f..1f；渲染器版本太老、拿不到进度时为 null，UI 退回不定进度条。
     */
    sealed interface BenchState {
        data class Running(
            val target: BenchTarget,
            val progress: Float? = null,
            /** 第几次测量（1 起）。抖得厉害时 native 会放大场景重测，最多 [BENCH_MAX_ATTEMPTS] 次。 */
            val attempt: Int = 1,
            /**
             * 上一趟把 GL 上下文撑爆了，这一趟在更小的场景上从头再来。
             *
             * 是「重来」不是「继续」：微秒数随场景规模变，两种规模下的数字摆在一起排名
             * 就是拿两把尺子量。所以崩掉那一趟的结果整份作废。
             */
            val retryingAtSections: Int? = null,
        ) : BenchState

        data class Done(
            val target: BenchTarget,
            /** 每个函数一份排名，按 [MultidrawEntry] 的声明顺序。 */
            val rankings: Map<MultidrawEntry, List<RankedItem<MultidrawBackend>>>,
            /** 每个函数各自的成色：测了几轮、抖多少、是不是抖到没法信。 */
            val quality: Map<MultidrawEntry, MultidrawBenchQuality> = emptyMap(),
            /** 这次测量与 ANGLE 的关系里有值得告诉用户的一句话；null = 一切如预期。 */
            val angleNote: BenchAngleNote? = null,
        ) : BenchState {
            /** 有函数抖到压不下去，采用这份结果就得用户自己拍板。 */
            val anyNoisy: Boolean get() = quality.values.any { it.noisy }

            /** 测的驱动和游戏要用的不是同一个，这份名次搬过去不成立。 */
            val driverMismatch: Boolean
                get() = angleNote == BenchAngleNote.SystemInsteadOfAngle ||
                    angleNote == BenchAngleNote.BorrowFailed
        }

        data class Failed(val message: String) : BenchState
    }

    /**
     * 跑分结束后关于驱动的那句话。三种都不是错误状态——错误走 [BenchState.Failed]——
     * 而是「你以为测的驱动」和「实际测的驱动」之间需要说明的差异。
     */
    enum class BenchAngleNote(@param:StringRes val messageRes: Int) {
        /** 配置会让游戏用 ANGLE，而这次（用户自己选的）测的是系统驱动。 */
        SystemInsteadOfAngle(R.string.md_bench_wrong_driver),

        /** 借了 ANGLE 但没加载成（来源损坏、被启动器更新掉了……），实测是系统驱动。 */
        BorrowFailed(R.string.md_bench_borrow_failed),

        /** 设备过不了 ANGLE 探测，借用被渲染器忽略；游戏同样会用系统驱动，结果依然有效。 */
        BorrowUnsupported(R.string.md_bench_borrow_unsupported),
    }

    private val mutableBenchState = MutableStateFlow<BenchState?>(null)
    val benchState: StateFlow<BenchState?> = mutableBenchState.asStateFlow()

    /**
     * MultiDraw 还是出厂那份顺序——没人调过，也没采用过跑分结果。
     *
     * 默认顺序是照着「一般来说什么快」定的，不是在这台设备上量出来的，所以首页可以轻轻
     * 提一句。一旦排序变成非默认（自己拖过，或采用了跑分），这条提示自己就消失了。
     */
    val multidrawUntuned: StateFlow<Boolean> = configStore.config
        .map { it != null && it.multidraw == MultidrawSettings.Default }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 借 ANGLE 是为了哪件事：跑分，还是查 MobileGlues 信息。 */
    sealed interface AngleUse {
        data class Bench(val target: BenchTarget) : AngleUse
        data object GlInfo : AngleUse
    }

    /**
     * 该选 ANGLE 来源了。
     *
     * 每一次都问，不因为上次信过就替他决定——载入的是另一个应用的原生代码，而那个应用
     * 随时可能被更新成别的东西。上次选的只是排在最前面并标出来，省一次找。
     */
    data class AngleSourcePrompt(
        val use: AngleUse,
        val sources: List<AngleSource>,
        val lastChosen: String?,
    )

    private val mutableAngleSourcePrompt = MutableStateFlow<AngleSourcePrompt?>(null)
    val angleSourcePrompt: StateFlow<AngleSourcePrompt?> = mutableAngleSourcePrompt.asStateFlow()

    /** 弹一次来源选择：上次选过的排最前，其余按发现顺序。 */
    private fun promptForAngle(use: AngleUse) {
        val last = pluginConfig.angleSourcePackage.value
        val found = AngleProvider.sources(context)
        mutableAngleSourcePrompt.value = AngleSourcePrompt(
            use = use,
            sources = found.sortedByDescending { it.packageName == last },
            lastChosen = last,
        )
    }

    /**
     * 配置最终会用 ANGLE 吗。
     *
     * 与 native 的判断保持一致：强制启用就是用，「尽可能启用」要设备支持才用——后者本 App
     * 判断不了（那要问 Vulkan 和 GPU 型号），所以按会用来算，宁可多问一次也别测错驱动。
     */
    private fun benchNeedsAngle(): Boolean = when (configStore.config.value?.angle) {
        AngleConfig.ForceEnable, AngleConfig.EnableIfPossible -> true
        else -> false
    }

    /**
     * 立即跑分。渲染器被 dlopen 进本进程、在同一块 GPU 上轮流测量每种实现，
     * 结束后弹出推荐排序，用户点「采用」才写入配置。
     *
     * 配置若要用 ANGLE，得先借到 ANGLE：它随启动器分发，本 App 里没有，而渲染器借不到时
     * 会不声不响退回系统驱动——那样测出来的名次搬进游戏里根本不成立。所以先问用户信任谁。
     *
     * native 侧默认花 8 秒把每个候选反复测上几十轮再取中位数，所以这里要边跑边报进度。
     */
    fun runMultidrawBench(target: BenchTarget) {
        if (mutableBenchState.value is BenchState.Running) return
        if (benchNeedsAngle()) {
            promptForAngle(AngleUse.Bench(target))
            return
        }
        startMultidrawBench(target, null)
    }

    /**
     * 从 adb 直接跑分,不经过界面。没有任何入口指向它——它存在是为了让一次跑分能被脚本
     * 驱动,而不是靠猜坐标点屏幕。
     *
     *   adb shell am start -n net.kdt.pojavlaunch.firefly.mobileglues/.MainActivity \
     *       --es mg_bench all            # 或某个函数名,见 MultidrawEntry.glFunction
     *       --es mg_angle borrow         # borrow=用记住的那个来源 / system=系统驱动
     *
     * 与 [runMultidrawBench] 的区别只有一处:它不弹来源选择框。自动化点不掉对话框,而
     * 这个入口的全部意义就是不需要有人在场。[angle] 为 borrow 时用上次记住的来源,没记住
     * 就退回系统驱动并在结果里如实写明——和用户自己选「用系统驱动测」是同一条路径。
     */
    fun runMultidrawBenchHeadless(entryName: String?, angle: String?) {
        if (mutableBenchState.value is BenchState.Running) return
        val target = entryName
            ?.takeUnless { it.isEmpty() || it.equals("all", ignoreCase = true) }
            ?.let { name -> MultidrawEntry.entries.firstOrNull { it.glFunction.equals(name, true) } }
            ?.let { BenchTarget.Entry(it) }
            ?: BenchTarget.AllEntries

        val borrow = angle == null || angle.equals("borrow", ignoreCase = true)
        val dir = if (borrow && benchNeedsAngle()) {
            val last = pluginConfig.angleSourcePackage.value
            val found = AngleProvider.sources(context)
            (found.firstOrNull { it.packageName == last } ?: found.firstOrNull())?.libraryDir
        } else {
            null
        }
        startMultidrawBench(target, dir)
    }

    /** 用户选定了这一次要信任的来源：记下它（下次排最前），然后把刚才拦下的事接着做。 */
    fun confirmAngleSource(source: AngleSource) {
        val pending = mutableAngleSourcePrompt.value ?: return
        pluginConfig.setAngleSourcePackage(source.packageName)
        mutableAngleSourcePrompt.value = null
        resumeWithAngle(pending.use, source.libraryDir)
    }

    /** 一个来源都不信（或一个都没有）：照样做，但结果会写明这是系统驱动上的。 */
    fun continueWithoutAngle() {
        val pending = mutableAngleSourcePrompt.value ?: return
        mutableAngleSourcePrompt.value = null
        resumeWithAngle(pending.use, null)
    }

    fun dismissAngleSourcePrompt() {
        mutableAngleSourcePrompt.value = null
    }

    private fun resumeWithAngle(use: AngleUse, angleDirectory: String?) {
        when (use) {
            is AngleUse.Bench -> startMultidrawBench(use.target, angleDirectory)
            is AngleUse.GlInfo -> startGlInfo(angleDirectory)
        }
    }

    private fun startMultidrawBench(target: BenchTarget, angleDirectory: String?) {
        if (mutableBenchState.value is BenchState.Running) return
        mutableBenchState.value = BenchState.Running(target)
        scope.launch {
            val report = runBenchWithBackoff(target, Constants.MG_DIRECTORY, angleDirectory)
            val rankings = when (target) {
                is BenchTarget.AllEntries -> MultidrawEntry.entries
                    .associateWith { MultidrawBenchAnalyzer.rankEntry(report, it) }
                    // 一个方案都没测出来的函数没什么可采用的——那份「排名」就是默认顺序本身。
                    .filterValues { ranking -> ranking.any { it.relativeCost != null } }
                is BenchTarget.Entry ->
                    mapOf(target.entry to MultidrawBenchAnalyzer.rankEntry(report, target.entry))
            }
            mutableBenchState.value = when {
                report.error != null -> BenchState.Failed(benchErrorMessage(report.error))
                rankings.isEmpty() -> BenchState.Failed(
                    context.getString(R.string.md_bench_failed, context.getString(R.string.md_bench_nothing)),
                )
                else -> BenchState.Done(
                    target = target,
                    rankings = rankings,
                    quality = report.quality.filterKeys { it in rankings },
                    angleNote = benchAngleNote(report, borrowed = angleDirectory != null),
                )
            }
        }
    }

    /**
     * 跑一趟；上下文被撑爆就换个更小的场景从头再跑一趟。
     *
     * 为什么退让必须发生在这一层，而不是渲染器内部：撑爆的不只是 GL 上下文，还有它底下
     * 那个 VkDevice。同一个进程里再建一个是碰运气，而 [MgQuery] 每次绑定都是一个全新的
     * 查询进程——干净是结构给的，不是驱动给的。
     *
     * 退让也一定是「重来」而不是「接着跑」：微秒数随场景规模变，两种规模的数字混在一份
     * 排名里就是拿两把尺子量同一件事。崩掉那一趟的结果整份丢掉。
     *
     * 上限只活在这一次点击里，不落盘。设备当时忙不忙、温度高不高都会挪动那条线，把某一
     * 次的坏运气记成这台机器的属性，往后每一次跑分都要替它背着。
     */
    private suspend fun runBenchWithBackoff(
        target: BenchTarget,
        mgDirectory: String,
        angleDirectory: String?,
    ): MultidrawBenchReport {
        var start = BENCH_START_SECTIONS
        var ceiling = 0                     // 0 = 还没撞到过天花板
        var report = runBenchOnce(mgDirectory, angleDirectory, start, ceiling)

        var retries = 0
        while (report.error == "context-lost" && retries < BENCH_MAX_BACKOFFS) {
            // 渲染器报的是「撑爆时场景多大」。它以下的第一档就是本次的上限，此后不再越过。
            val crashed = report.sections.takeIf { it > 0 } ?: start
            val next = crashed / 2
            if (next < BENCH_MIN_SECTIONS) break

            ++retries
            start = next
            ceiling = next
            mutableBenchState.value = BenchState.Running(target, retryingAtSections = next)
            report = runBenchOnce(mgDirectory, angleDirectory, start, ceiling)
        }
        return report
    }

    /** 一趟：绑一个查询进程、跑完、解绑（进程随之自杀）。 */
    private suspend fun runBenchOnce(
        mgDirectory: String,
        angleDirectory: String?,
        startSections: Int,
        maxSections: Int,
    ): MultidrawBenchReport = try {
        // 跑在一次性的查询进程里（见 MgQuery）：渲染器只在初次载入时读 MG_ANGLE_DIR
        // 和配置，本进程里跑第二次就永远是第一次的驱动。binder 调用是阻塞的，占的是
        // IO 线程；查询进程崩了（驱动崩溃）这边收到异常，兜成一份错误报告。
        mgQuery.use { query ->
            // coroutineScope 而不是外层的 scope：测量和轮询都是这一趟的孩子，函数
            // 返回时它们必须已经收干净——退让重来会再起一趟，两趟的轮询协程重叠着
            // 写同一个 benchState 就会互相盖掉。
            coroutineScope {
                // 这里必须自己吞掉异常。async 一旦失败就立刻把异常抛给父 scope，
                // await() 外面的 try/catch 接到的只是副本——父 scope 已经炸了，
                // 整个 App 跟着崩。查询进程本来就可能死（驱动在里面崩溃正是它存在
                // 的理由之一），所以失败在这里就变成一份错误报告。
                val measuring = async(Dispatchers.IO) {
                    runCatching {
                        MultidrawBenchReport.parse(
                            query.runBench(
                                mgDirectory,
                                angleDirectory.orEmpty(),
                                startSections,
                                maxSections,
                            ),
                        )
                    }.getOrElse { MultidrawBenchReport(emptyMap(), error = queryFailure(it)) }
                }
                val polling = launch {
                    while (isActive) {
                        delay(BENCH_PROGRESS_POLL_MS)
                        val raw = withContext(Dispatchers.IO) {
                            runCatching { query.benchProgress() }.getOrDefault(-1)
                        }
                        val progress = MGBench.decodeProgress(raw) ?: continue
                        val running = mutableBenchState.value as? BenchState.Running ?: break
                        mutableBenchState.value = running.copy(
                            progress = progress.fraction,
                            attempt = progress.attempt.coerceIn(1, BENCH_MAX_ATTEMPTS),
                        )
                    }
                }
                try {
                    measuring.await()
                } finally {
                    polling.cancel()
                }
            }
        }
    } catch (e: Exception) {
        MultidrawBenchReport(timings = emptyMap(), error = queryFailure(e))
    }

    /**
     * 渲染器报的错翻成人话。
     *
     * "context-lost" 是其中最要紧的一个：驱动在测量途中把上下文丢了，之后每次查询都
     * 回零、每次绘制都成空操作，所以那之后的数字是虚构的，而依赖前置条件的方案会因为
     * 读到零而"回退"，看上去就像设备不支持。跑分因此整体作废，而不是交出半份结果。
     *
     * 走到这里说明退让也没救回来：[runBenchWithBackoff] 已经在更小的场景上重来过了。
     */
    private fun benchErrorMessage(error: String): String = when (error) {
        "context-lost" -> context.getString(R.string.md_bench_context_lost)
        else -> context.getString(R.string.md_bench_failed, error)
    }

    /**
     * 查询进程失败时给用户看的那句话。
     *
     * DeadObjectException 是最要紧的一种：查询进程没了，几乎总是渲染器在里面崩了。
     * 把它说成人话，而不是把异常类名甩给用户——而且这恰恰是隔离进程挣来的东西，
     * 换在以前这一下会把整个 App 带走。
     */
    private fun queryFailure(e: Throwable): String = when (e) {
        is android.os.DeadObjectException -> context.getString(R.string.md_bench_process_died)
        else -> e.message ?: e.javaClass.simpleName
    }

    /**
     * 这次测量与 ANGLE 的关系需要说明吗？
     *
     * 依据是渲染器的自报而不是本 App 的意图：借没借是意图，加载没加载上是事实，
     * 两者对不上的时候恰恰是最需要说明的时候。
     */
    private fun benchAngleNote(report: MultidrawBenchReport, borrowed: Boolean): BenchAngleNote? = when {
        borrowed && report.angleInUse -> null // 借了也用上了，如预期
        borrowed && report.angleConfigured == AngleConfig.EnableIfPossible.wire && !report.angleSupported ->
            BenchAngleNote.BorrowUnsupported
        borrowed -> BenchAngleNote.BorrowFailed
        report.wrongDriver -> BenchAngleNote.SystemInsteadOfAngle
        else -> null
    }

    /**
     * 采用跑分给出的排序：每个测出结果的函数各自启用例外，写入自己那份顺序。
     *
     * 全局排序原样不动——它是「没有单独说法的函数走这里」的兜底，跑分说不了它的话。
     */
    fun adoptBenchResult() {
        val done = mutableBenchState.value as? BenchState.Done ?: return
        update { config ->
            val multidraw = done.rankings.entries.fold(config.multidraw) { settings, (entry, ranking) ->
                settings.withExceptionOrder(entry, ranking.map { it.item })
            }
            config.copy(multidraw = multidraw)
        }
        mutableBenchState.value = null
    }

    fun dismissBench() {
        if (mutableBenchState.value is BenchState.Running) return // 跑分中断没有意义，让它跑完
        mutableBenchState.value = null
    }

    // ---- 首页配置摘要 ----

    /**
     * 「ANGLE 尽可能启用 · 缓存 32 MiB」式的一行只读摘要。
     *
     * 取值本身（「尽可能启用」「不启用」）离开设置页就没有意义了，所以这里带上是谁的取值；
     * GL 版本只在被自定义过的时候才出现——默认那一档说了等于没说。
     */
    fun configSummary(config: MGConfig): String = listOfNotNull(
        context.getString(R.string.home_summary_angle, config.angle.label(context)),
        config.glVersion.takeIf { it != GlVersion.Default }?.label(context)?.toString(),
        context.getString(
            R.string.home_summary_cache,
            (config.glslCache as? GlslCacheSize.Limited)?.let {
                context.getString(R.string.option_glsl_cache_value, it.mebibytes)
            } ?: context.getString(R.string.option_glsl_cache_off),
        ),
    ).joinToString(" · ")

    /** 与滑块保持同一套单位（MiB / KiB），不用 SI 的 MB。 */
    fun formatCacheSize(bytes: Long): String {
        val mebibytes = bytes / (1024.0 * 1024.0)
        return if (mebibytes >= 1.0) {
            context.getString(R.string.option_glsl_cache_size_mib, mebibytes)
        } else {
            context.getString(R.string.option_glsl_cache_size_kib, bytes / 1024.0)
        }
    }

    // ---- 外链 ----

    private val mutableRepoPicker = MutableStateFlow(false)

    /** 三个仓库的选择框。 */
    val repoPicker: StateFlow<Boolean> = mutableRepoPicker.asStateFlow()

    fun openSourceRepositories() {
        mutableRepoPicker.value = true
    }

    fun dismissRepoPicker() {
        mutableRepoPicker.value = false
    }

    fun onRepositorySelected(link: LinkEntry) {
        openUrl(link.url)
        mutableRepoPicker.value = false
    }

    fun openThirdPartyComponent(component: ThirdPartyComponent) = openUrl(component.url)

    fun openContributor(contributor: Contributor) =
        openUrl("https://github.com/${contributor.login}")

    private fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    val appVersionName: String get() = BuildConfig.VERSION_NAME

    // ---- 赞助弹窗 ----

    private val mutableSponsorPrompt = MutableStateFlow<SponsorPromptState?>(null)
    val sponsorPrompt: StateFlow<SponsorPromptState?> = mutableSponsorPrompt.asStateFlow()

    /**
     * 首页出现时调用。每个进程最多弹一次；判定见 [SponsorPrompt]。
     *
     * 启动次数要去 MG 目录里读（native 库记的），所以未授权时问不出结果——那就先不问，
     * 等用户授权之后自然会再走到这里。
     */
    fun maybeShowSponsorPrompt() {
        if (app.sponsorPromptedThisProcess) return
        if (!pluginConfig.privacyAccepted.value) return
        if (pluginConfig.donated.value) return
        val storage = auth.state.value.storage ?: return

        scope.launch {
            val stats = withContext(Dispatchers.IO) { MgStats.parse(storage.readStats()) }
            if (app.sponsorPromptedThisProcess) return@launch
            if (!SponsorPrompt.shouldPrompt(
                    stats.launchCount,
                    pluginConfig.lastSponsorPromptAt,
                    pluginConfig.donated.value,
                )
            ) {
                return@launch
            }
            app.sponsorPromptedThisProcess = true
            // 记在弹出的这一刻，而不是等用户点某个按钮：点了「下次再说」就走人的话，
            // 下次启动会立刻又弹一遍。
            pluginConfig.markSponsorPromptedAt(stats.launchCount)
            mutableSponsorPrompt.value = SponsorPromptState.Ask(stats.launchCount)
        }
    }

    private val mutableSponsorPicker = MutableStateFlow(false)

    /** 赞助渠道选择框。信息页和赞助弹窗都开这一个。 */
    val sponsorPicker: StateFlow<Boolean> = mutableSponsorPicker.asStateFlow()

    /**
     * 选完渠道之后要不要接着问「已经捐赠了吗」。
     *
     * 从信息页点进来的不问——那是用户自己想去看看，不是我们开口要的；
     * 从赞助弹窗点进来的才问，那句话是那次询问的下半截。
     */
    private var askAfterPicking = false

    /** 信息页的「赞助我们」。 */
    fun openSponsorChannels() {
        askAfterPicking = false
        mutableSponsorPicker.value = true
    }

    /** 「去支持一下」：先让用户挑渠道，回来再委婉地二次确认。 */
    fun onSponsorDonate() {
        askAfterPicking = true
        mutableSponsorPrompt.value = null
        mutableSponsorPicker.value = true
    }

    fun onSponsorChannelSelected(channel: LinkEntry) {
        openUrl(channel.url)
        mutableSponsorPicker.value = false
        if (askAfterPicking) {
            askAfterPicking = false
            mutableSponsorPrompt.value = SponsorPromptState.Confirm
        }
    }

    /** 没挑就退出来了，那就什么都没发生——别追着问「捐了吗」。 */
    fun dismissSponsorPicker() {
        askAfterPicking = false
        mutableSponsorPicker.value = false
    }

    fun onSponsorLater() {
        mutableSponsorPrompt.value = null
    }

    /** 「已捐赠」：记录并永不再弹。 */
    fun onSponsorDonated() {
        pluginConfig.markDonated()
        mutableSponsorPrompt.value = null
    }

    fun onSponsorNotYet() {
        mutableSponsorPrompt.value = null
    }

    // ---- GL 信息 ----

    private val mutableGlInfo = MutableStateFlow<String?>(null)
    val glInfo: StateFlow<String?> = mutableGlInfo.asStateFlow()

    private val mutableGlInfoLoading = MutableStateFlow(false)
    val glInfoLoading: StateFlow<Boolean> = mutableGlInfoLoading.asStateFlow()

    /**
     * 这份 GL 信息是经谁读出来的。
     *
     * [Borrowed] 与 [BorrowIneffective] 的分界来自渲染器的自报（信息文本里的
     * "ANGLE in use" 一行），不是「传没传目录」——传了目录而设备不支持、或加载失败时，
     * 渲染器照样退回系统驱动，此时页面必须说实话。老渲染器没有这行自报，只好按意图归类。
     */
    enum class GlInfoAngle {
        /** 没借，读的就是系统驱动。 */
        System,

        /** 借了，渲染器确认用上了。 */
        Borrowed,

        /** 借了，但没用上（设备不支持或加载失败），实际读的是系统驱动。 */
        BorrowIneffective,
    }

    private val mutableGlInfoAngle = MutableStateFlow(GlInfoAngle.System)

    /** 当前这份信息经谁读出。 */
    val glInfoAngle: StateFlow<GlInfoAngle> = mutableGlInfoAngle.asStateFlow()

    /**
     * 配置要 ANGLE，而当前这份信息是在系统驱动上查的——它讲的不是游戏里的那个驱动。
     *
     * 进页面不弹窗：借 ANGLE 是把别的应用的原生代码载进查询进程，这种事不该在用户只想
     * 看一眼信息的时候自己发生。所以先照实查一份、把话说明白，要不要借由用户点。
     * 借过而未生效（[GlInfoAngle.BorrowIneffective]）不再劝借：再借一次也是同样下场。
     */
    val glInfoNeedsAngle: StateFlow<Boolean> =
        combine(glInfo, glInfoAngle, configStore.config) { info, angle, _ ->
            info != null && angle == GlInfoAngle.System && benchNeedsAngle()
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /** 每次进入 GL 信息页都重新查询（渲染器库可能刚被游戏更新过）。 */
    fun loadGlInfo() = startGlInfo(null)

    /** 「借 ANGLE 重新查一次」：先问信任谁，再查。 */
    fun reloadGlInfoWithAngle() {
        if (mutableGlInfoLoading.value) return
        promptForAngle(AngleUse.GlInfo)
    }

    private fun startGlInfo(angleDirectory: String?) {
        if (mutableGlInfoLoading.value) return
        mutableGlInfoLoading.value = true
        mutableGlInfo.value = null
        scope.launch {
            // 一次性查询进程（见 MgQuery）：不然本进程里第一次查询的驱动会钉死后面每一次。
            val info = try {
                mgQuery.use { query ->
                    withContext(Dispatchers.IO) {
                        query.glInfo(Constants.MG_DIRECTORY, angleDirectory.orEmpty())
                    }
                }
            } catch (e: Exception) {
                "Error: ${queryFailure(e)}"
            }
            mutableGlInfo.value = info
            mutableGlInfoAngle.value = when {
                angleDirectory == null -> GlInfoAngle.System
                // 渲染器自报了用没用上，照它说的办。
                info.contains("ANGLE in use: yes") -> GlInfoAngle.Borrowed
                info.contains("ANGLE in use:") -> GlInfoAngle.BorrowIneffective
                // 老渲染器没有自报，只能按意图归类——历史行为，宁可标成借到。
                else -> GlInfoAngle.Borrowed
            }
            mutableGlInfoLoading.value = false
        }
    }

    // ---- 移除 MobileGlues ----

    private val mutableRemoving = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = mutableRemoving.asStateFlow()

    private val mutableFarewell = MutableStateFlow<Farewell?>(null)

    /**
     * 收尾对话框。
     *
     * 撤销和移除都会走到这里：用户刚刚收回了让这个 App 工作所需要的东西，界面继续留着
     * 只会立刻把隐私政策弹窗糊到他脸上。谢一声然后退出，是这时候唯一体面的收场。
     */
    val farewell: StateFlow<Farewell?> = mutableFarewell.asStateFlow()

    fun resetMobileGluesData() {
        mutableResetPrompt.value = false
        scope.launch {
            if (!confirm(
                    R.string.remove_mg_files_message,
                    REMOVE_COOLDOWN_SECONDS,
                    messageIsHtml = true,
                )
            ) {
                return@launch
            }
            mutableRemoving.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val storage = auth.state.value.storage
                        ?: throw java.io.IOException("MG directory is not authorized")
                    storage.deleteAll()
                    app.cacheExporter.clear()
                }
            }
            mutableRemoving.value = false
            result
                .onSuccess {
                    configStore.forget()
                    ensureConfigLoaded()
                    snackbar(context.getString(R.string.reset_complete_message))
                }
                .onFailure {
                    snackbar(
                        context.getString(
                        R.string.reset_failed,
                            it.message ?: it.javaClass.simpleName,
                        ),
                    )
                }
        }
    }

    // ---- 撤销 / 重置 ----

    private val mutableResetPrompt = MutableStateFlow(false)

    /**
     * 「撤销授权或重置」的选择框。
     *
     * 两件事是包含关系而不是并列关系——删文件必然要先有访问权，撤了权限就删不动了——
     * 所以入口只有一个，进去再选做到哪一步，而不是两个会互相锁死的按钮。
     */
    val resetPrompt: StateFlow<Boolean> = mutableResetPrompt.asStateFlow()

    fun openResetPrompt() {
        mutableResetPrompt.value = true
    }

    fun dismissResetPrompt() {
        mutableResetPrompt.value = false
    }

    /**
     * 把用户给过的两样东西一起收回：存储授权，以及对隐私政策的同意。
     *
     * 不删任何文件——MG 目录里的配置原封不动，用户重新同意并授权之后还是老样子。
     * 收回之后隐私政策弹窗会立刻回来：同意是使用这个 App 的前提，撤了就得重新表态。
     */
    fun revokeAuthorization() {
        mutableResetPrompt.value = false
        scope.launch {
            if (!confirm(R.string.warning_revoke_authorization)) return@launch
            auth.revoke()
            pluginConfig.revokePrivacyAcceptance()
            // MG 目录里的文件是用户的，留着；缓存里这份副本是我们自己拷的，权限都收回了就别留了。
            withContext(Dispatchers.IO) { app.cacheExporter.clear() }
            mutableFarewell.value = Farewell.Revoked
        }
    }

    /** 收尾对话框的唯一出口：退出应用。 */
    fun exitAfterFarewell() = launcher.exitApp()

    fun destroy() = scope.cancel()

    companion object {

        const val CUSTOM_GL_VERSION_COOLDOWN_SECONDS = 41
        const val REMOVE_COOLDOWN_SECONDS = 10

        /** 跑分进度的轮询间隔。native 那边是个原子计数器，问一次几乎不要钱。 */
        private const val BENCH_PROGRESS_POLL_MS = 100L

        /** 与 native 的 BENCH_MAX_ATTEMPTS 对齐：抖得压不下去时最多重测这么多次。 */
        const val BENCH_MAX_ATTEMPTS = 4

        /**
         * 起手的场景规模，与 native 的 BENCH_START_SECTIONS 对齐。
         *
         * 每次点击都从这里起步、从这里重新往上探，不记上一次探到哪。
         */
        private const val BENCH_START_SECTIONS = 256

        /** 小到这个地步还撑爆，问题就不在场景大小上了。对齐 native 的 BENCH_MIN_SECTIONS。 */
        private const val BENCH_MIN_SECTIONS = 32

        /**
         * 最多这样退让几次。
         *
         * 天花板不是一条硬线——同一台机器忙起来能提前几百个 section 撞上——所以退一步之后
         * 再崩一次是正常的。三次之后还崩就不是场景大小的事了，报错比接着试更诚实，何况
         * 每一次都要让用户干等十几秒。
         */
        private const val BENCH_MAX_BACKOFFS = 3
    }
}


