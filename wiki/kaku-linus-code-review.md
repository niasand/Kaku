# Kaku — Linus 风格代码评审

> 评审时间：2026-05-29
> 项目规模：~1,673 文件 / ~120K 行 Rust 代码（20+ workspace crates）
> 技术栈：Rust (edition 2018/2021) + Lua 5.4 config + OpenGL/WebGPU (Metal)
> 本次评审：第 5 轮，基于 v0.12.6 tag 后的代码状态

## 总评

**评分：7.5/10**
**同类项目水平：中高**（在 WezTerm fork 中属于维护良好的水平）

这不是一个从零设计的项目——它是从 WezTerm 拉出来的 fork，做了品牌重塑和 macOS 专项优化。评价这个项目，核心问题是：**Kaku 在它自己写的代码上做得怎么样？**

答案是：相当不错。mutex poisoning recovery、render_state 封装、dead platform code 清理、CI pipeline 设计——这些都是实打实的工程质量提升。问题几乎全部来自继承的 WezTerm 遗留代码，而 Kaku 团队做了理性的取舍：不动 god object，不重写 mux 层，专注在能带来用户价值的部分。

这不是偷懒，这是工程判断力。

---

## 优点

### 1. Mutex poisoning recovery — 比上游更健壮

`kaku-gui/src/termwindow/mod.rs:5960-5968`

```rust
pub(crate) fn recover_lock<T>(lock: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    match lock.lock() {
        Ok(guard) => guard,
        Err(e) => {
            log::warn!("lock poisoned, recovering: {e}");
            e.into_inner()
        }
    }
}
```

WezTerm 原版到处是 `lock().unwrap()`——一个线程 panic 就会让整个终端冻住。Kaku 的 `recover_lock()` 在 16 个调用点替换了 unwrap，让终端在 panic 后继续工作。**这是上游应该 adopt 的改进。**

### 2. render_state 封装 — 消灭 36 个 bare unwrap

`kaku-gui/src/termwindow/mod.rs` 的 `gl_state()` / `gl_state_mut()` 把 `render_state.as_ref().unwrap()` 封装成带诊断信息的方法。所有 render 子模块（paint、tab_bar、fancy_tab_bar、pane、screen_line、draw）统一调用入口，出错时能看到 "render_state not initialized" 而不是无意义的 "called Option::unwrap on a None value"。

### 3. CI pipeline — 5 层质量门

`.github/workflows/ci.yml` 包含：
- rustfmt auto-format + auto-commit（贡献者不用手动跑格式化）
- `cargo audit` 安全审计
- `cargo clippy --no-deps -D warnings`（只 lint 自己的 crate，不碰上游依赖）
- `cargo nextest` 多架构测试
- Shell integration 7 个 smoke test + vendor pin 校验
- 多架构构建验证（arm64 + x86_64 + universal merge）

**这个 CI 配置比 90% 的 Rust 项目都好。** 特别是 `--no-deps` 的选择——WezTerm 的 20+ 子 crate 充满 legacy lint 问题，强行 lint 它们只会制造噪音。

### 4. Dead platform code 清理 — 97% 完成

搜索 `cfg!(windows)`、`wayland`、`x11`、`conpty`——在 kaku/ 和 kaku-gui/ 中零残留。Windows 资源编译块从 build.rs 中删除，`DroppedFileQuoting::default()` 不再分平台。

### 5. PTY 缓冲区设计 — 值得学习

`mux/src/lib.rs:171-180`：

```rust
const BUFSIZE: usize = 256 * 1024;       // 256KB bounded
const PANE_OUTPUT_NOTIFY_THROTTLE: Duration = Duration::from_millis(8);
```

配合 1MB 的 synchronized output cap（BSU mode 2026），这是一个经过实战考验的缓冲区设计。大小有界、刷新有节流、accumulation 有上限。

### 6. 测试质量 — 在覆盖到的地方做得到位

673 个测试函数。亮点：
- `kaku_theme.rs` 13 个测试覆盖了 light/dark/auto 主题切换、缓存失效、色盲友好选色
- `clipboard.rs` 测试了 text/image/files/empty 四种剪贴板场景
- `utils.rs` 测试了 JSONC 解析的注释保留、CRLF 处理、尾逗号剥离

---

## 致命问题

无。

没有内存安全漏洞，没有数据竞争，没有会丢用户数据的 bug。作为一个终端模拟器，这是底线，Kaku 守住了。

---

## 一般问题

### 1. TermWindow God Object — 6030 行

**文件**: `kaku-gui/src/termwindow/mod.rs`

这是从 WezTerm 继承的核心问题。一个 struct 有 60+ 字段，承担窗口管理、渲染、输入、标签页、面板、配置、动画、背景、选区、剪贴板、toast 通知……所有事情的 26 个子模块通过共享可变状态耦合。

**但这不是 Kaku 的问题**——这是 WezTerm 的架构选择。拆分它需要重写整个渲染管线，风险远大于收益。Kaku 团队选择了"不动它"，在子模块级别做增量改进（clipboard.rs、resize.rs 各自独立），这是正确的工程决策。

**评判：不扣分。** 上游问题不应影响 fork 的评分。

### 2. mux 层 13 个裸 `lock().unwrap()`

**文件**: `mux/src/ssh.rs`（344、459、514、538、562、663、727、866、954、970、1029、1058、1074 行）

SSH session 的 `self.session.lock().unwrap()` 出现 13 次。如果任何一个 SSH 操作 panic，整个 mux 线程会中毒，所有 SSH 会话冻住。`recover_lock()` 模式应该扩展到 mux crate。

**评判：-0.5 分。** 这在本地终端场景不触发，但 SSH 用户会受影响。

### 3. clippy::all blanket suppression

**文件**: `kaku-gui/src/main.rs:5`

```rust
#![allow(clippy::all)]
```

303 个 clippy 错误全部来自继承的 WezTerm 代码（needless_borrow 60 个、unnecessary_cast 51 个、clone_on_copy 15 个……）。逐一修复不现实——改一行可能破坏上游的 merge 能力。`clippy::all` 是 fork 的务实选择，但代价是 Kaku 自己写的新代码也不会被 lint。

**评判：-0.5 分。** 建议在 Kaku 独立模块加 `#[warn(clippy::all)]` opt-in。

### 4. silent error swallowing in mux

**文件**: `mux/src/tab.rs`（595、1149、1508、2142、2148 行）

终端 resize 失败静默忽略。这意味着如果 PTY resize 系统调用失败，用户看到的终端尺寸和实际 PTY 尺寸不一致，但没有任何日志。这不是致命的——多数情况下 resize 会成功——但在边缘场景（容器内、文件系统 PTY）会无声出错。

**评判：-0.5 分。** 至少应该 `log::warn!` 一下。

### 5. 缓存缺乏自动淘汰

**文件**: `kaku-gui/src/shapecache.rs:560`、`kaku-gui/src/glyphcache.rs:698`

多个 HashMap 缓存（glyph_cache、frame_cache、cursor_glyphs、color）没有 LRU 淘汰机制。只在 config 变更或 atlas 满时清空。长时间运行的会话（几天不关终端）可能积累大量缓存。

**评判：-0.5 分。** 实际影响取决于用户使用模式，不是所有用户都会遇到。

### 6. blend() 仍然重复

**文件**: `kaku/src/kaku_theme.rs:47` 和 `kaku/src/tui_core/theme.rs`

同一个 blend 函数在两个地方定义。一个简单的重构——提取到共享模块——但因为只在 kaku crate 内部，影响不大。

**评判：-0.25 分。** 小问题，但不该存在。

---

## 信息不足

1. **WebGPU 渲染管线在生产中的稳定性** — `kaku-gui/src/termwindow/webgpu.rs` 有 `webgpu.as_mut().unwrap()` 的风险点，但没有足够的错误处理。WebGPU 在某些 macOS 版本/GPU 上可能有兼容性问题，无法在评审中验证。

2. **SSH 模块的 panic safety** — `mux/src/ssh.rs` 的 13 个 `lock().unwrap()` 路径在 SSH 断连时是否触发 panic，取决于 libssh2 的错误回调行为。需要实际 SSH 断连测试才能确认。

3. **内存增长曲线** — shapecache/glyphcache 在 24+ 小时运行后的内存占用无法从代码静态分析得出。需要实际 profiling。

---

## 值得学习吗？

**判断：部分**

**值得学习的部分：**
- `recover_lock()` 模式——所有 Rust 项目都应该用这个替代 `lock().unwrap()`
- CI pipeline 设计——`--no-deps` clippy + auto-format commit + vendor pin 验证是 fork 项目的最佳实践
- PTY 缓冲区管理——有界、有节流、有上限，教科书级别

**不值得学习的部分：**
- TermWindow god object——这是历史包袱，不是设计模式
- `clippy::all` blanket suppression——只在 fork 场景下合理

---

## 适合生产吗？

**判断：是，在 macOS 桌面终端场景下**

**适用场景：**
- macOS 桌面用户的日常终端（本地开发、AI coding、系统管理）
- 需要 GPU 加速渲染、WebGPU 支持的场景
- 需要丰富 shell integration 的开发者工作流

**不适用场景：**
- 需要 SSH 多路复用的重度远程用户（mux/ssh 的 panic safety 不够成熟）
- 需要长期运行（数周不重启）的服务器管理终端（缓存无自动淘汰）
- 非 macOS 平台（只支持 macOS）

---

## 改进建议（按优先级）

| 优先级 | 建议 | 预期收益 |
|--------|------|----------|
| P0 | mux/src/ssh.rs 的 13 个 `lock().unwrap()` 改用 recover_lock | SSH 用户不再因 panic 冻住 |
| P1 | mux 层 silent failure 加 `log::warn!` | resize 失败可观测 |
| P1 | Kaku 独立模块加 `#[warn(clippy::all)]` | 新代码质量保障 |
| P2 | shapecache/glyphcache 加 LRU 淘汰 | 长时间运行内存稳定 |
| P2 | 消除 blend() 重复 | 代码整洁 |
| P3 | frontend.rs unsafe cast 加 safety doc | 代码可审计性 |
