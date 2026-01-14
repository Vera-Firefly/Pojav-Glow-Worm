package com.movtery.feature.mod.parser

enum class AllModCheckSettings(
    val prefKey: String,      // Pref 存储的键, ZL结构与PGW差别过大且修改困难, 因此使用Pref简单存储
    var defaultValue: String = "0"  // 默认值
) {
    TOUCH_CONTROLLER("modCheckTouchController"),
    SODIUM_OR_EMBEDDIUM("modCheckSodiumOrEmbeddium"),
    PHYSICS_MOD("modCheckPhysics"),
    MCEF("modCheckMCEF"),
    VALKYRIEN_SKIES("modCheckValkyrienSkies"),
    YES_STEVE_MODEL("modCheckYesSteveModel"),
    IM_BLOCKER("modCheckIMBlocker"),
    REPLAY_MOD("modCheckReplayMod"),
    BORDERLESS_WINDOW("modCheckBorderlessWindow")
}