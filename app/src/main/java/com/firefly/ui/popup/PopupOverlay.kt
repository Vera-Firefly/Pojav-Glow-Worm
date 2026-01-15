package com.firefly.ui.popup

import android.content.Context
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.graphics.Color.argb
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity.CENTER
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_BACK
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView

class PopupOverlay private constructor(
    private val context: Context,
    private val rootContainer: FrameLayout
) {

    private var currentPopup: PopupContainer? = null

    companion object {
        fun create(context: Context, rootContainer: FrameLayout): PopupOverlay {
            return PopupOverlay(context, rootContainer)
        }
    }

    fun showPopup(contentView: View? = null, cancelable: Boolean = true) {
        dismiss()

        currentPopup = PopupContainer(context, contentView ?: createDefaultView(), cancelable)

        rootContainer.addView(currentPopup)

        currentPopup?.show()
    }

    fun dismiss() {
        currentPopup?.dismiss()
        currentPopup = null
    }

    fun isShowing(): Boolean = currentPopup != null

    private fun createDefaultView(): View {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

            background = createRoundedDrawable(WHITE, 8.dp())
            elevation = 8f

            val textView = TextView(context).apply {
                text = "Hello World!"
                textSize = 18f
                setTextColor(BLACK)
                gravity = CENTER
                setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
            }

            addView(textView)
        }
    }

    private fun createRoundedDrawable(color: Int, cornerRadius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            this.cornerRadius = cornerRadius.toFloat()
        }
    }

    private fun Int.dp(): Int = (this * context.resources.displayMetrics.density).toInt()

    private inner class PopupContainer(
        context: Context,
        private val content: View,
        private val cancelable: Boolean
    ) : FrameLayout(context) {

        private var isDismissing = false
        private var isShowing = false
        private val handler = Handler(Looper.getMainLooper())

        private lateinit var backgroundLayer: View
        private lateinit var contentContainer: FrameLayout

        init {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

            isClickable = true
            isFocusable = true

            initLayers()
        }

        private fun initLayers() {
            backgroundLayer = View(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                setBackgroundColor(argb(180, 0, 0, 0))

                alpha = 0f

                if (cancelable) {
                    setOnClickListener {
                        dismiss()
                    }
                } else {
                    setOnClickListener {
                        // 什么也不做, 但必须要有
                    }
                }
            }

            addView(backgroundLayer)

            contentContainer = FrameLayout(context).apply {
                layoutParams = LayoutParams(300.dp(), WRAP_CONTENT).apply {
                    gravity = CENTER
                }

                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false

                alpha = 0f
                scaleX = 0.8f
                scaleY = 0.8f
            }

            content.isClickable = true
            content.isFocusable = true

            content.layoutParams = LayoutParams(300.dp(), WRAP_CONTENT).apply {
                gravity = CENTER
            }

            contentContainer.addView(content)
            addView(contentContainer)
        }

        fun show() {
            if (isShowing) return
            isShowing = true

            backgroundLayer.animate()
                .alpha(1f)
                .setDuration(300)
                .start()

            contentContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start()
        }

        fun dismiss() {
            if (isDismissing || !isShowing) return
            isDismissing = true

            contentContainer.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .start()

            backgroundLayer.animate()
                .alpha(0f)
                .setDuration(250)
                .setStartDelay(50)
                .withEndAction {
                    removeFromParent()
                }
                .start()
        }

        private fun removeFromParent() {
            handler.post {
                (parent as? ViewGroup)?.removeView(this)
                if (currentPopup == this) {
                    currentPopup = null
                }
            }
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (cancelable && event.action == MotionEvent.ACTION_DOWN) {
                val rect = Rect()
                contentContainer.getGlobalVisibleRect(rect)

                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                if (!rect.contains(x, y)) {
                    val bgRect = Rect()
                    backgroundLayer.getGlobalVisibleRect(bgRect)

                    if (bgRect.contains(x, y)) {

                    }
                }
            }
            return false
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (cancelable && event.keyCode == KEYCODE_BACK
                && event.action == ACTION_UP
            ) {
                dismiss()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_DOWN && cancelable) {
                val rect = Rect()
                contentContainer.getGlobalVisibleRect(rect)

                if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    // ?
                }
            }

            return super.dispatchTouchEvent(ev)
        }
    }
}