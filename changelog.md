# Changelog

## 2026-06-06 — Auto Review: 10 Critical/High/Medium Bug Fixes

基于自动化代码审查（4 维度 × 18 个子代理），发现 50 个问题，经对抗验证后确认 48 个。已修复覆盖 P0-P2 共 10 项，发布 v0.12.9 → v0.12.14。

### Critical (2/2 ✅)

- **max_fps=0 除零 panic** — `config/src/config.rs` 添加 `validate_max_fps` 校验（min=1），`glyphcache.rs:605,639` + `window.rs:5283` 均加 `.max(1)` 兜底
- **pane tree take/replace panic 级联** — 引入 `PaneTreeGuard` RAII 守卫（55 行），覆盖 `tab.rs` 14 处调用点。panic 时 Drop 恢复空树，避免 `None` 级联崩溃

### High (4/9 ✅)

- **vtparse transmute UB** — `Action::from_u16()` / `State::from_u16()` 替换为安全 match（已有提交 `2e95264`）
- **utf16 对齐 UB** — `config/src/lua.rs:884` 的 `from_raw_parts` 替换为 `chunks_exact(2)` + `u16::from_ne_bytes`
- **OSC 52 pastejacking** — 新增 `enable_osc52_clipboard_write` 配置项（默认 true），添加 1MB payload 上限，超限拒绝并 `log::warn!`
- **configuration() 热路径 Mutex** — `parse_buffered_data` 循环前缓存 config；`tab.rs` 新增 `cached_gutters()` + `_with` 变体，消除 resize 时 ~30+ 次无意义锁获取

### Medium (4/12 ✅)

- **unwrap() panic** — `tab_bar_pixel_height().unwrap()` → `.unwrap_or(0.)`；`webgpu.as_mut().unwrap()` × 2 → `.context()?`
- **RefCell 重入 panic** — `WrappedSshPty` 从 `RefCell` 改为 `parking_lot::Mutex`
- **ConnectionUI 线程泄漏** — 存储 JoinHandle，Drop 时发 Close + join
- **child.kill() 静默丢弃** — 改为 `log::warn!` 记录错误

### Low/Correctness (2/4 ✅)

- **Recency score off-by-one** — `count` 从 0 改为 1，首个 pane 得分 1 vs 未标记 0
- **std::env::set_var 多线程安全** — `KAKU_UNIX_SOCKET` 提前到线程创建前；`SSH_AUTH_SOCK` 添加 `// SAFETY:` 注释

### Code Quality

- **重复代码提取** — `recover_lock`（4 处 → 1 处）+ `PasswordPromptHost`（2 处 → 1 处）合并到 `mux/src/util.rs`

### 发布版本

| Tag | 主要变更 |
|-----|---------|
| v0.12.9 | utf16 对齐修复 + max_fps 防护 |
| v0.12.10 | OSC 52 安全加固 |
| v0.12.11 | Recency score + env 安全 |
| v0.12.12 | RefCell + 线程泄漏 + kill 日志 |
| v0.12.13 | config 热路径缓存 |
| v0.12.14 | PaneTreeGuard panic 安全 |

### 未覆盖（架构级，建议单独规划）

- TermWindow 拆分（6030 行 / 99 字段 God Object）
- Config mega-struct 拆分（225 字段）
- perform_key_assignment 678 行 match 分发
- frame_cache / color HashMap 无界增长加 LFU 驱逐
- Mux 单例管理与业务逻辑混合
