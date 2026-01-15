package com.firefly.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

object ToastUtils {
    private var previousToast: Toast? = null

    fun Context.ShowToast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        previousToast?.cancel()
        previousToast = Toast.makeText(this, message, duration).apply {
            show()
        }
    }

    fun Context.ShowToast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        this.ShowToast(this.getText(resId), duration)
    }

    @JvmStatic
    @JvmOverloads
    fun Toast(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        context.ShowToast(message, duration)
    }

    @JvmStatic
    @JvmOverloads
    fun Toast(context: Context, @StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        context.ShowToast(resId, duration)
    }
}