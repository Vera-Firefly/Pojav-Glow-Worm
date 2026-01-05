package net.kdt.pojavlaunch.firefly.customcontrols.gamepad;

import net.kdt.pojavlaunch.firefly.GrabListener;

public interface GamepadDataProvider {
    GamepadMap getMenuMap();
    GamepadMap getGameMap();
    boolean isGrabbing();
    void attachGrabListener(GrabListener grabListener);
}
