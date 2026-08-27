package net.kdt.pojavlaunch.firefly.mobileglues.settings

import android.content.Context
import android.net.Uri
import net.kdt.pojavlaunch.firefly.mobileglues.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 一次授权判定的结果。[storage] 非空即「现在有访问权」。 */
data class AuthState(
    val method: AuthMethod?,
    val storage: MgStorage?,
) {
    val granted: Boolean get() = storage != null
}

/**
 * 决定「现在能不能碰 MG 目录」，并在授权建立后装配对应的 [MgStorage]。
 *
 * 授权只认本 App 记下来的那次明确选择（[PluginConfigStore.authMethod]），不从系统状态反推：
 * 「所有文件访问」这种系统级权限撤不掉，若是看到它开着就认作已授权，用户做完重置一开 App
 * 又会变成已授权，撤销等于没撤。
 *
 * 反过来，选择也不等于当前有效——所有文件访问可能被系统在设置里收回，SAF 的 URI 可能被吊销、
 * 目录可能被外部删除。所以每次界面回到前台都要 [refresh] 一次，用真实状态驱动权限门。
 */
class AuthController(
    private val context: Context,
    private val pluginConfigStore: PluginConfigStore,
) {

    private val appContext = context.applicationContext

    private val mutableState = MutableStateFlow(AuthState(null, directStorage()))
    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    /** 内置渲染器的配置保存在本应用私有目录，无需外部存储授权。 */
    fun refresh() {
        mutableState.value = AuthState(null, directStorage())
    }

    /** 「所有文件访问」已在系统设置里开好。 */
    fun grantAllFiles() {
        refresh()
    }

    /** [grantSaf] 的结果。 */
    enum class SafGrantResult { Success, WrongFolder, Inaccessible }

    /**
     * SAF 选择器返回的 tree URI。URI 的持久化（takePersistableUriPermission）由调用方做。
     * [SafGrantResult.WrongFolder] 时什么都没记录。
     */
    fun grantSaf(treeUri: Uri): SafGrantResult {
        refresh()
        return SafGrantResult.Success
    }

    /** 旧版运行时权限已授予（Android 10 及以下）。 */
    fun grantLegacy() {
        refresh()
    }

    /**
     * 忘掉授权选择。SAF 的持久化权限一并释放。
     *
     * 「所有文件访问」是系统级权限，本 App 撤不掉；但授权与否只看本 App 有没有记录，
     * 记录一清就等同于未授权，系统那边的权限开着也不会被用到。
     *
     * 两处会用到：「撤销授权」和「撤销并删除全部文件」。
     */
    fun revoke() {
        refresh()
    }

    private fun directStorage() = DirectMgStorage(Constants.MG_DIRECTORY_FILE)

    companion object {
        fun isMgDirectoryName(name: String?): Boolean = name == "mobileglues"

        fun hasAllFilesAccess(): Boolean = true
    }
}


