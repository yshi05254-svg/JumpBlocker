# 跨应用跳转阻断器

面向 Android 16 / LSPosed 的隐私模块。LSPosed 作用域即受限制应用列表。

## 版本
- **v1.0.5（当前）**：修复离线编译桩与真实 Xposed API 的返回类型不一致问题。`XposedBridge.hookMethod` 和 `XposedHelpers.findAndHookMethod` 现在均以 `XC_MethodHook.Unhook` 为返回类型，避免 LSPosed 注入 `com.jiaohua_browser` 后所有 Hook 因 `NoSuchMethodError` 注册失败。
- v1.0.4：针对 `com.jiaohua_browser` 拉起 `com.heytap.browser` 加固，但因离线桩方法描述符错误而实质失效。新增 ActivityTaskManager/ActivityManager Binder 代理、`IntentSender.sendIntent`、`Intent.prepareToLeaveProcess` 与 PendingIntent 创建阶段净化防线。
- v1.0.3：修复 v1.0.2 漏打包 `assets/xposed_init` 导致 LSPosed 无法发现入口的问题；APK 已包含 `io.operit.jumpblocker.JumpBlocker` 入口。
- v1.0.2：已废弃（虽修复桩类冲突，但手工打包漏掉 `assets/xposed_init`）。
- v1.0.1：已废弃（dex 混入桩类，LSPosed 无法加载）。

注意：签名证书与 v1.0.0 不同（原 keystore 丢失），**安装前需先卸载旧版**。

## 构建
项目可用 Android Studio（JDK 17）打开构建。依赖 Xposed API 82（compileOnly）。

## 安装
安装 APK → LSPosed 启用模块 → 作用域只勾选待限制 App → 强停并重开目标 App。

## 边界
拦截常规 Activity/IntentSender/PendingIntent 外跳，不改变 INTERNET 权限。native Binder、root shell、厂商私有链路、应用内 WebView 不在常规外部 Activity 跳转定义内。