# Kaku — Linus-Style Code Review

> "I'm not saying it's bad code. I'm saying some of these design decisions would make me question whether the author has ever maintained a project past v0.1."

**Project**: Kaku — GPU-accelerated terminal emulator forked from WezTerm
**Language**: Rust (edition 2018/2021)
**Platform**: macOS only
**Version**: 0.10.0
**Review Date**: 2026-05-23

---

## Overall Rating: 5/10

一个有野心的 fork，AI-native 定位有差异化价值，但代码库背负了太重的 WezTerm 历史包袱。新写的 Kaku 特有代码（AI 系统、TUI config）质量明显高于继承的 WezTerm 核心。问题在于：你 fork 了一个 200K+ LoC 的项目，却只清理了最表层的平台代码，核心架构的 god object 问题基本没动。

**评分说明**：5 分不是"一般"，是"能用但有严重的结构性问题需要还债"。如果只看 Kaku 自己新写的代码，能到 7 分。

---

## 1. 致命问题

### 1.1 `commands.rs:1055` — 1452 行的 match 函数

```
kaku-gui/src/commands.rs:1055  fn label_string() — 1,452 lines
kaku-gui/src/commands.rs:35     fn us_layout_shift() — 996 lines
kaku/src/config_tui/mod.rs:197  fn normal_mode_action() — 1,216 lines
config/src/config.rs:1008       fn default_ulimit_nproc() — 937 lines
```

这不是代码，这是把数据硬编码成了函数。`label_string()` 就是个 `HashMap<&str, String>`，非要写成 1452 行的 match。Rust 的 `match` 很强大，但不意味着你应该把整个宇宙塞进一个 match expression。

**这设计是错的。** 这种函数应该用数据结构（HashMap/BTreeMap）或宏生成，不该手写。每次加一个 key binding 要改 1452 行函数里的某个位置——这维护成本是 O(n²) 的。

### 1.2 `termwindow/mod.rs` — God Object，90+ 字段，6,034 行

`kaku-gui/src/termwindow/mod.rs:938` — `TermWindow` struct 有 90+ 个字段，管理窗口、渲染、输入、tab、缓存、GPU 状态……什么都有。

4 个 `impl` 块（行 1079, 1296, 2764, 5813），最大的一个有 105 个方法。一个 struct 上的 impl 块出现 4 次，这不是"组织代码"，这是不知道该把代码放哪。

**这设计是错的。** TermWindow 违反了 SRP 到了荒谬的程度。它同时是一个 window manager、rendering pipeline、input handler、tab manager 和 GPU state machine。正确做法是把这些关注点拆成独立的 trait 或组合模式——`TermWindow` 持有 `Renderer`、`InputHandler`、`TabManager` 等组件，而不是自己就是所有这些东西。

### 1.3 遗留平台代码：1067 处死代码

```
X11 references:     781 instances
Wayland references: 247 instances
Windows/ConPTY:      39 instances
```

你是一个 **macOS-only** 的项目。你的 `Cargo.toml` 里写着 `target = macos`。但你代码里还留着 1067 处 X11/Wayland/Windows 的引用。这不是"兼容性"，这是"懒得清理"。

每行死代码都是维护成本。每次有人 grep 代码库找 X11 相关逻辑，都要花时间判断"这行到底有没有人用"。你的编译时间也被这些无用代码拖慢了。

---

## 2. 严重问题

### 2.1 unwrap/expect 滥用：227+ 处

```
kaku-gui/src/termwindow/mod.rs:54  unwrap/expect calls
mux/src/tab.rs:52                    unwrap/expect calls
kaku/src/config_tui/mod.rs:89        unwrap/expect calls
config/src/config.rs:22              unwrap/expect calls
```

`termwindow/mod.rs:2171` — `.get().expect("on main thread").beep()`

这是一个 GUI 应用。用户看到的不是 stack trace，是闪退。每个 `unwrap()` 在生产环境都是一颗定时炸弹。特别是 `shapecache.rs` 里的 16 个 unwrap——字体加载失败、纹理创建失败、GPU 内存不足，任何一个都会让整个应用崩溃。

**WezTerm 也这么写，但 WezTerm 是个多平台项目，有社区贡献者在不同平台上测试。你是一个 macOS-only 的商业产品，标准应该更高。**

### 2.2 Clippy 抑制：61 处 `#![allow(clippy::...)]` 在 main.rs

`kaku-gui/src/main.rs` 有 61 个 clippy lint 抑制。60 个。六——十——个。

其中包含 `float_cmp`、`cast_abs_to_unsigned`、`unwrap_or_default`。Clippy 在告诉你代码有问题，你的回应是把 Clippy 嘴堵上。

### 2.3 核心渲染模块零测试

```
kaku-gui/src/termwindow/ — 23 个文件，0 个测试
```

终端模拟器的**核心渲染路径**没有一个测试。`keyevent.rs`（48.4K）、`mouseevent.rs`（69.7K）、`selection.rs`、`clipboard.rs`——这些都是用户每天交互的核心功能，没有测试意味着每次改动都是在赌。

`doctor.rs` 有 17 个高质量测试（包含边界情况），`session_restore.rs` 有 3 个有意义的测试。这说明团队**会写测试**，只是没在最重要的地方写。

### 2.4 `tab.rs` — 3111 行，TabInner 94 个方法

`mux/src/tab.rs:105` — `Tab` 包 `Mutex<TabInner>`，而 `TabInner` 有 94 个方法。一个内部状态类型需要 94 个方法来管理，说明你的抽象层次不对。

`mux/src/tab.rs:282` — `pane_tree()` 函数 134 行。二叉树操作写成 134 行的单函数，没有辅助方法。读这段代码需要的认知负载比读一段精心分解的代码高一个数量级。

---

## 3. 一般问题

### 3.1 代码重复

```
blend() — kaku/src/tui_core/theme.rs:25 和 kaku/src/kaku_theme.rs:47 完全相同
is_executable() — config/src/lib.rs:510 和 kaku/src/init.rs:261 功能重复
```

两个文件里的 `blend()` 是一字不差地复制粘贴。这不是"相似的逻辑"，这是 literal duplicate。

### 3.2 Config 巨型结构体

`config/src/config.rs` — 225 个 `#[dynamic]` 字段，37 个测试。平均 6 个字段共享 1 个测试。这不是测试，这是摆设。

Config struct 是整个项目的依赖瓶颈——8 个模块直接 import `ConfigHandle`。任何配置变更都可能引发级联影响。

### 3.3 CI 无覆盖率报告

`.github/workflows/ci.yml` 跑了 format、test、check、build——但没有覆盖率报告。不知道哪些代码被测试覆盖了，哪些没有。在一个 200K+ LoC 的项目里靠感觉管理测试覆盖，不靠谱。

### 3.4 CODE_REVIEW_INDEX 过时

文档中引用的多个文件和路径已不存在：
- `kaku-ai-utils` crate 不存在
- `ai_chat_engine/approval.rs` 路径不准确
- `ai_tools/shell.rs` 和 `ai_tools/paths.rs` 路径不准确
- 声称的测试数量与实际不符

文档比代码更快地腐化了。过时的文档比没有文档更危险——它会误导新人。

### 3.5 `std::thread::sleep` 在 GUI 线程

`kaku-gui/src/stats.rs:201` — `std::thread::sleep(Duration::from_secs(1))` 在 GUI 代码里直接 sleep。虽然是在统计日志循环中，但这是 blocking IO 的信号——如果统计功能变复杂，这里会成为卡顿源。

---

## 4. 值得肯定的设计

**不是所有东西都烂。** 以下是我认为设计得不错的部分：

### 4.1 AI Tool 系统的安全模型

AI tool framework 的分层安全设计（path guards、shell command approval、byte budgets、cooperative cancellation）是经过深思熟虑的。这比很多"AI coding assistant"的安全设计强得多。

### 4.2 Shell Integration 的鲁棒性

`kaku/src/doctor.rs` 的 17 个测试覆盖了各种边界情况（缺失文件、格式错误、不兼容配置、遗留配置检测），诊断逻辑写得扎实。

### 4.3 Build & Release Pipeline

`scripts/release.sh` 有预检、可恢复、公证重试、Homebrew 验证轮询。发布工程做得很专业，很多项目在这个阶段都偷懒了。

### 4.4 无循环依赖

14 个 workspace crate，无循环依赖。这在大型 Rust 项目里不容易做到，说明最初的模块划分是有远见的。

---

## 5. 评分维度

| 维度 | 评分 | 说明 |
|------|------|------|
| **架构设计** | 4/10 | God Object 问题严重，但无循环依赖 |
| **代码质量** | 5/10 | Kaku 新代码质量好，WezTerm 继承代码质量差 |
| **工程实践** | 6/10 | CI 基本完整，但无覆盖率；测试分布不均 |
| **性能/安全** | 6/10 | AI 安全模型好，unwrap 滥用是隐患 |
| **可维护性** | 4/10 | 1000+ 行函数 + 1067 处死代码 = 维护噩梦 |

---

## 6. 如果我来重构，优先级

| 优先级 | 动作 | 预期收益 |
|--------|------|----------|
| **P0** | 砍掉 X11/Wayland/Windows 1067 处死代码 | 减少 30%+ 编译体积和认知负载 |
| **P0** | `label_string()` 等巨型 match 用宏或数据结构重写 | 从 O(n²) 降到 O(n) 维护成本 |
| **P1** | `TermWindow` 拆分为 `Renderer` + `InputHandler` + `TabManager` | 每个 < 1000 行，可独立测试 |
| **P1** | 核心渲染路径加基础测试 | 防止回归 |
| **P2** | unwrap 替换为 `context()` / `with_context()` | 生产环境不再闪退 |
| **P2** | Clippy 抑制从 61 降到 < 10 | 让 lint 重新为你工作 |

---

*"The problem with code that's too clever is that it's too clever. The problem with code that's too dumb is that someone wrote 1,452 lines of match statements and called it a function."*
