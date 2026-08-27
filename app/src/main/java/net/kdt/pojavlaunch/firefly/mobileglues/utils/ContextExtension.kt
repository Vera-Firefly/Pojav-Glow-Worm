package net.kdt.pojavlaunch.firefly.mobileglues.utils

import android.content.Context
import android.widget.Toast

fun Context.toast(text: Any, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text.toString(), duration).show()


