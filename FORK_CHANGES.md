# Fork 改动说明（基于上游 `sqlpxc/airhand`）

本仓库为 [`sqlpxc/airhand`](https://github.com/sqlpxc/airhand) 的个人维护 Fork，当前基于上游 `v0.4.1-rc.2`（`e13186e`），仅保留 `main` 单分支。  
后续每次向 `main` 推送改动时，均会在此同步记录**改动位置**与**新增功能/修复内容**，并在 Commit 正文中附详细说明。

---

## `0.4.1-rc.8`（2026-11-26）

### 新增功能（macOS 个人 Fork 专属）
1. **macOS 托盘菜单新增「禁用 AWDL」开关 + `awdl0` 实时状态反馈**
   - 位置：菜单栏托盘中紧跟在「不自动睡眠」下方，**默认关闭**，文字直接反馈网卡状态：`禁用 AWDL（awdl0：已开启 / 已关闭）`（切换授权期间显示「正在切换…」并防重复点击）。
   - **开机持久生效、零进程负担**：开启时经 macOS 原生管理员授权框安装系统 `LaunchDaemon`（`/Library/LaunchDaemons/app.airhand.disable-awdl.plist` 与 `/Library/Application Support/Airhand/disable-awdl.sh`），每 5 秒仅在 `awdl0` 被系统拉起为 `UP` 时执行 `ifconfig awdl0 down`；重启或休眠唤醒后依然生效。
   - **零内存泄漏 / 零子进程轮询**：Airhand 主进程不常驻额外后台循环，实时状态直接复用现有 2 秒托盘监听并通过 `libc::getifaddrs` 系统调用读取 `awdl0` 的 `IFF_UP` 标志（RAII 自动释放 `freeifaddrs`）。
   - **卸载自动还原**：使用菜单栏「卸载 Airhand…」时，若安装过该守护服务，会自动移除 `LaunchDaemon` 与脚本并执行 `ifconfig awdl0 up` 恢复隔空投送等功能；若从未开启则不额外弹管理员授权框。

### 改动位置
- `client/src-tauri/src/awdl.rs`（新增）：实现 `getifaddrs` 状态查询、基于 `osascript` 的安装/卸载 `LaunchDaemon`、防重复点击锁与单元测试
- `client/src-tauri/src/status.rs`：`AppStatus` 新增 macOS 专属 `awdl` 状态字段
- `client/src-tauri/src/tray.rs`：托盘菜单新增「禁用 AWDL（awdl0：已开启 / 已关闭）」勾选项与中英文文案
- `client/src-tauri/src/lib.rs`：接入启动初查、2 秒实时状态刷新与 `"toggle_awdl"` 异步切换处理
- `client/src-tauri/src/uninstall.rs`：卸载确认提示与清理步骤自动移除 AWDL 守护服务并恢复 `awdl0`
- `.github/workflows/build-dmg-apk.yml`：默认版本号更新至 `0.4.1-rc.8`

---

## `0.4.1-rc.7`（2026-11-26，提交 `d42c417`）

### 界面结构重构（首页设置 & 会话悬浮窗统一大分类）
1. **首页「设置」升级为 7 个一级大分类导航 + 二级详情页**
   - 告别超长平铺列表，首页仅展示 **视频与画面、音频、鼠标与触控、键盘与输入、屏幕按键、网络与本机、关于与数据** 7 个大分类入口（每项带当前设置摘要），点进对应二级页调整细项，支持系统返回键回到分类列表。
2. **远程会话「悬浮窗」重构为「4 个高频快捷 + 6 个固定大分类」**
   - 顶部保留 **键盘、声音、切到竖/横屏、断开** 4 个最高频单步动作；
   - 下方固定为 2×3 的 6 个一级大分类入口（**视频与画面、音频、鼠标与触控、键盘与输入、屏幕按键、手势说明**），与首页设置名称和归属 100% 对齐，所有细项（画质、编码、多屏、防窥、适应屏幕、滚轮、双指、快捷键、剪贴板、游戏按键等）均收纳进对应二级分类页，后续新增细项不再使主菜单膨胀。

### 改动位置
- `android/app/src/main/java/app/airhand/android/ui/phone/SettingsTab.kt`：重构为 7 大一级分类 + 二级详情页（支持 `BackHandler`）
- `android/app/src/main/java/app/airhand/android/ui/session/PhoneSession.kt`：重构 `MainMenu` 为 4 快捷动作 + 6 一级分类，拆分 `VideoCategoryPage` / `AudioCategoryPage` / `MouseTouchCategoryPage` / `KeyboardCategoryPage`
- `android/app/src/main/res/values/strings.xml`、`values-zh/strings.xml`：新增统一大分类中英文文案
- `.github/workflows/build-dmg-apk.yml`：默认版本号更新至 `0.4.1-rc.7`

---

## `0.4.1-rc.6`（2026-11-26，提交 `cd8c622`）

### 新增功能与体验优化
1. **滚动速度进一步细腻化（适配小屏手机）**
   - 将屏幕双指滑动基础灵敏度再减半（`WHEEL_PER_DP: 3.0 → 1.5`）；
   - 外接鼠标滚轮与双指滑动速度滑块下限统一放宽至 **`5%`**（`5%–200%`，步进 5%），小屏上也可精细慢滚。
2. **双击浮标直接开关键盘（可选开关）**
   - 绕开安卓系统「三指截屏」手势冲突，新增「**双击浮标开关键盘**」开关（默认关闭；开启后快速双击悬浮圆钮直接呼出/收起键盘，单击仍开菜单，拖动不受影响）。
3. **默认重力感应自动旋转 + 保留手动切换**
   - 新增「**重力感应自动旋转**」（默认开启）：竖拿手机自动切竖屏、横拿自动切横屏；浮标菜单保留手动「切到竖屏 / 切到横屏」按钮（显示目标方向更直观）。
4. **虚拟键盘弹出不再压扁画面（竖屏上移 / 横屏平移跟随光标）**
   - 弹出键盘时不再把远程画面强行压缩进剩余狭缝：
     - **竖屏**：Mac 画面完整保留原宽比例并自动居中在键盘正上方；
     - **横屏**：保持画面原比例不缩小，自动将当前光标所在行平移到键盘正上方可视区（可继续拖动查看别处，收起键盘自动回位），并自动暂时隐藏顶部状态浮窗、将快捷键栏切换为紧凑高度（`36dp`）。
5. **统一浮标菜单与首页设置的选项和默认状态**
   - 将新增功能开关（顶部状态浮窗、双击浮标开关键盘、鼠标平滑滚动、反转鼠标滚轮、双指滑动惯性）统一调整为**默认关闭**（重力自动旋转按需默认开启）；
   - 将首页「设置」与远程画面浮标菜单「**控制设置**」统一为完全一致的 3 组与排序：**显示与交互**、**外接鼠标滚轮**、**屏幕双指滑动**。

### 改动位置
- `android/app/src/main/java/app/airhand/android/data/Stores.kt`：新增 `autoRotate`、`doubleTapKeyboard`，将新增开关默认设为关闭，速度下限放宽至 `5%`
- `android/app/src/main/java/app/airhand/android/ui/session/Gestures.kt`：`WHEEL_PER_DP` 调整为 `1.5f`，`touchScrollFling` 默认关闭
- `android/app/src/main/java/app/airhand/android/ui/session/RemoteView.kt`：新增 `setKeyboardInset()` 与 `ensureCursorVisibleAboveKeyboard()`，弹出键盘时不压缩画面并平移跟随光标
- `android/app/src/main/java/app/airhand/android/ui/session/SessionActivity.kt`、`AndroidManifest.xml`：支持重力感应自动旋转与手动切换同步
- `android/app/src/main/java/app/airhand/android/ui/session/PhoneSession.kt`：接入非压缩键盘视口、紧凑 `KeysBar`、双击浮标开关键盘、统一「控制设置」子页与更直观的旋转按钮
- `android/app/src/main/java/app/airhand/android/ui/phone/SettingsTab.kt`：按与浮标菜单完全一致的分组与顺序重排设置项
- `android/app/src/main/res/values/strings.xml`、`values-zh/strings.xml`：同步新增中英文文案
- `android/app/src/test/java/app/airhand/android/{data/SettingsTest.kt,ui/session/GesturesTest.kt}`：更新并补充单测
- `.github/workflows/build-dmg-apk.yml`：默认版本号更新至 `0.4.1-rc.6`

---

## `0.4.1-rc.5`（2026-11-25，提交 `dc624a4`）

### 新增功能与体验优化
1. **Android 外接鼠标滚轮体验重构（不影响指针移动与连接流畅性）**
   - **速度范围扩展**：由原来的 5 档（`50%–200%`）升级为连续滑块 **`10%–200%`**（步进 5%），远控 macOS 时可下调至 `20%–35%` 实现逐行精细滚动。
   - **滚轮模式可选**：新增「**自适应加速（默认推荐）**」与「**固定线性**」。自适应模式下慢滚单格自动 `0.5×` 精细定位，连续快速拨动按角速度平滑加速至 `2.0×`，换方向瞬间归零不漂移。
   - **平滑滚动开关**：可选开启 `SmoothWheel`（60fps 微步插值，首步立即发出零起手延迟、总增量严格守恒，静止即停定时器），控 Mac 时呈现接近触控板的顺滑手感。
2. **Android 屏幕双指滑动密度归一化与独立调节**
   - 修复原 `Gestures.kt` 按物理像素 `px` 乘以 3 导致高分屏（3x DPI 手机）双指滑动过快约 3 倍的问题，改为按屏幕密度 `dp` 归一化；惯性滚动阈值同样按 `dp/ms` 归一化并略收紧衰减。
   - 新增独立的**双指滑动速度**滑块（`20%–200%`）与**双指滑动惯性**开关（关闭可松手即停）。
3. **会话浮标菜单实时微调**
   - 远程画面浮标主菜单新增「**滚动设置**」子页，无需断开连接即可边滚 Mac 页面边实时拖动滑块测试手感，设置自动持久化。

### 改动位置
- `android/app/src/main/java/app/airhand/android/data/Stores.kt`：新增 `WheelMode` 枚举、`smoothWheel`、`touchScrollSpeed`、`touchScrollFling`，放宽 `wheelSpeed` 范围至 `10..200`
- `android/app/src/main/java/app/airhand/android/ui/session/RemoteView.kt`：`MouseWheel` 增加自适应加速曲线，新增 `SmoothWheel` 平滑插值器并对接 `Gestures` 密度与双指设置
- `android/app/src/main/java/app/airhand/android/ui/session/Gestures.kt`：双指滑动与惯性按屏幕密度 `dp` 归一化，新增速度与惯性开关
- `android/app/src/main/java/app/airhand/android/ui/session/PhoneSession.kt`：浮标菜单新增「滚动设置」实时调节子页
- `android/app/src/main/java/app/airhand/android/ui/phone/SettingsTab.kt`：设置页升级滑块对话框并新增滚轮模式、平滑滚动、双指速度与惯性开关
- `android/app/src/main/res/values/strings.xml`、`values-zh/strings.xml`：同步新增中英文文案
- `android/app/src/test/java/app/airhand/android/{ui/session/MouseWheelTest.kt,ui/session/GesturesTest.kt,data/SettingsTest.kt}`：补充自适应加速、平滑滚轮、高分屏双指归一化与持久化单元测试
- `.github/workflows/build-dmg-apk.yml`：默认版本号更新至 `0.4.1-rc.5`

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
