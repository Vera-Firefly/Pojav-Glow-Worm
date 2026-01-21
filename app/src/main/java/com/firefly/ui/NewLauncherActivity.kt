package com.firefly.ui

import android.os.Bundle
import net.kdt.pojavlaunch.firefly.BaseActivity
import net.kdt.pojavlaunch.firefly.R

class NewLauncherActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
    }
}