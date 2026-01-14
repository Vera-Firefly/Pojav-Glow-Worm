package com.movtery.feature.mod.parser

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.firefly.ui.dialog.CustomDialog
import com.mio.util.AndroidUtil
import com.movtery.task.TaskExecutors
import net.kdt.pojavlaunch.firefly.Logger
import net.kdt.pojavlaunch.firefly.Architecture
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.plugins.FFmpegPlugin

class ModChecker {
    class ModCheckResult() : Parcelable {
        var hasTouchController: Boolean = false
        var hasSodiumOrEmbeddium: Boolean = false
        var hasPhysics: Boolean = false
        var hasMCEF: Boolean = false
        var hasValkyrienSkies: Boolean = false
        var hasYesSteveModel: Boolean = false
        var hasIMBlockerOrInGameIME: Boolean = false
        var hasReplayMod: Boolean = false
        var hasBorderlesswindow: Boolean = false

        private fun Boolean.getInt(): Int = if (this) 1 else 0
        private fun Int.toBoolean(): Boolean = this != 0

        constructor(parcel: Parcel) : this() {
            hasTouchController = parcel.readInt().toBoolean()
            hasSodiumOrEmbeddium = parcel.readInt().toBoolean()
            hasPhysics = parcel.readInt().toBoolean()
            hasMCEF = parcel.readInt().toBoolean()
            hasValkyrienSkies = parcel.readInt().toBoolean()
            hasYesSteveModel = parcel.readInt().toBoolean()
            hasIMBlockerOrInGameIME = parcel.readInt().toBoolean()
            hasReplayMod = parcel.readInt().toBoolean()
            hasBorderlesswindow = parcel.readInt().toBoolean()
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(hasTouchController.getInt())
            dest.writeInt(hasSodiumOrEmbeddium.getInt())
            dest.writeInt(hasPhysics.getInt())
            dest.writeInt(hasMCEF.getInt())
            dest.writeInt(hasValkyrienSkies.getInt())
            dest.writeInt(hasYesSteveModel.getInt())
            dest.writeInt(hasIMBlockerOrInGameIME.getInt())
            dest.writeInt(hasReplayMod.getInt())
            dest.writeInt(hasBorderlesswindow.getInt())
        }

        companion object CREATOR : Parcelable.Creator<ModCheckResult> {
            override fun createFromParcel(parcel: Parcel): ModCheckResult {
                return ModCheckResult(parcel)
            }

            override fun newArray(size: Int): Array<ModCheckResult?> {
                return arrayOfNulls(size)
            }
        }
    }

    /**
     * 检查所有模组，并对一些已知的模组进行判断
     */
    fun check(context: Context, modInfoList: List<ModInfo>, executeTask: (ModCheckResult?) -> Unit) {
        runCatching {
            val modCheckSettings = mutableMapOf<AllModCheckSettings, Pair<String, String>>()

            if (modInfoList.isNotEmpty()) {
                Logger.appendToLog("Mod Perception: ${modInfoList.size} Mods parsed successfully")
            }

            val modResult = ModCheckResult()

            modInfoList.forEach { mod ->
                when (mod.id) {
                    "touchcontroller" -> {
                        if (!modResult.hasTouchController) {
                            modResult.hasTouchController = true
                            modCheckSettings[AllModCheckSettings.TOUCH_CONTROLLER] = Pair(
                                "1",
                                context.getString(R.string.mod_check_touch_controller, mod.file.name)
                            )
                        }
                    }
                    "sodium", "embeddium" -> {
                        if (!modResult.hasSodiumOrEmbeddium) {
                            modResult.hasSodiumOrEmbeddium = true
                            modCheckSettings[AllModCheckSettings.SODIUM_OR_EMBEDDIUM] = Pair(
                                "2",
                                context.getString(R.string.mod_check_sodium_or_embeddium, mod.file.name)
                            )
                        }
                    }
                    "physicsmod" -> {
                        if (!modResult.hasPhysics) {
                            modResult.hasPhysics = true
                            val arch = AndroidUtil.getElfArchFromZip(
                                mod.file,
                                "de/fabmax/physxjni/linux/libPhysXJniBindings_64.so"
                            )
                            if (arch.isBlank() or (!Architecture.isx86Device() and arch.contains("x86"))) {
                                modCheckSettings[AllModCheckSettings.PHYSICS_MOD] = Pair(
                                    "1",
                                    context.getString(R.string.mod_check_physics, mod.file.name)
                                )
                            }
                        }
                    }
                    "mcef" -> {
                        if (!modResult.hasMCEF) {
                            modResult.hasMCEF = true
                            modCheckSettings[AllModCheckSettings.MCEF] = Pair(
                                "1",
                                context.getString(R.string.mod_check_mcef, mod.file.name)
                            )
                        }
                    }
                    "valkyrienskies" -> {
                        if (!modResult.hasValkyrienSkies) {
                            modResult.hasValkyrienSkies = true
                            modCheckSettings[AllModCheckSettings.VALKYRIEN_SKIES] = Pair(
                                "1",
                                context.getString(R.string.mod_check_valkyrien_skies, mod.file.name)
                            )
                        }
                    }
                    "yes_steve_model" -> {
                        if (!modResult.hasYesSteveModel) {
                            modResult.hasYesSteveModel = true
                            val defaultArch = AndroidUtil.getElfArchFromZip(
                                mod.file,
                                "META-INF/native/libysm-core.so"
                            )
                            val androidArch = AndroidUtil.getElfArchFromZip(
                                mod.file,
                                "META-INF/native/libysm-core-android.so"
                            )
                            if (defaultArch.isNotBlank() && androidArch.isBlank()) {
                                modCheckSettings[AllModCheckSettings.YES_STEVE_MODEL] = Pair(
                                    "1",
                                    context.getString(R.string.mod_check_yes_steve_model, mod.file.name)
                                )
                            }
                        }
                    }
                    "imblocker", "ingameime" -> {
                        if (!modResult.hasIMBlockerOrInGameIME) {
                            modResult.hasIMBlockerOrInGameIME = true
                            modCheckSettings[AllModCheckSettings.IM_BLOCKER] = Pair(
                                "2",
                                context.getString(R.string.mod_check_imblocker, mod.file.name)
                            )
                        }
                    }
                    "replaymod" -> {
                        if (!modResult.hasReplayMod) {
                            modResult.hasReplayMod = true
                            FFmpegPlugin.discover(context)
                            if (!FFmpegPlugin.isAvailable) {
                                modCheckSettings[AllModCheckSettings.REPLAY_MOD] = Pair(
                                    "1",
                                    context.getString(R.string.mod_check_replay_mod, mod.file.name,
                                        "https://github.com/FCL-Team/FoldCraftLauncher/releases/download/ffmpeg/Pojav.FFmpeg.Plugin.1.1.APK",
                                        "https://pan.quark.cn/s/6201574edb62"
                                    )
                                )
                            }
                        }
                    }
                    "borderlesswindow" -> {
                        if (!modResult.hasBorderlesswindow) {
                            modResult.hasBorderlesswindow = true
                            modCheckSettings[AllModCheckSettings.BORDERLESS_WINDOW] = Pair(
                                "1",
                                context.getString(R.string.mod_check_borderlesswindow, mod.file.name)
                            )
                        }
                    }
                }
            }

            showResultDialog(context, modCheckSettings) {
                executeTask(modResult)
            }
        }.onFailure { e ->
            Log.e("LaunchGame", "An error occurred while trying to process existing mod information", e)
            executeTask(null)
        }
    }

    private fun showResultDialog(
        context: Context,
        modCheckSettings: MutableMap<AllModCheckSettings, Pair<String, String>>,
        executeTask: () -> Unit
    ) {
        val messages = modCheckSettings
            .mapNotNull { (setting, valuePair) ->
                if (setting.defaultValue != valuePair.first) valuePair.second else null
            }.withIndex()
            .joinToString("\r\n\r\n") {
                "${it.index + 1}. ${it.value}"
            }

        if (messages.isEmpty()) {
            executeTask()
            return
        }

        TaskExecutors.runInUIThread {
            CustomDialog.Builder(context)
                .setTitle(context.getString(R.string.mod_check_dialog_title))
                .setMessage(messages)
                .setCancelable(false)
                .setConfirmListener(R.string.alertdialog_done) { view ->
                    executeTask()
                    true
                }
                .setCancelListener(R.string.generic_no_more_reminders) { v ->
                    modCheckSettings.forEach { (setting, valuePair) ->
                        setting.defaultValue = valuePair.first
                    }
                    true
                }
                .build()
                .show()
        }
    }
}