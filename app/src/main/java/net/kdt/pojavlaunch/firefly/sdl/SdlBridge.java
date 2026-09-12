/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.firefly.sdl;

import android.os.Looper;

import net.kdt.pojavlaunch.firefly.MainActivity;
import net.kdt.pojavlaunch.firefly.Tools;

/**
 * Owns the SDL integration state shared by the launcher and game JVM.
 * Ported from ZalithLauncher2's SdlBridge, adapted to PGW.
 */
public final class SdlBridge {
    private static volatile boolean sdlEnabled = false;
    private static volatile boolean sdlImeAutoShowEnabled = true;
    private static boolean jniReady = false;
    private static boolean sdlInitialized = false;

    private SdlBridge() {
    }

    public static synchronized boolean setupJNI() {
        if (jniReady) {
            return true;
        }
        org.libsdl.app.SDL.setupJNI();
        jniReady = true;
        return true;
    }

    public static synchronized boolean markSdlInitialized() {
        if (sdlInitialized) return false;
        sdlInitialized = true;
        return true;
    }

    public static synchronized void clearSdlInitialized() {
        sdlInitialized = false;
    }

    public static boolean getSdlEnabled() {
        return sdlEnabled;
    }

    public static void setSdlEnabled(boolean enabled) {
        sdlEnabled = enabled;
    }

    /** Whether the launcher responds when SDL requests the input method. */
    public static boolean getSdlImeAutoShowEnabled() {
        return sdlImeAutoShowEnabled;
    }

    public static void setSdlImeAutoShowEnabled(boolean enabled) {
        sdlImeAutoShowEnabled = enabled;
    }

    /** Notifies the host UI to (re)take focus. */
    public static void requestComposeFocus() {
    }

    /** Collapses the launcher's own input bar. */
    public static void disableActiveLauncherInput() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            disableActiveLauncherInputOnMainThread();
            return;
        }
        Tools.runOnUiThread(SdlBridge::disableActiveLauncherInputOnMainThread);
    }

    private static void disableActiveLauncherInputOnMainThread() {
        if (MainActivity.touchCharInput != null) {
            MainActivity.touchCharInput.disable();
        }
    }

    /**
     * Registers the game surface with the SDL Java layer (external mode).
     * Must be called on the main thread.
     */
    public static void prepareSurface(android.app.Activity activity, android.view.Surface surface,
                                      android.view.ViewGroup layout) {
        if (org.libsdl.app.SDLActivity.getSDLSurface() == null) {
            org.libsdl.app.SDL.initialize();
            org.libsdl.app.SDL.setContext(activity);
            org.libsdl.app.SDLActivity.externalInitialize(
                    new org.libsdl.app.SDLSurface(activity), layout, surface);
        } else {
            org.libsdl.app.SDLSurface.setNativeSurface(surface);
        }
    }

    /** Re-binds a recreated surface to the running SDL backend. */
    public static void registerSurface(android.app.Activity activity, android.view.Surface surface,
                                       android.view.ViewGroup layout) {
        org.libsdl.app.SDLSurface.setNativeSurface(surface);
    }

    public static void unregisterSurface(android.view.Surface surface) {
        org.libsdl.app.SDLSurface.clearNativeSurface();
    }
}
