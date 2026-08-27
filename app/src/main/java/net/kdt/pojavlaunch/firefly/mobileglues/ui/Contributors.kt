package net.kdt.pojavlaunch.firefly.mobileglues.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import net.kdt.pojavlaunch.firefly.R

/**
 * 一位贡献者。头像随包内置，不是运行时下载的——本应用没有网络权限。
 */
data class Contributor(
    val login: String,
    @param:DrawableRes val avatar: Int,
)

/** 一个仓库的贡献者名单。 */
data class ContributorGroup(
    @param:StringRes val title: Int,
    val contributors: List<Contributor>,
)

/**
 * 三个仓库的贡献者，按提交数从多到少。
 *
 * 这份名单和头像由 tools/fetch_contributors.py 生成，不要手改：改了下次刷新就没了。
 * 也正因为是生成的，它停在生成的那一刻——把它做成实时的需要 INTERNET 权限，
 * 而隐私政策里承诺了没有这个权限，一份致谢名单不值得拿那条承诺去换。
 */
val ContributorGroups = listOf(
    // MobileGlues
    ContributorGroup(
        R.string.third_party_renderer,
        listOf(
            Contributor("BZLZHH", R.drawable.avatar_bzlzhh), // 256
            Contributor("Swung0x48", R.drawable.avatar_swung0x48), // 154
            Contributor("Tungstend", R.drawable.avatar_tungstend), // 78
            Contributor("alexytomi", R.drawable.avatar_alexytomi), // 5
            Contributor("youfeng11", R.drawable.avatar_youfeng11), // 2
            Contributor("Ahmet53535353", R.drawable.avatar_ahmet53535353), // 1
            Contributor("crystall1nedev", R.drawable.avatar_crystall1nedev), // 1
        ),
    ),
    // MobileGlues-plugin
    ContributorGroup(
        R.string.third_party_plugin,
        listOf(
            Contributor("BZLZHH", R.drawable.avatar_bzlzhh), // 89
            Contributor("Swung0x48", R.drawable.avatar_swung0x48), // 74
            Contributor("youfeng11", R.drawable.avatar_youfeng11), // 57
            Contributor("Tungstend", R.drawable.avatar_tungstend), // 40
            Contributor("ShirosakiMio", R.drawable.avatar_shirosakimio), // 1
            Contributor("MovTery", R.drawable.avatar_movtery), // 1
        ),
    ),
    // MobileGlues-release
    ContributorGroup(
        R.string.repo_release,
        listOf(
            Contributor("Swung0x48", R.drawable.avatar_swung0x48), // 116
            Contributor("chy2240", R.drawable.avatar_chy2240), // 54
            Contributor("BZLZHH", R.drawable.avatar_bzlzhh), // 34
            Contributor("lgc2333", R.drawable.avatar_lgc2333), // 26
            Contributor("Evsdrg", R.drawable.avatar_evsdrg), // 23
            Contributor("usernotfound999", R.drawable.avatar_usernotfound999), // 20
            Contributor("HappyDIY", R.drawable.avatar_happydiy), // 10
            Contributor("xiaoliyuanpp", R.drawable.avatar_xiaoliyuanpp), // 10
            Contributor("qisumei", R.drawable.avatar_qisumei), // 7
            Contributor("Mo-J8X", R.drawable.avatar_mo_j8x), // 4
            Contributor("ssbtt", R.drawable.avatar_ssbtt), // 3
            Contributor("dontknowhy", R.drawable.avatar_dontknowhy), // 3
            Contributor("TNTyep520", R.drawable.avatar_tntyep520), // 3
            Contributor("Liufeng258", R.drawable.avatar_liufeng258), // 3
            Contributor("ConfectionaryQwQ", R.drawable.avatar_confectionaryqwq), // 3
            Contributor("DHJComical", R.drawable.avatar_dhjcomical), // 3
            Contributor("ApartTUSITU", R.drawable.avatar_aparttusitu), // 3
            Contributor("Milk13995", R.drawable.avatar_milk13995), // 2
            Contributor("Nnkshv", R.drawable.avatar_nnkshv), // 2
            Contributor("Tungstend", R.drawable.avatar_tungstend), // 2
            Contributor("laoliu114514", R.drawable.avatar_laoliu114514), // 2
            Contributor("wyj5211", R.drawable.avatar_wyj5211), // 2
            Contributor("zhongjiening", R.drawable.avatar_zhongjiening), // 2
            Contributor("zheshiqunzhu", R.drawable.avatar_zheshiqunzhu), // 1
            Contributor("EldenThrone588", R.drawable.avatar_eldenthrone588), // 1
            Contributor("leemwood", R.drawable.avatar_leemwood), // 1
            Contributor("yzcthinking", R.drawable.avatar_yzcthinking), // 1
        ),
    ),
)


