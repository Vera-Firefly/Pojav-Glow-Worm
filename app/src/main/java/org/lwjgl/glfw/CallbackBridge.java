package org.lwjgl.glfw;

import android.content.ClipData;
import android.content.ClipDescription;
import android.view.Choreographer;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.firefly.GrabListener;
import net.kdt.pojavlaunch.firefly.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.firefly.MainActivity;

import java.util.ArrayList;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static boolean isGrabbing = false;
    private static final ArrayList<GrabListener> grabListeners = new ArrayList<>();

    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;

    // SDL launcher integration notification types
    public static final int NOTIF_TYPE_SDL = 0;
    public static final int ACTION_INIT_LAUNCHER_INTEGRATION = 0;
    public static final int ACTION_SEND_TEXTBOX_RECT = 1;

    /**
     * SDL launcher integration notification entry, called from JRE side.
     * @return whether the notification was handled
     */
    @SuppressWarnings("unused")
    public static boolean notifyLauncher(int type, int... action) {
        if (action == null || action.length == 0) {
            net.kdt.pojavlaunch.firefly.Logger.appendToLog("SDLBridge: notification has no action");
            return false;
        }
        switch (type) {
            case NOTIF_TYPE_SDL:
                if (action[0] == ACTION_INIT_LAUNCHER_INTEGRATION) {
                    if (!net.kdt.pojavlaunch.firefly.sdl.SdlBridge.markSdlInitialized()) {
                        return true;
                    }
                    try {
                        net.kdt.pojavlaunch.firefly.Logger.appendToLog("SDLBridge: loading real SDL3");
                        System.loadLibrary("SDL3");
                        net.kdt.pojavlaunch.firefly.Logger.appendToLog("SDLBridge: setting up SDL JNI");
                        net.kdt.pojavlaunch.firefly.sdl.SdlBridge.setupJNI();
                        net.kdt.pojavlaunch.firefly.Logger.appendToLog("SDLBridge: binding SDL surface");
                        net.kdt.pojavlaunch.firefly.sdl.SdlBridge.setSdlEnabled(true);
                        org.libsdl.app.SDLSurface surface = org.libsdl.app.SDLActivity.getSDLSurface();
                        if (surface != null) {
                            surface.surfaceChanged();
                            if (windowWidth > 0 && windowHeight > 0) {
                                surface.nativeResize(windowWidth, windowHeight);
                            }
                        }
                        net.kdt.pojavlaunch.firefly.Logger.appendToLog("SDLBridge: SDL support enabled!");
                        return true;
                    } catch (Throwable e) {
                        net.kdt.pojavlaunch.firefly.sdl.SdlBridge.setSdlEnabled(false);
                        net.kdt.pojavlaunch.firefly.sdl.SdlBridge.clearSdlInitialized();
                        java.io.StringWriter trace = new java.io.StringWriter();
                        e.printStackTrace(new java.io.PrintWriter(trace));
                        net.kdt.pojavlaunch.firefly.Logger.appendToLog("SDLBridge: SDL launcher integration unavailable:\n" + trace);
                    }
                }
                if (action[0] == ACTION_SEND_TEXTBOX_RECT) {
                    // TODO: 输入框位置同步（后续接入）
                }
        }
        return false;
    }

    /**
     * LWJGL SDL binding entry point, forwards to {@link #notifyLauncher}.
     */
    @SuppressWarnings("unused")
    public static void nativeNotifyLauncher(int type, int... action) {
        notifyLauncher(type, action);
    }

    public static volatile int windowWidth, windowHeight;
    // Android mouse button bitmask reported with onNativeMouse
    private static int sMouseButtonState = 0;
    private static int sdlMoveLogCounter = 0;
    /** SDL relative mouse mode flag */
    public static volatile boolean sdlRelativeMode = false;
    private static float sdlLastSentX, sdlLastSentY;
    private static boolean sdlLastSentValid = false;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY;
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        sChoreographer.postFrameCallbackDelayed(l -> putMouseEventWithCoords(button, false, x, y), 33);
    }

    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    public static void sendCursorPos(float x, float y) {
        mouseX = x;
        mouseY = y;
        nativeSendCursorPos(mouseX, mouseY);
        // SDL mode: relative deltas while grabbing, absolute coordinates otherwise
        if (net.kdt.pojavlaunch.firefly.sdl.SdlBridge.getSdlEnabled()) {
            if (sdlRelativeMode) {
                float dx = x - (sdlLastSentValid ? sdlLastSentX : x);
                float dy = y - (sdlLastSentValid ? sdlLastSentY : y);
                sdlLastSentX = x;
                sdlLastSentY = y;
                sdlLastSentValid = true;
                org.libsdl.app.SDLActivity.onNativeMouse(0, android.view.MotionEvent.ACTION_MOVE, dx, dy, true);
            } else {
                sdlLastSentValid = false;
                if ((++sdlMoveLogCounter % 120) == 1) {
                    net.kdt.pojavlaunch.firefly.Logger.appendToLog(
                        "SDLBridge: onNativeMouse MOVE x=" + x + " y=" + y + " (window=" + windowWidth + "x" + windowHeight + ")");
                }
                org.libsdl.app.SDLActivity.onNativeMouse(0, android.view.MotionEvent.ACTION_MOVE, x, y, false);
            }
        }
    }

    /** Relative (grabbed) mouse movement */
    public static void sendCursorDelta(float x, float y) {
        mouseX += x;
        mouseY += y;
        nativeSendCursorPos(mouseX, mouseY);
        if (net.kdt.pojavlaunch.firefly.sdl.SdlBridge.getSdlEnabled()) {
            org.libsdl.app.SDLActivity.onNativeMouse(0, android.view.MotionEvent.ACTION_MOVE, x, y, true);
        }
    }

    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        // TODO CHECK: This may cause input issue, not receive input!
        if (keycode != 0) nativeSendKey(keycode, scancode, isDown ? 1 : 0, modifiers);
        if (isDown && keychar != '\u0000') {
            nativeSendCharMods(keychar, modifiers);
            nativeSendChar(keychar);
        }
        // SDL mode: also forward through the SDL Java layer
        if (net.kdt.pojavlaunch.firefly.sdl.SdlBridge.getSdlEnabled()) {
            try {
                int androidKeycode = net.kdt.pojavlaunch.firefly.input.EfficientAndroidLWJGLKeycode.getSdlAndroidKeycode(keycode);
                if (androidKeycode == android.view.KeyEvent.KEYCODE_UNKNOWN) return;
                if (isDown) {
                    org.libsdl.app.SDLActivity.onNativeKeyDown(androidKeycode);
                    if (!Character.isISOControl(keychar) && org.libsdl.app.SDLActivity.isSDLTextInputActive()) {
                        org.libsdl.app.SDLActivity.onNativeTextInput(String.valueOf(keychar));
                    }
                } else {
                    org.libsdl.app.SDLActivity.onNativeKeyUp(androidKeycode);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void sendChar(char keychar, int modifiers) {
        nativeSendCharMods(keychar, modifiers);
        nativeSendChar(keychar);
        // SDL mode: also forward into the SDL text input channel
        if (net.kdt.pojavlaunch.firefly.sdl.SdlBridge.getSdlEnabled()
                && !Character.isISOControl(keychar)
                && org.libsdl.app.SDLActivity.isSDLTextInputActive()) {
            org.libsdl.app.SDLActivity.onNativeTextInput(String.valueOf(keychar));
        }
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        // if (isGrabbing()) DEBUG_STRING.append("MouseGrabStrace: " + android.util.Log.getStackTraceString(new Throwable()) + "\n");
        nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
        // SDL mode: also forward button state through onNativeMouse
        if (net.kdt.pojavlaunch.firefly.sdl.SdlBridge.getSdlEnabled()) {
            int aKey;
            switch (button) {
                case 0: aKey = android.view.MotionEvent.BUTTON_PRIMARY; break;
                case 1: aKey = android.view.MotionEvent.BUTTON_SECONDARY; break;
                case 2: aKey = android.view.MotionEvent.BUTTON_TERTIARY; break;
                default: aKey = 1 << (button - 1); break;
            }
            if (isDown) {
                sMouseButtonState |= aKey;
            } else {
                sMouseButtonState &= ~aKey;
            }
            org.libsdl.app.SDLActivity.onNativeMouse(sMouseButtonState,
                    isDown ? android.view.MotionEvent.ACTION_DOWN : android.view.MotionEvent.ACTION_UP,
                    mouseX, mouseY, false);
        }
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendScroll(double xoffset, double yoffset) {
        nativeSendScroll(xoffset, yoffset);
        // SDL mode: also forward scroll through onNativeMouse
        if (net.kdt.pojavlaunch.firefly.sdl.SdlBridge.getSdlEnabled()) {
            org.libsdl.app.SDLActivity.onNativeMouse(0, android.view.MotionEvent.ACTION_SCROLL,
                    (float) xoffset, (float) yoffset, false);
        }
    }

    public static void sendUpdateWindowSize(int w, int h) {
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        switch (type) {
            case CLIPBOARD_COPY:
                MainActivity.GLOBAL_CLIPBOARD.setPrimaryClip(ClipData.newPlainText("Copy", copy));
                return null;

            case CLIPBOARD_PASTE:
                if (MainActivity.GLOBAL_CLIPBOARD.hasPrimaryClip() && MainActivity.GLOBAL_CLIPBOARD.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    return MainActivity.GLOBAL_CLIPBOARD.getPrimaryClip().getItemAt(0).getText().toString();
                } else {
                    return "";
                }

            case CLIPBOARD_OPEN:
                MainActivity.openLink(copy);
                return null;
            default:
                return null;
        }
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        }
        if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        }
        if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        }
        if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        }
        if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown) {
        switch (keyCode) {
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT:
                CallbackBridge.holdingAlt = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
        }
    }

    /** Forwards an SDL pointer lock request to the launcher grab mechanism */
    public static void notifyGrabStateFromSdl(final boolean grabbing) {
        sdlRelativeMode = grabbing;
        sdlLastSentValid = false;
        onGrabStateChanged(grabbing);
    }

    public static void clearSdlBridgeState() {
        sMouseButtonState = 0;
        sdlRelativeMode = false;
        sdlLastSentValid = false;
        sdlLastSentX = 0;
        sdlLastSentY = 0;
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    private static void onGrabStateChanged(final boolean grabbing) {
        isGrabbing = grabbing;
        sdlLastSentValid = false;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if (isGrabbing != grabbing) return;

            System.out.println("Grab changed : " + grabbing);
            synchronized (grabListeners) {
                for (GrabListener g : grabListeners) g.onGrabState(grabbing);
            }

        }, 16);

    }

    public static void addGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            listener.onGrabState(isGrabbing);
            grabListeners.add(listener);
        }
    }

    public static void removeGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            grabListeners.remove(listener);
        }
    }

    @CriticalNative
    public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @CriticalNative
    private static native boolean nativeSendChar(char codepoint);

    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @CriticalNative
    private static native boolean nativeSendCharMods(char codepoint, int mods);

    @CriticalNative
    private static native void nativeSendKey(int key, int scancode, int action, int mods);

    // private static native void nativeSendCursorEnter(int entered);
    @CriticalNative
    private static native void nativeSendCursorPos(float x, float y);

    @CriticalNative
    private static native void nativeSendMouseButton(int button, int action, int mods);

    @CriticalNative
    private static native void nativeSendScroll(double xoffset, double yoffset);

    @CriticalNative
    private static native void nativeSendScreenSize(int width, int height);

    public static native void nativeSetWindowAttrib(int attrib, int value);

    public static native int initFps();

    static {
        System.loadLibrary("pojavexec");
    }
}
