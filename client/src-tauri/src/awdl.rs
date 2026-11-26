//! macOS：禁用 AWDL（`awdl0`）低延迟开关。
//!
//! macOS 的 `awdl0`（Apple Wireless Direct Link，用于隔空投送、隔空播放、随航、通用控制等）
//! 会周期性切换 Wi-Fi 信道扫描周边苹果设备，导致纯 Wi-Fi 远程控制时出现规律性延迟抖动和微卡。
//!
//! 本模块提供托盘菜单的开关（默认关闭），通过系统级 `LaunchDaemon` 持续保持 `awdl0` 处于关闭状态
//! （重启电脑或休眠唤醒后依然生效），并在 Airhand 卸载时自动移除守护服务并恢复 `awdl0`：
//! * Airhand 主进程本身不常驻后台循环、不反复派生子进程；
//! * 实时状态通过 `libc::getifaddrs` 系统调用读取 `awdl0` 的 `IFF_UP` 标志（零子进程、零泄漏）；
//! * 安装 / 卸载 `LaunchDaemon` 需要管理员权限，切换时通过 macOS 原生授权框（`osascript`）请求一次授权，
//!   平时运行和重启不再弹窗。

use std::ffi::CStr;
use std::path::Path;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};

use base64::Engine as _;

const LABEL: &str = "app.airhand.disable-awdl";
const SUPPORT_DIR: &str = "/Library/Application Support/Airhand";
const SCRIPT_PATH: &str = "/Library/Application Support/Airhand/disable-awdl.sh";
const PLIST_PATH: &str = "/Library/LaunchDaemons/app.airhand.disable-awdl.plist";

/// 极简守护脚本：每 5 秒检查一次 `awdl0`，仅在其被系统重新拉成 `UP` 时才执行 `ifconfig awdl0 down`。
const SCRIPT_CONTENT: &str = "\
#!/bin/sh
while true; do
  case \"$(/sbin/ifconfig awdl0 2>/dev/null)\" in
    *\"<UP,\"*|*\"<UP>\"*) /sbin/ifconfig awdl0 down ;;
  esac
  sleep 5
done
";

const PLIST_CONTENT: &str = "\
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">
<plist version=\"1.0\">
<dict>
  <key>Label</key>
  <string>app.airhand.disable-awdl</string>
  <key>ProgramArguments</key>
  <array>
    <string>/Library/Application Support/Airhand/disable-awdl.sh</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
</dict>
</plist>
";

/// 防止授权框未关闭时重复点击托盘菜单弹出多个授权框
static BUSY: AtomicBool = AtomicBool::new(false);

/// `awdl0` 网卡接口的实时状态
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LinkState {
    /// `awdl0` 处于启用状态（`IFF_UP`）
    Active,
    /// `awdl0` 处于关闭状态（已禁用）
    Down,
    /// 本机未检测到 `awdl0` 接口
    Unavailable,
}

/// 托盘菜单展示的 AWDL 开关与实时状态
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct State {
    /// Airhand 管理的禁用 AWDL 系统守护服务是否已安装（控制勾选项）
    pub daemon_installed: bool,
    /// `awdl0` 接口当前的实时状态（显示在开关文字后面）
    pub link: LinkState,
    /// 正在等待管理员授权 / 执行切换
    pub busy: bool,
}

/// 切换 AWDL 开关时的错误
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToggleError {
    /// 用户在管理员授权对话框里点了“取消”
    Canceled,
    /// 执行失败
    Failed(String),
}

struct IfAddrs(*mut libc::ifaddrs);

impl Drop for IfAddrs {
    fn drop(&mut self) {
        if !self.0.is_null() {
            // SAFETY: `self.0` came from a successful `getifaddrs` call and is freed once here.
            unsafe { libc::freeifaddrs(self.0) };
        }
    }
}

struct BusyGuard;

impl Drop for BusyGuard {
    fn drop(&mut self) {
        BUSY.store(false, Ordering::Release);
    }
}

/// 检查 Airhand 的禁用 AWDL 守护服务是否已安装（仅一次 `stat`，无子进程开销）
pub fn is_installed() -> bool {
    Path::new(PLIST_PATH).exists()
}

/// 通过 `getifaddrs` 直接查询 `awdl0` 接口当前是否开启（无子进程开销，零泄漏）
pub fn link_state() -> LinkState {
    let mut raw: *mut libc::ifaddrs = std::ptr::null_mut();
    // SAFETY: `raw` is initialized to null and populated by `getifaddrs`.
    if unsafe { libc::getifaddrs(&mut raw) } != 0 || raw.is_null() {
        return LinkState::Unavailable;
    }
    let _guard = IfAddrs(raw);
    let mut cur = raw;
    while !cur.is_null() {
        // SAFETY: `cur` is a valid element of the linked list owned by `_guard`.
        let ifa = unsafe { &*cur };
        if !ifa.ifa_name.is_null() {
            // SAFETY: `ifa_name` is a NUL-terminated C string from `getifaddrs`.
            let name = unsafe { CStr::from_ptr(ifa.ifa_name) };
            if name.to_bytes() == b"awdl0" {
                let up = (ifa.ifa_flags as libc::c_int) & libc::IFF_UP != 0;
                return if up {
                    LinkState::Active
                } else {
                    LinkState::Down
                };
            }
        }
        cur = ifa.ifa_next;
    }
    LinkState::Unavailable
}

/// 当前 AWDL 完整状态快照（轻量无阻塞）
pub fn state() -> State {
    State {
        daemon_installed: is_installed(),
        link: link_state(),
        busy: BUSY.load(Ordering::Acquire),
    }
}

/// 尝试占用切换锁：返回 `true` 时调用方必须随后在后台线程调用 [`apply_toggle`] 释放锁
pub fn begin_toggle() -> bool {
    !BUSY.swap(true, Ordering::AcqRel)
}

/// 在后台线程执行安装或卸载守护服务（由 [`begin_toggle`] 配对调用，结束后自动释放忙标记）
pub fn apply_toggle(enable: bool) -> Result<State, ToggleError> {
    let busy = BusyGuard;
    let (cmd, prompt) = if enable {
        (
            install_shell_command(),
            tr!(
                "Airhand 需要管理员权限以安装禁用 AWDL 的系统服务（优化 Wi-Fi 延迟，将停用隔空投送等苹果无线直连功能）。",
                "Airhand needs administrator privileges to install the AWDL disable service (improves Wi-Fi latency; disables AirDrop and Continuity features)."
            ),
        )
    } else {
        (
            uninstall_shell_command(),
            tr!(
                "Airhand 需要管理员权限以移除禁用 AWDL 的系统服务并恢复 awdl0（隔空投送等功能）。",
                "Airhand needs administrator privileges to remove the AWDL disable service and restore awdl0 (AirDrop and Continuity features)."
            ),
        )
    };
    run_privileged(&cmd, &prompt)?;
    drop(busy);
    Ok(state())
}

/// 卸载 Airhand 时调用：仅在已安装禁用 AWDL 守护服务时才请求权限卸载并恢复 `awdl0`
pub fn uninstall_if_installed() -> Result<(), String> {
    if !is_installed() {
        return Ok(());
    }
    let prompt = tr!(
        "卸载 Airhand 需要管理员权限以移除禁用 AWDL 的系统服务并恢复 awdl0。",
        "Uninstalling Airhand needs administrator privileges to remove the AWDL disable service and restore awdl0."
    );
    match run_privileged(&uninstall_shell_command(), &prompt) {
        Ok(()) => Ok(()),
        Err(ToggleError::Canceled) => Err("administrator authorization canceled".into()),
        Err(ToggleError::Failed(e)) => Err(e),
    }
}

fn install_shell_command() -> String {
    let script_b64 = base64::engine::general_purpose::STANDARD.encode(SCRIPT_CONTENT);
    let plist_b64 = base64::engine::general_purpose::STANDARD.encode(PLIST_CONTENT);
    format!(
        "set -e; \
         /bin/mkdir -p '{SUPPORT_DIR}'; \
         /usr/sbin/chown root:wheel '{SUPPORT_DIR}'; \
         /bin/chmod 755 '{SUPPORT_DIR}'; \
         /usr/bin/printf '%s' '{script_b64}' | /usr/bin/base64 -D > '{SCRIPT_PATH}'; \
         /usr/sbin/chown root:wheel '{SCRIPT_PATH}'; \
         /bin/chmod 755 '{SCRIPT_PATH}'; \
         /usr/bin/printf '%s' '{plist_b64}' | /usr/bin/base64 -D > '{PLIST_PATH}'; \
         /usr/sbin/chown root:wheel '{PLIST_PATH}'; \
         /bin/chmod 644 '{PLIST_PATH}'; \
         /bin/launchctl bootout system/{LABEL} 2>/dev/null || true; \
         /bin/launchctl bootstrap system '{PLIST_PATH}'; \
         /sbin/ifconfig awdl0 down 2>/dev/null || true"
    )
}

fn uninstall_shell_command() -> String {
    format!(
        "/bin/launchctl bootout system/{LABEL} 2>/dev/null || true; \
         /bin/rm -f '{PLIST_PATH}' '{SCRIPT_PATH}'; \
         /bin/rmdir '{SUPPORT_DIR}' 2>/dev/null || true; \
         /sbin/ifconfig awdl0 up 2>/dev/null || true"
    )
}

fn applescript_quote(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    out.push('"');
    for ch in s.chars() {
        match ch {
            '\\' => out.push_str("\\\\"),
            '"' => out.push_str("\\\""),
            '\r' => out.push_str("\\r"),
            '\n' => out.push_str("\\n"),
            _ => out.push(ch),
        }
    }
    out.push('"');
    out
}

fn build_applescript(shell_cmd: &str, prompt: &str) -> String {
    format!(
        "do shell script {} with prompt {} with administrator privileges",
        applescript_quote(shell_cmd),
        applescript_quote(prompt),
    )
}

fn run_privileged(shell_cmd: &str, prompt: &str) -> Result<(), ToggleError> {
    let script = build_applescript(shell_cmd, prompt);
    let out = Command::new("/usr/bin/osascript")
        .args(["-e", &script])
        .output()
        .map_err(|e| ToggleError::Failed(format!("can't run osascript: {e}")))?;
    if out.status.success() {
        return Ok(());
    }
    let stderr = String::from_utf8_lossy(&out.stderr).trim().to_owned();
    if is_user_canceled(&stderr) {
        return Err(ToggleError::Canceled);
    }
    Err(ToggleError::Failed(if stderr.is_empty() {
        format!("osascript exited with {}", out.status)
    } else {
        stderr
    }))
}

fn is_user_canceled(stderr: &str) -> bool {
    stderr.contains("(-128)") || stderr.to_ascii_lowercase().contains("user canceled")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn applescript_quotes_special_chars() {
        assert_eq!(applescript_quote(r#"a "b" \ c"#), r#""a \"b\" \\ c""#);
    }

    #[test]
    fn install_and_uninstall_commands_reference_owned_paths() {
        let install = install_shell_command();
        assert!(install.contains(PLIST_PATH));
        assert!(install.contains(SCRIPT_PATH));
        assert!(install.contains("/sbin/ifconfig awdl0 down"));
        assert!(!install.contains('\n'));

        let uninstall = uninstall_shell_command();
        assert!(uninstall.contains(PLIST_PATH));
        assert!(uninstall.contains(SCRIPT_PATH));
        assert!(uninstall.contains("/sbin/ifconfig awdl0 up"));
        assert!(!uninstall.contains('\n'));
    }

    #[test]
    fn detects_user_cancel_error() {
        assert!(is_user_canceled("execution error: User canceled. (-128)"));
        assert!(is_user_canceled("execution error: 用户已取消。 (-128)"));
        assert!(!is_user_canceled("execution error: permission denied (1)"));
    }

    #[test]
    fn link_state_query_succeeds_without_panicking() {
        let _ = state();
    }
}
