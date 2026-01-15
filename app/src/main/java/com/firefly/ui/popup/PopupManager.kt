package com.firefly.ui.popup

import android.view.View

object PopupManager {

    private var internalPopup: PopupOverlay? = null

    fun initialize(popupOverlay: PopupOverlay) {
        this.internalPopup = popupOverlay
    }

    fun show(contentView: View? = null, cancelable: Boolean = true): Boolean {
        return internalPopup?.run {
            showPopup(contentView, cancelable)
            true
        } ?: false.also {
            println("PopupManager not initialized!")
        }
    }

    fun dismiss() {
        internalPopup?.dismiss()
    }

    fun isShowing(): Boolean {
        return internalPopup?.isShowing() ?: false
    }

    fun cleanup() {
        dismiss()
        internalPopup = null
    }
}