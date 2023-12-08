# PojavLauncher-Beta-Zink
A Minecraft: Java Edition Launcher for Android based on Boardwalk.
点击切换<a href="/README-ZH_CN.md">中文</a>
## Introduction:
* This repository was forked on [PojavLauncherTeam:PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)

* This app will be aligned with the Pojav team's main branch update →[v3_openjdk](https://github.com/PojavLauncherTeam/PojavLauncher/tree/v3_openjdk)

* Add vgpu render,vgpu1.4.0 OpenGL4.4

* Virgl render is already working

* This modified version of lwjgl uses the latest content from [Vera-Firefly](https://github.com/Vera-Firefly) [lwjgl3-build](https://github.com/Vera-Firefly/lwjgl3-build) repository for automated builds

* In addition, this modified version of the Java runtime also uses the latest content from Vera-Firefly [android-openjdk-build](https://github.com/Vera-Firefly/android-openjdk-build) repository for automated builds

* This is not the official version, it's just a modified,If you don't want to use it, please go to the [PojavLauncherTeam:PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)

* Provide own Curseforge API key

## Notice:
* If you are using a device with a Snapdragon processor and you want to use the zink renderer to render light and shadow, be careful to identify the Mesa version

* Devices with Snapdragon processors currently only support the old Mesa version of the zink renderer, and playing with the new Mesa version of the zink renderer will cause the game launcher to flash back.

* The new Mesa version of zink renderer is not very stable at present, and the compactness is very poor. If you use it to enter the game, please restart the device for many times. If it can't be solved, please go back to the old Mesa version of zink renderer.

* Given that the launcher update will produce some magical features that prevent some devices from entering or starting the game, the Beta version can be installed back and forth between all versions, so you don't have to worry about your game data unless you uninstall it by hand.

## More:
* If you want a different experience, try experimental version:[Pojav EXP](https://github.com/Vera-Firefly/PojavLauncher-Experimental-Edition)

## [Get it](https://github.com/Vera-Firefly/PojavLauncher-Beta-Zink/releases) on release
