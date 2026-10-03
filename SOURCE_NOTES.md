# 源码说明

## 核心文件

- `app/src/main/java/io/operit/jumpblocker/JumpBlocker.java`：LSPosed Hook 与外跳判定
- `app/src/main/java/io/operit/jumpblocker/MainActivity.java`：模块说明界面
- `app/src/main/assets/xposed_init`：传统 Xposed/LSPosed 入口
- `app/src/main/AndroidManifest.xml`：模块声明
- `app/build.gradle`：Android 16（API 36）构建配置

## 构建

使用 Android Studio、JDK 17 与 Android SDK 36：

```bash
./gradlew assembleDebug
```

Xposed API 使用 `compileOnly`，不会被打包进 APK。`settings.gradle` 已配置 Xposed API 仓库。

无 Gradle 环境的离线两步编译（v1.0.2 采用，已修复 v1.0.1 的桩类混入问题）：

```bash
# 1) 桩类单独编译，仅作 classpath
javac -d stubclasses $(find stubs -name '*.java')
# 2) 应用代码用桩类 classpath 编译（切勿把 stubs 加入 sources）
javac -classpath stubclasses:/path/to/android.jar -d classes $(find app/src/main/java -name '*.java')
# 3) d8 -> aapt2 compile/link -> 注入 classes.dex -> apksigner 签名
```

关键：`sources.txt` 只允许包含 `app/src/main/java` 下的源文件，`stubs/` 永远不得参与 dex 打包，否则运行时与 LSPosed 注入的真实 Xposed API 类冲突，模块无法加载。

## 版本记录

- **v1.0.5（当前）**：修复离线编译桩的 Xposed API 方法描述符。`XposedBridge.hookMethod(Member, XC_MethodHook)` 与 `XposedHelpers.findAndHookMethod(...)` 的桩返回类型由错误的 `void`/`Object` 改为真实的 `XC_MethodHook.Unhook`，并补充 `XC_MethodHook.Unhook` 桩。该错误曾导致 v1.0.4 虽成功注入 `com.jiaohua_browser` 和 WebView 进程，但所有 Hook 均以 `NoSuchMethodError` 注册失败。最终 dex 只引用 Xposed API，不包含桩类定义。
- v1.0.4：针对 `com.jiaohua_browser` 拉起 `com.heytap.browser` 加固，但因上述离线桩签名错误而实质失效。PendingIntent 在创建阶段净化外跳载荷，覆盖 `getActivity/getActivities/getService/getForegroundService/getBroadcast`；新增 ActivityTaskManager/ActivityManager Binder 代理、`IntentSender.sendIntent`、`Intent.prepareToLeaveProcess` 防线；Hook 采用方法签名去重，并按真实返回类型安全取消。
- v1.0.3：修复 v1.0.2 漏打包 `assets/xposed_init` 的问题；该文件内容为 `io.operit.jumpblocker.JumpBlocker`，LSPosed 可据此发现入口，但对目标广告跳转的拦截仍不完整。
- v1.0.2：已废弃——修复了桩类冲突，但手工打包时漏掉 `assets/xposed_init`。
- v1.0.1：已废弃——构建时把 `stubs/` 一并编译进 classes.dex（7 个桩类定义），与 LSPosed 注入类冲突。
- v1.0.0：基线版本，拦截能力较弱（无硬拦截名单）。

## 作用域

LSPosed 中勾选的应用就是被限制的应用。请勿勾选系统框架、系统桌面、LSPosed 管理器或本模块。

## 说明

`stubs/` 是在无完整 Gradle 环境下做离线编译检查时使用的最小接口桩，不参与 Android Studio 的正常构建。