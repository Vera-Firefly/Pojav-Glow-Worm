package net.kdt.pojavlaunch.firefly.mobileglues.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用户选择的存储授权方式。 */
enum class AuthMethod(val key: String) {
    /** 「所有文件访问」权限（Android 11+）。 */
    AllFiles("all_files"),

    /** SAF 目录授权，用户自己挑出 MG 目录。 */
    Saf("saf"),

    /** Android 10 及以下的旧版 READ/WRITE 运行时权限。 */
    Legacy("legacy");

    companion object {
        fun ofKey(key: String?): AuthMethod? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 赞助弹窗的判定逻辑。独立成纯函数是为了让「每 20 次问一次」这件事可以被单测盯住。
 *
 * 计数来自 native 库写在 `MG/stats.json` 里的启动次数——一次启动是一局游戏加载渲染器，
 * 不是用户打开一次设置页。本 App 只记住「上次是在第几次启动时问过」，两者相差够远才再问：
 * 用差值而不是取模，是因为这个计数不由本 App 掌控，它可能一次跳很多（用户连开几局），
 * 取模会正好跨过那个点而永远不弹。
 */
object SponsorPrompt {
    const val INTERVAL = 20

    fun shouldPrompt(launchCount: Int, lastPromptedAt: Int, donated: Boolean): Boolean =
        !donated && launchCount - lastPromptedAt >= INTERVAL
}

/**
 * 本 App 自己的本地设置（与 MobileGlues 的 config.json 无关）。
 *
 * 用 SharedPreferences、不引新依赖。它记录的是「界面风格、授权方式、启动次数」这类
 * 本地偏好，所以必须放在存储权限门之上——未授权时设置页也不能是空白。
 */
class PluginConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableAuthMethod =
        MutableStateFlow(AuthMethod.ofKey(prefs.getString(KEY_AUTH_METHOD, null)))

    /** 用户上次选择的授权方式；`null` = 还没选过。授权本身是否仍然有效由 AuthController 判断。 */
    val authMethod: StateFlow<AuthMethod?> = mutableAuthMethod.asStateFlow()

    fun setAuthMethod(method: AuthMethod?) {
        prefs.edit {
            if (method == null) remove(KEY_AUTH_METHOD) else putString(KEY_AUTH_METHOD, method.key)
        }
        mutableAuthMethod.value = method
    }

    /** SAF 模式下持久化过的 tree URI。 */
    var safTreeUri: String?
        get() = prefs.getString(KEY_SAF_TREE_URI, null)
        set(value) {
            prefs.edit {
                if (value == null) remove(KEY_SAF_TREE_URI) else putString(KEY_SAF_TREE_URI, value)
            }
        }

    /**
     * 上次弹赞助窗时的启动次数。
     *
     * 启动次数本身由 native 库记在 `MG/stats.json`，本 App 不再自己数——打开设置页
     * 不是一次启动。这里只记「问过的那个点」，够远了再问下一次。
     */
    val lastSponsorPromptAt: Int get() = prefs.getInt(KEY_LAST_SPONSOR_PROMPT_AT, 0)

    fun markSponsorPromptedAt(launchCount: Int) {
        prefs.edit { putInt(KEY_LAST_SPONSOR_PROMPT_AT, launchCount) }
    }

    private val mutablePrivacyAccepted =
        MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false))

    /**
     * 用户同意过隐私政策。
     *
     * 首次启动必须先问一次——这个 App 要的是「所有文件访问」这种分量的权限，
     * 在开口要之前应当先讲清楚自己会碰什么。
     */
    val privacyAccepted: StateFlow<Boolean> = mutablePrivacyAccepted.asStateFlow()

    fun markPrivacyAccepted() {
        prefs.edit { putBoolean(KEY_PRIVACY_ACCEPTED, true) }
        mutablePrivacyAccepted.value = true
    }

    fun revokePrivacyAcceptance() {
        prefs.edit { putBoolean(KEY_PRIVACY_ACCEPTED, false) }
        mutablePrivacyAccepted.value = false
    }

    private val mutableAngleSource =
        MutableStateFlow(prefs.getString(KEY_ANGLE_SOURCE, null))

    /**
     * 用户点头信任、用来借 ANGLE 的那个启动器的包名；`null` = 还没选过。
     *
     * 存包名而不是路径：路径里带着安装时生成的一串随机码，启动器一更新就变了。
     */
    val angleSourcePackage: StateFlow<String?> = mutableAngleSource.asStateFlow()

    fun setAngleSourcePackage(packageName: String?) {
        prefs.edit {
            if (packageName == null) remove(KEY_ANGLE_SOURCE) else putString(KEY_ANGLE_SOURCE, packageName)
        }
        mutableAngleSource.value = packageName
    }

    private val mutableDonated = MutableStateFlow(prefs.getBoolean(KEY_DONATED, false))

    /** 用户说过「已经捐赠了」。一旦为 true，赞助弹窗永不再出现。 */
    val donated: StateFlow<Boolean> = mutableDonated.asStateFlow()

    fun markDonated() {
        prefs.edit { putBoolean(KEY_DONATED, true) }
        mutableDonated.value = true
    }

    /**
     * 清空全部本地偏好，回到刚安装的样子。
     *
     * 只在「撤销并删除全部文件」那条路上用：既然承诺了删干净，就不能把授权方式、
     * 上次询问赞助的次数、是否已捐赠这些留在设备上。
     *
     * 界面风格只清存储、不动内存里的当前值：这时候收尾对话框正开着，把皮肤当场切回
     * 默认值只会让人以为程序出错了。下次启动读到的就是默认值。
     */
    fun clearAll() {
        prefs.edit { clear() }
        mutableAuthMethod.value = null
        mutablePrivacyAccepted.value = false
        mutableDonated.value = false
        mutableAngleSource.value = null
    }

    private companion object {
        const val PREFS_NAME = "plugin_config"
        const val KEY_AUTH_METHOD = "auth_method"
        const val KEY_SAF_TREE_URI = "saf_tree_uri"
        const val KEY_LAST_SPONSOR_PROMPT_AT = "last_sponsor_prompt_at"
        const val KEY_DONATED = "donated"
        const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        const val KEY_ANGLE_SOURCE = "angle_source_package"
    }
}


