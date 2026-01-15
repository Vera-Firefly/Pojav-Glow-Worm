package com.movtery.feature.mod.parser

import android.content.Context

class ModCheckResult {
    @JvmField var hasTouchController: Boolean = false
    @JvmField var hasSodiumOrEmbeddium: Boolean = false
    @JvmField var hasPhysics: Boolean = false
    @JvmField var hasMCEF: Boolean = false
    @JvmField var hasValkyrienSkies: Boolean = false
    @JvmField var hasYesSteveModel: Boolean = false
    @JvmField var hasIMBlockerOrInGameIME: Boolean = false
    @JvmField var hasReplayMod: Boolean = false
    @JvmField var hasBorderlesswindow: Boolean = false

    companion object {
        private const val KEY_RESULT = "mod_check_result"

        @JvmStatic
        fun loadFrom(context: Context): ModCheckResult {
            return ModCheckResult().apply { load(context) }
        }

        @JvmStatic
        fun clear(context: Context) {
            ModCheckResult().apply { reset(context) }
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(KEY_RESULT, Context.MODE_PRIVATE).edit().apply {
            putBoolean("hasTouchController", hasTouchController)
            putBoolean("hasSodiumOrEmbeddium", hasSodiumOrEmbeddium)
            putBoolean("hasPhysics", hasPhysics)
            putBoolean("hasMCEF", hasMCEF)
            putBoolean("hasValkyrienSkies", hasValkyrienSkies)
            putBoolean("hasYesSteveModel", hasYesSteveModel)
            putBoolean("hasIMBlockerOrInGameIME", hasIMBlockerOrInGameIME)
            putBoolean("hasReplayMod", hasReplayMod)
            putBoolean("hasBorderlesswindow", hasBorderlesswindow)
            apply()
        }
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(KEY_RESULT, Context.MODE_PRIVATE)
        hasTouchController = p.getBoolean("hasTouchController", false)
        hasSodiumOrEmbeddium = p.getBoolean("hasSodiumOrEmbeddium", false)
        hasPhysics = p.getBoolean("hasPhysics", false)
        hasMCEF = p.getBoolean("hasMCEF", false)
        hasValkyrienSkies = p.getBoolean("hasValkyrienSkies", false)
        hasYesSteveModel = p.getBoolean("hasYesSteveModel", false)
        hasIMBlockerOrInGameIME = p.getBoolean("hasIMBlockerOrInGameIME", false)
        hasReplayMod = p.getBoolean("hasReplayMod", false)
        hasBorderlesswindow = p.getBoolean("hasBorderlesswindow", false)
    }

    fun reset(context: Context) {
        hasTouchController = false
        hasSodiumOrEmbeddium = false
        hasPhysics = false
        hasMCEF = false
        hasValkyrienSkies = false
        hasYesSteveModel = false
        hasIMBlockerOrInGameIME = false
        hasReplayMod = false
        hasBorderlesswindow = false
        save(context)
    }
}