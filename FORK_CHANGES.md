# Fork 改动说明（基于上游 `sqlpxc/airhand`）

本仓库为 [`sqlpxc/airhand`](https://github.com/sqlpxc/airhand) 的个人维护 Fork，当前基于上游 `v0.4.1-rc.2`（`e13186e`），仅保留 `main` 单分支。  
后续每次向 `main` 推送改动时，均会在此同步记录**改动位置**与**新增功能/修复内容**，并在 Commit 正文中附详细说明。

---

## `0.4.1-rc.4`（2026-11-25，提交 `462ef0f`）

### 新增功能
1. **Android 远程画面顶部实时状态信息浮窗**
   - 在远程画面最顶部新增半透明黑底圆角卡片 `StatsHud`（自动避开刘海/状态栏，触摸完全穿透，不遮挡顶部菜单栏操作）。
   - 实时展示 10 项指标：**连接**（直连 UDP / 中转 UDP）、**编码格式**、**帧率**、**分辨率**、**码率**、**解码器**（硬件 / 软件）、**解码耗时**、**RTT**、**抖动**、**丢包**。
   - 默认开启，支持在**浮标菜单（状态信息：开/关）**以及**设置 → 远程控制 → 顶部显示状态信息**中随时开关并持久化保存。
   - 浮标主菜单顶部的状态摘要中同步补上了实时视频分辨率。
2. **底层实时网络与解码指标**
   - Rust 控制端会话统计新增 `jitterMs`（基于相邻 Pong RTT 差值指数平滑）与 `lossPct`（基于因丢包重传卡顿帧与跳帧占比）。
   - Android 解码器暴露当前 `MediaCodec` 是否为硬件解码及平滑后的单帧解码耗时（每 500ms 刷新）。

### 优化与 CI
3. **缩短 macOS 合盖后恢复清晰度的等待时间**
   - 将合盖切回 HiDPI 的等待时间由原来的 `SETTLE = 2s`（叠加轮询间隔）拆分为独立的 `MODE_SETTLE = 500ms`，并在等待循环中按到期时间精确唤醒，合盖后模糊恢复速度加快约 1.5–2 秒。
4. **Android CI 自动跑单元测试**
   - 构建 APK 前自动执行 `./gradlew testDebugUnitTest`（校验 `MessagesTest` 中英文词条一致、`SettingsTest`、`ProtocolTest` 等）。

### 改动位置
- `crates/airhand-link/src/controller/session.rs`：新增 `jitter_ms`、`loss_pct` 统计与单测断言
- `client/src/lib/api.ts`：同步补充 `SessionEvent` 的 `jitterMs`、`lossPct` 类型定义
- `client/src-tauri/src/host/virtual_display/mac.rs`：新增 `MODE_SETTLE = 500ms` 及精确定时唤醒
- `android/app/src/main/java/app/airhand/android/core/Model.kt`：`Stats` 新增 `jitterMs`/`lossPct`，新增 `DecoderStats`
- `android/app/src/main/java/app/airhand/android/controller/VideoDecoder.kt`：上报硬件/软件解码状态与单帧解码耗时
- `android/app/src/main/java/app/airhand/android/controller/RemoteSession.kt`：解析新增统计字段并暴露 `decoderStats`
- `android/app/src/main/java/app/airhand/android/data/Stores.kt`：新增 `Settings.showStats` 配置项与序列化
- `android/app/src/main/java/app/airhand/android/ui/phone/SettingsTab.kt`：设置页新增「顶部显示状态信息」开关
- `android/app/src/main/java/app/airhand/android/ui/session/PhoneSession.kt`：实现顶部 `StatsHud`、浮标菜单开关及分辨率摘要
- `android/app/src/main/res/values/strings.xml`、`values-zh/strings.xml`：新增中英文状态浮窗与开关文案
- `android/app/src/test/java/app/airhand/android/data/SettingsTest.kt`：新增 `showStats` 持久化单测
- `.github/workflows/build-dmg-apk.yml`：默认版本升级至 `0.4.1-rc.4`，Android 构建前增加单元测试

---

## `0.4.1-rc.3`（2026-11-25，提交 `96bd381`、`0b9f467`）

### 修复与新增功能
1. **修复 macOS 15.6（M4 MacBook Air）合盖后虚拟显示器降级 1x 变糊且切不回 HiDPI 的问题**
   - 根因：WindowServer 持久化记录了异常的 `UnmirrorInfo`，合盖切到虚拟屏时落到 1x 模式，但直接调用 `CGConfigureDisplayWithDisplayMode` 切回 2x HiDPI 会被 WindowServer 误判为 `mode 9 -> 9` 无变化而跳过；且已打开的 `ScreenCaptureKit` 采集流可能卡在 `pointPixelScale = 1.0`。
   - 修复：在 `ah_vd_set_mode` 中检测到直接切换未生效时，自动执行两段“跳板切换”（先用会话级配置切到同点数 1x 等其他模式，间隔 50ms 后再永久切回目标 HiDPI 模式）；同时新增 `mode_generation()` 版本号，切完模式后强制重建 `SCStream` 采集流。
2. **新增手动构建 macOS DMG 与 Android APK 的 GitHub Actions Workflow**
   - 支持在 Actions 页面手动触发构建 `macos-arm64`（可选 `both` 含 x64）的 `.dmg` 和 Android `.apk`，兼容未配置自定义中转服务器变量的情况。

### 改动位置
- `client/src-tauri/src/host/virtual_display/mac.m`：`ah_vd_set_mode` 增加两段跳板切换逻辑
- `client/src-tauri/src/host/virtual_display/mac.rs`、`mod.rs`：新增 `MODE_GEN` / `mode_generation()` 通知采集层重开流
- `client/src-tauri/src/host/capture/sck.rs`：检测 `mode_generation()` 变化并重建 `SCStream`
- `.github/workflows/build-dmg-apk.yml`：新增手动构建 macOS DMG 与 Android APK 的工作流
