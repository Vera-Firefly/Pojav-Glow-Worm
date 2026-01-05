package net.kdt.pojavlaunch.firefly.customcontrols.mouse;

import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;

import android.os.Handler;

import net.kdt.pojavlaunch.firefly.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences;

public class DropGesture implements Runnable {
    private final Handler mHandler;
    private boolean mActive;

    public DropGesture(Handler mHandler) {
        this.mHandler = mHandler;
    }

    public void submit() {
        if (!mActive) {
            mActive = true;
            mHandler.postDelayed(this, LauncherPreferences.PREF_LONGPRESS_TRIGGER);
        }
    }

    public void cancel() {
        mActive = false;
        mHandler.removeCallbacks(this);
    }

    @Override
    public void run() {
        if (!mActive) return;
        sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_Q);
        mHandler.postDelayed(this, 250);
    }
}
