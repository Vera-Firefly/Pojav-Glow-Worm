<H1 align="center">Pojav Beta·Zink</H1>

<img src="https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/assets/pojavlauncher.png" align="left" width="130" height="150" alt="PojavLauncher logo">

[![Android CI](https://github.com/PojavLauncherTeam/PojavLauncher/workflows/Android%20CI/badge.svg)](https://github.com/Vera-Firefly/PojavLauncher-Beta-Zink/actions)
---------
* <a href="/README.md">English</a>

* 基于 [Boardwalk](https://github.com/zhuowei/Boardwalk) 而制作的PojavLauncher!

* PojavLauncher是一个允许你在Android设备上玩《Minecraft:Java Edition》的启动器!

* 它几乎可以运行Mincraft的每个版本,允许您使用.jar仅安装程序来安装modloader,比如[Forge](https://files.minecraftforge.net/)和[Fabric](http://fabricmc.net/),模组比如[OptiFine](https://optifine.net)和[LabyMod](https://www.labymod.net/en),以及黑客客户端,比如[Wurst](https://www.wurstclient.net/),还有更多!
## 开始时的一些注意事项
- 我仅在[BiliBili](https://space.bilibili.com/1412062866?spm_id_from=333.1007.0.0)平台发布视频,其它地方出现均为搬运

- 该存储库fork自[PojavLauncherTeam:PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)

- 这个启动器将与Pojav团队的主分支更新保持一致 →[v3_openjdk](https://github.com/PojavLauncherTeam/PojavLauncher/tree/v3_openjdk)

- 这个启动器增加了更多渲染器和实验性设置

- 这个启动器并非原版,它是魔改版,如果你不想使用它,请到[PojavLauncherTeam:PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)

- 使用了自己的Curseforge API key

## 导航
- [介绍](#介绍)  
- [获取 Pojav Beta·Zink](#获取Pojav-Beta-Zink)
- [构建](#构建) 
- [当前状态](#当前状态) 
- [许可证](#许可证) 
- [贡献](#贡献) 
- [信用和第三方组件及其许可证](#信用和第三方组件及其许可证)
- [更多](#更多)

## 介绍 
* PojavLauncher是一款Android可用的基于[Boardwalk](https://github.com/zhuowei/Boardwalk)制作的Minecraft: Java Edition启动器
* 这个启动器可以运行几乎所有可用的Minecraft版本，从rd-132211到最新的快照快照版本(包括战斗测试版本)
* 还支持通过Forge和Fabric进行修改
* 此存储库包含Android的源代码
* Pojav Beta·Zink不支持IOS!!!

## 获取Pojav Beta Zink

你可以通过以下三种方法获取:

1. 你可以从[正式包](https://github.com/Vera-Firefly/PojavLauncher-Beta-Zink/releases)或[自动构建](https://github.com/Vera-Firefly/PojavLauncher-Beta-Zink/actions)获取

2. 你可以从[网盘](https://www.123pan.com/s/O0EDVv-ZJT13)获取

3. 你可以从源代码[构建](#构建)
## 构建
如果要从源代码构建,请执行以下步骤.
### Java运行时环境(JRE)
- 适用于Android的JRE[这里](https://github.com/Vera-Firefly/android-openjdk-build)
- 遵循构建脚本上的构建说明[README.md](https://github.com/Vera-Firefly/android-openjdk-build/blob/buildjre8/README.md).
- 如果你懒惰或者出于某种原因,你还可以从[自动构建](https://github.com/Vera-Firefly/android-openjdk-build/actions)获取它
* 要么获取 `jre8-pojav` 的移动构建文件, 或自己拆分所有工作流:</br>
   - 获取4种受支持的处理器架构(arm, arm64, x86, x86_64) </br> 
      - 将JRE拆分为以下几个部分:</br>
                Platform-independent: .jar 文件,库,配置文件等...</br>
                Platform-dependent: .so 文件等...</br>
        - 创建:</br>
                一个名为 `universal.tar.xz` 的Platform-independent文件</br>
                4个名为 `bin-<arch>.tar.xz` 的与platform-dependent有关的处理器架构文件</br>
        - 把这些放在 `assets/components/jre/` 文件夹</br>
        - (如果需要)使用当前日期更新版本文件</br>

### LWJGL
* 自定义LWJGL的构建说明可参照[LWJGL repository](https://github.com/PojavLauncherTeam/lwjgl3)

* 此修改版本的lwjgl使用的最新内容来自[Vera-Firefly](https://github.com/Vera-Firefly) [lwjgl3-build](https://github.com/Vera-Firefly/lwjgl3-build)用于自动构建的存储库
### 启动器
- 由于语言是由Crowdin自动添加的,因此在构建之前需要运行语言列表生成器.在工程目录下,执行： 

* 在Linux, Mac OS上:
```
chmod +x scripts/languagelist_updater.sh
bash scripts/languagelist_updater.sh
```
* 在Windows上:
```
scripts\languagelist_updater.bat
```
然后,运行这些命令,~~或使用Android Studio构建~~

* 构建 GLFW stub:
```
./gradlew :jre_lwjgl3glfw:build
```       
* 构建启动器
```
./gradlew :app_pojavlauncher:assembleDebug
```
(替换 `gradlew` 和 `gradlew.bat` 如果你在Windows上构建).

## 当前状态
- [x] ~~OpenJDK 9 Mobile port: ARM32, ARM64, x86, x86_64.~~ 替换为 JRE8.
- [x] OpenJDK 8 移动端口: ARM32, ARM64, x86, x86_64
- [x] OpenJDK 17 移动端口: ARM32, ARM64, x86, x86_64
- [x] 无脑模组安装程序
- [x] 带GUI的Mod安装程序.已使用`Caciocavallo`无X11的AWT项目。
- [x] OpenJDK环境下的OpenGL
- [x] OpenAL (适用于大多数设备)
- [x] 支持Mincraft 1.12.2及更低版本.使用[lwjglx](https://github.com/PojavLauncherTeam/lwjglx),LWJGL3的LWJGL2兼容层
- [x] 支持Minecraft 1.13 及更高版本.使用[GLFW stub](https://github.com/PojavLauncherTeam/lwjgl3-glfw-java).
- [x] 支持Minecraft 1.17 及更高版本.使用[Holy GL4ES](https://github.com/PojavLauncherTeam/gl4es-114-extra)
- [x] 游戏分辨率缩放
- [x] 新的输入管道重写为本机代码,以提高性能
- [x] 重写了整个操作方式(感谢@Mathias-Boulay)
- [ ] 还有更多到来!

## 了解出现的问题
- 控制器模块不工作
- 在加载游戏或加入世界时,Android 5.X可能会经常发生随机崩溃
- 使用大型的整合包,纹理可能会出现错乱
- 可能更多,这就是为什么我们有一个错误追踪器

## 许可证
- [GNU GPLv3](https://github.com/Vera-Firefly/PojavLauncher-Beta-Zink/blob/v3_openjdk/LICENSE).

## 贡献
欢迎投稿!我们欢迎任何类型的贡献,不仅仅是代码.例如,您可以帮忙翻译!

对此存储库的任何代码更改都应作为Pull请求提交.描述应该解释代码的功能,并给出执行步骤.

## 信用和第三方组件及其许可证
- [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM Launcher): Unknown License/[Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) or GNU GPLv2.
- Android支持库 : [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [GL4ES](https://github.com/PojavLauncherTeam/gl4es): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).<br>
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).<br>
- [LWJGL3](https://github.com/PojavLauncherTeam/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [LWJGLX](https://github.com/PojavLauncherTeam/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): 未知许可证<br>
- [Mesa 3D图形库](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [pro-grade](https://github.com/pro-grade/pro-grade) (Java沙盒安全管理器): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).
- [xHook](https://github.com/iqiyi/xHook) (用于退出代码处理): [MIT and BSD-style licenses](https://github.com/iqiyi/xHook/blob/master/LICENSE).
- [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
- [virglrenderer](https://github.com/PojavLauncherTeam/virglrenderer): [MIT License](https://gitlab.freedesktop.org/virgl/virglrenderer/-/blob/master/COPYING).
- 感谢[MCHeads](https://mc-heads.net)提供Minecraft头像组件

## 更多
* 如果你想要一种不同的体验,试试实验版本:[Pojav EXP](https://github.com/Vera-Firefly/PojavLauncher-Experimental-Edition)
