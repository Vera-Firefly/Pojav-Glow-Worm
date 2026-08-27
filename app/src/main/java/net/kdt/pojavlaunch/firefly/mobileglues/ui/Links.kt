package net.kdt.pojavlaunch.firefly.mobileglues.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.kdt.pojavlaunch.firefly.R

/**
 * 一条外链。
 *
 * [label] 说这是什么，[url] 原样展示——用户点下去之前有权知道要去哪里。
 */
data class LinkEntry(val label: String, val url: String)

/**
 * 赞助渠道。
 *
 * 项目账号排在最前，然后按 [R.string.info_author] 的顺序列出三位开发者各自的收款页——
 * 这是一份名单，不是一个排行榜，顺序就该和署名一致。爱发电有三个域名在用、收款方各不
 * 相同，所以网址必须写出来。
 */
val SponsorChannels = listOf(
    LinkEntry("爱发电 · MobileGlues", "https://afdian.com/a/MobileGlues"),
    LinkEntry("Buy Me a Coffee · Swung", "https://www.buymeacoffee.com/Swung0x48"),
    LinkEntry("爱发电 · BZLZHH", "https://www.ifdian.net/a/bzlzhh"),
    LinkEntry("爱发电 · Tungsten", "https://afdian.net/a/tungs"),
)

/** 三个仓库：发行、渲染器本体、以及你正在用的这个插件。 */
@Composable
fun sourceRepositories(): List<LinkEntry> = listOf(
    LinkEntry(
        stringResource(R.string.repo_release),
        "https://github.com/MobileGL-Dev/MobileGlues-release",
    ),
    LinkEntry(
        stringResource(R.string.repo_renderer),
        "https://github.com/MobileGL-Dev/MobileGlues",
    ),
    LinkEntry(
        stringResource(R.string.repo_plugin),
        "https://github.com/MobileGL-Dev/MobileGlues-plugin",
    ),
)

/**
 * 一个第三方开源组件。
 *
 * [license] 用许可证本来的英文名字，不翻译：那是法律文件的名称，译名不具备同等效力。
 */
data class ThirdPartyComponent(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

/** 第三方组件分成两组：渲染器用的，和这个插件用的。 */
data class ThirdPartyGroup(
    @param:StringRes val title: Int,
    val components: List<ThirdPartyComponent>,
)

/**
 * 用到的全部第三方开源项目。
 *
 * 分成「渲染器」和「插件」两组，因为它们是两个仓库、两套构建：用户看到 SPIRV-Cross
 * 的时候，应该同时知道它是被游戏里那个 .so 用的，而不是被这个设置界面用的。
 */
val ThirdPartyGroups = listOf(
    ThirdPartyGroup(
        R.string.third_party_renderer,
        listOf(
            ThirdPartyComponent(
                "SPIRV-Cross", "KhronosGroup", "Apache License 2.0",
                "https://github.com/KhronosGroup/SPIRV-Cross",
            ),
            ThirdPartyComponent(
                "glslang", "KhronosGroup", "Various Licenses",
                "https://github.com/KhronosGroup/glslang",
            ),
            ThirdPartyComponent(
                "cJSON", "DaveGamble", "MIT License",
                "https://github.com/DaveGamble/cJSON",
            ),
            ThirdPartyComponent(
                "FidelityFX-FSR", "AMD", "MIT License",
                "https://github.com/GPUOpen-Effects/FidelityFX-FSR",
            ),
            ThirdPartyComponent(
                "Perfetto", "Google", "Apache License 2.0",
                "https://github.com/google/perfetto",
            ),
            ThirdPartyComponent(
                "xxHash", "Yann Collet", "BSD 2-Clause License",
                "https://github.com/Cyan4973/xxHash",
            ),
            // 用的是 MobileGL-Dev 的 fork：上游 2018 年起无人维护，而它的素数表
            // 是个 size_t[]，其中三分之二大于 2^32，导致 32 位构建连头文件都包不进来。
            // 署名仍归原作者，链接指向实际编译进去的那一份。
            ThirdPartyComponent(
                "flat_hash_map", "Malte Skarupke", "Boost Software License 1.0",
                "https://github.com/MobileGL-Dev/flat_hash_map",
            ),
        ),
    ),
    ThirdPartyGroup(
        R.string.third_party_plugin,
        listOf(
            ThirdPartyComponent(
                "Jetpack Compose", "Android Open Source Project", "Apache License 2.0",
                "https://developer.android.com/jetpack/compose",
            ),
            ThirdPartyComponent(
                "AndroidX", "Android Open Source Project", "Apache License 2.0",
                "https://developer.android.com/jetpack/androidx",
            ),
            ThirdPartyComponent(
                "Miuix", "compose-miuix-ui", "Apache License 2.0",
                "https://github.com/compose-miuix-ui/miuix",
            ),
            ThirdPartyComponent(
                "kotlinx.coroutines", "JetBrains", "Apache License 2.0",
                "https://github.com/Kotlin/kotlinx.coroutines",
            ),
            ThirdPartyComponent(
                "Gson", "Google", "Apache License 2.0",
                "https://github.com/google/gson",
            ),
        ),
    ),
)


