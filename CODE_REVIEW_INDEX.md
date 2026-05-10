# Kaku Code Review Index

> Auto-generated code review and architecture map for developer onboarding and reference.
> Based on codebase snapshot at v0.10.0 (branch: main).

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture & Dependency Graph](#2-architecture--dependency-graph)
3. [Workspace Layout](#3-workspace-layout)
4. [Module Deep Dives](#4-module-deep-dives)
   - [4.1 kaku (CLI Binary)](#41-kaku-cli-binary)
   - [4.2 kaku-gui (GUI Application)](#42-kaku-gui-gui-application)
   - [4.3 Core Crates](#43-core-crates)
   - [4.4 AI Tools System](#44-ai-tools-system)
   - [4.5 Kaku-Specific Crates](#45-kaku-specific-crates)
   - [4.6 Utility Crates](#46-utility-crates)
5. [Build System](#5-build-system)
6. [Code Quality Summary](#6-code-quality-summary)
7. [Known Issues & Technical Debt](#7-known-issues--technical-debt)
8. [Key File Quick Reference](#8-key-file-quick-reference)

---

## 1. Project Overview

**Kaku** is a GPU-accelerated terminal emulator forked from WezTerm, rebranded and extended with AI-native features by author Tw93. It is positioned as "A fast, out-of-box terminal built for AI coding."

| Attribute | Value |
|-----------|-------|
| Language | Rust (edition 2018/2021) |
| Toolchain | Rust 1.93.0 (stable), nightly rustfmt |
| Platform | macOS only (current focus) |
| Version | 0.10.0 |
| License | MIT (inherited from WezTerm) |
| Rendering | OpenGL + WebGPU (Metal backend) |
| Config | Lua 5.4 via mlua |
| AI Providers | OpenAI-compatible API, Copilot OAuth, Codex |

### Binaries

| Binary | Crate | Purpose |
|--------|-------|---------|
| `kaku` | `kaku/` | CLI: launcher, shell integration, AI config TUI, self-update, doctor |
| `kaku-gui` | `kaku-gui/` | GUI terminal emulator with GPU rendering |
| `k` | `kaku-gui/src/bin/k.rs` | AI chat CLI (shares `kaku_gui_lib`) |
| `kaku-relay` | `crates/kaku-relay/` | WebSocket relay for desktop-mobile connectivity (independent deploy) |

---

## 2. Architecture & Dependency Graph

```
                        ┌──────────────────┐
                        │    kaku (CLI)    │
                        └────────┬─────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
          ▼                      ▼                      ▼
   kaku-ai-utils          kaku-remote             wezterm-gui-
      (utils)            (remote access)           subcommands
                                                       │
                        ┌──────────────────┐           │
                        │   kaku-gui       │◄──────────┘
                        │  (GUI + AI lib)  │
                        └────────┬─────────┘
                                 │
              ┌──────────────────┼──────────────────────┐
              │                  │                      │
              ▼                  ▼                      ▼
          window              mux                 kaku-remote
       (OS window)    (tab/pane/pty)           (remote access)
              │                  │
              │       ┌──────────┼──────────┐
              │       │          │          │
              │       ▼          ▼          ▼
              │   wezterm-term  config   wezterm-ssh
              │  (term state)  (Lua cfg)  (SSH client)
              │       │          │
              │       ▼          │
              └──► termwiz ◄────┘
               (terminal primitives)
```

**Dependency rule:** arrows point from "depends on" to "depended upon." `termwiz` and `config` are the foundational layers.

---

## 3. Workspace Layout

```
Kaku/
├── kaku/                    # CLI binary (~16.5K LoC)
│   └── src/
│       ├── main.rs          # Entry, clap CLI, main menu TUI
│       ├── ai_config/       # AI provider config TUI (9 providers)
│       ├── cli/             # Experimental mux CLI (19 subcommands)
│       ├── config_tui/      # Lua config file editor TUI
│       ├── chat.rs          # `kaku chat` → exec k binary
│       ├── doctor.rs        # Diagnostic health checks
│       ├── init.rs          # Shell integration bootstrap
│       ├── reset.rs         # Remove all Kaku managed state
│       ├── update.rs        # Self-updater (GitHub/Homebrew)
│       ├── shell.rs         # Shell type detection
│       └── ...
├── kaku-gui/                # GUI application (~160K+ LoC incl unicode_names)
│   └── src/
│       ├── main.rs          # GUI entry, single-instance handoff
│       ├── lib.rs           # Shared lib (AI, soul, tools) for k binary
│       ├── frontend.rs      # Event loop coordinator
│       ├── termwindow/      # Core rendering, input, tabs, panes
│       ├── overlay/         # Overlay system (AI chat, copy, launcher...)
│       │   └── ai_chat/     # Full AI chat TUI (Cmd+L)
│       ├── ai_client.rs     # HTTP client, SSE streaming, retry
│       ├── ai_chat_engine/  # Agent loop, approval, compaction
│       ├── ai_tools/        # Tool-calling framework
│       ├── ai_auth.rs       # Copilot/Codex OAuth
│       ├── ai_conversations.rs  # Persistent conversation store
│       ├── ai_state.rs      # UI state persistence
│       ├── ai_remote.rs     # Mobile client AI proxy
│       ├── soul.rs          # User identity system
│       ├── commands.rs      # Key bindings, menu bar
│       ├── scripting/       # Lua scripting bridge
│       └── ...
├── mux/                     # Multiplexer: tab/pane/window/PTY mgmt (~9.3K LoC)
├── term/                    # Terminal state machine: VT100/xterm emulation (~4.3K LoC)
├── termwiz/                 # Terminal primitives: parsing, caps, widgets
├── config/                  # Lua 5.4 config system (~3.8K LoC)
│   └── derive/              # ConfigMeta procedural macro
├── window/                  # Cross-platform window abstraction (macOS focus)
├── crates/
│   ├── kaku-ai-utils/       # Shared AI utility (model ID filter)
│   ├── kaku-relay/          # WebSocket relay (standalone deploy)
│   ├── kaku-remote/         # Remote access server (screen share, AI proxy)
│   ├── wezterm-*            # Inherited WezTerm crates (20+)
│   ├── bidi/                # Unicode bidi algorithm
│   ├── vtparse/             # VT parser state machine
│   ├── promise/             # Future/promise utilities
│   └── ...
├── deps/                    # Vendored native: cairo, freetype, harfbuzz, fontconfig
├── lua-api-crates/          # Lua API modules (battery, color, fs, mux, ssh...)
├── scripts/                 # Build, release, notarize scripts
├── assets/                  # App bundle, fonts, shell integration, vendor configs
├── docs/                    # User documentation
└── Makefile                 # Build targets (build, test, fmt, app, dmg, release)
```

---

## 4. Module Deep Dives

### 4.1 kaku (CLI Binary)

**Purpose:** Thin launcher that delegates to `kaku-gui` for the GUI, plus a comprehensive management CLI.

| Command | File | Lines | Responsibility |
|---------|------|-------|----------------|
| `(default)` | `main.rs` | 652 | Interactive main menu TUI when stdin is a tty |
| `kaku start` | `main.rs` | — | Delegates to `kaku-gui` binary |
| `kaku ai` | `ai_config/tui.rs` | 7,428 | Interactive TUI for 9 AI provider configs |
| `kaku chat` | `chat.rs` | 128 | `execvp` into `k` binary |
| `kaku config` | `config_tui/mod.rs` | 2,282 | Config file editor TUI |
| `kaku init` | `init.rs` | 312 | Shell integration (zsh/fish on macOS) |
| `kaku doctor` | `doctor.rs` | 1,509 | Health diagnostics with `--fix` auto-fix |
| `kaku update` | `update.rs` | 899 | Self-updater (GitHub/Homebrew) |
| `kaku reset` | `reset.rs` | 854 | Remove all Kaku managed state |
| `kaku cli` | `cli/mod.rs` | 224 | Experimental mux server CLI (19 sub-commands) |
| `kaku remote` | `kaku-remote` | — | QR code for iOS connection (feature-gated) |

**Key observations:**
- `ai_config/tui.rs` at 7,428 lines is the largest file — acknowledged as V0.10 compromise, planned refactor to per-provider adapters in V0.11
- 14 crate-level clippy lint suppressions in `main.rs` (inherited from WezTerm)
- Duplicated utilities: `is_executable()` in both `chat.rs` and `init.rs`; `blend()` in both `kaku_theme.rs` and `tui_core/theme.rs`
- No TODO/FIXME markers found — notably clean

---

### 4.2 kaku-gui (GUI Application)

**Rendering pipeline:**
```
OS Events → GuiFrontEnd → TermWindow → RenderState → GPU (OpenGL/WebGPU)
                │              │
                │              ├── Tab management
                │              ├── Pane/split layout
                │              ├── Input routing (InputMap)
                │              └── Overlay dispatch
                │
                └── Mux notifications (pane focus, resize, alerts)
```

**Overlay system:** Full-pane TUI overlays spawned in background threads:
- **AI Chat** (`overlay/ai_chat/`) — Cmd+L, 11 submodules, markdown rendering, IME support, slash commands ("waza")
- Copy Mode, Launcher, QuickSelect, Confirm, Prompt, Selector, ConfirmClosePane

**AI subsystem architecture:**
```
ai_client.rs ──► SSE streaming, retry, auth
     │
     ▼
ai_chat_engine/
     ├── mod.rs      ──► Agent loop (25 rounds max), system prompt, memory curator
     ├── approval.rs ──► Shell command safety analysis (allowlist + parser)
     └── compact.rs  ──► Micro-compaction (truncate tool output, spill to disk)
     │
     ▼
ai_tools/ ──► Modular tool-calling framework
     ├── fs      (read, list, write, patch, mkdir, delete)
     ├── shell   (exec, bg, poll)
     ├── search  (grep, symbol, web)
     ├── web     (fetch, http_request, read_url)
     ├── project (summary, file_tree)
     ├── paths   (resolution + sensitive-path guards)
     ├── soul    (identity, memory, spill cleanup)
     └── registry (tool defs, budgets, schema)
```

**Key modules:**

| Module | Lines | Responsibility |
|--------|-------|----------------|
| `termwindow/mod.rs` | 6,227 | Core window: rendering, input, tabs, panes, splits |
| `commands.rs` | 2,799 | Key bindings, menu bar, command palette |
| `ai_chat_engine/approval.rs` | 913 | Shell safety analysis |
| `ai_chat_engine/mod.rs` | 862 | Agent loop, system prompt, memory curator |
| `ai_client.rs` | 755 | HTTP client, config loading, SSE streaming |
| `ai_conversations.rs` | 546 | Persistent conversation store (100 cap, LRU eviction) |
| `soul.rs` | ~400 | User identity (SOUL/STYLE/SKILL/MEMORY files) |

---

### 4.3 Core Crates

#### mux (Multiplexer) — ~9,300 LoC

**Purpose:** Tab/pane/window management layer — the "window manager" of the terminal.

| File | Lines | Key Content |
|------|-------|-------------|
| `lib.rs` | 1,930 | `Mux` singleton, `MuxNotification` (18 variants), pane lifecycle |
| `tab.rs` | 3,112 | Binary tree split management, pane navigation, codec serialization |
| `pane.rs` | 1,179 | `Pane` trait (~50 methods), PTY output routing |
| `domain.rs` | 813 | `Domain` trait, `LocalDomain` PTY management |
| `window.rs` | 269 | Window workspace, tab list, position |

**Notable:** PTY output uses 256KB socketpair buffers with 8ms throttle. Synchronized output (BSU mode 2026) with 1MB cap.

#### term (wezterm-term) — ~4,300 LoC

**Purpose:** Terminal state machine — VT100/xterm escape sequence processing.

| File | Lines | Key Content |
|------|-------|-------------|
| `terminalstate/mod.rs` | 2,940 | Escape dispatch, cursor control, DEC modes, SGR, scroll regions |
| `screen.rs` | 1,236 | `VecDeque<Line>` with scrollback, line rewrapping, stable row indexing |
| `lib.rs` | 135 | Type aliases (`PhysRowIndex` vs `VisibleRowIndex` — different sizes to prevent mixing) |

**Notable:** Type-safe row indices (usize/i64/i32/isize) prevent arithmetic bugs. Screen recycles lines instead of alloc+free. Bundled terminfo via `include_bytes!`.

#### config — ~3,810 LoC

**Purpose:** Lua 5.4 configuration with filesystem watching and live reload.

| File | Lines | Key Content |
|------|-------|-------------|
| `config.rs` | 2,827 | ~200+ fields with `ConfigMeta` derive, `RemoteConfig`, font rules |
| `lib.rs` | 983 | Lua context management, bytecode cache (`KLBC` magic), file watcher |

**Notable:** Bytecode cache with source hash validation. File watcher watches parent dirs (not files) for atomic rename detection. Main-thread-only Lua execution.

#### window — ~516 LoC (header)

**Purpose:** Cross-platform window abstraction (macOS focus).

Key trait: `WindowOps` — 30+ methods for lifecycle, input, rendering, clipboard. macOS implementation wraps cocoa/objc with AppKit integration.

#### termwiz

**Purpose:** Foundational terminal primitives — escape parsing, capability probing, input events, surface/cell modeling. Nearly every crate depends on it.

---

### 4.4 AI Tools System

The tool-calling framework in `kaku-gui/src/ai_tools/`:

| Tool | File | Capabilities |
|------|------|-------------|
| **fs** | `fs.rs` | read, list, write, patch, mkdir, delete — with byte caps |
| **shell** | `shell.rs` | exec (60s timeout, process groups), bg/poll — with cancellation |
| **search** | `search.rs` | grep (rg/grep fallback), symbol search, web search (Brave/PipeLLM/Tavily) |
| **web** | `web.rs` | fetch (defuddle.md / r.jina.ai), generic HTTP client, URL reader |
| **project** | `project.rs` | project summary (language/build detection), file tree (depth 3, max 500) |
| **paths** | `paths.rs` | Path resolution, sensitive-path blocklist, cwd-escape prevention |
| **soul** | `soul.rs` | Identity reading (SOUL/STYLE/SKILL/MEMORY), spill file cleanup |

**Security model:**
- Path guards block `~/.ssh/`, `~/.aws/credentials`, `~/.gnupg/`, assistant.toml/secrets
- Three-layer cwd-escape prevention (canonical, ancestor canonical, lexical simulation)
- Shell command approval system: allowlist-based with full shell parser for pipes/redirections
- Sensitive command detection: curl flag analysis, git write detection
- Cooperative cancellation via `Arc<AtomicBool>` across all long-running ops
- Per-tool byte budgets with three tiers (brief/default/full)

---

### 4.5 Kaku-Specific Crates

| Crate | Purpose | Notable |
|-------|---------|---------|
| `kaku-ai-utils` | Model ID filter (excludes whisper/tts/dalle/etc.) | Single function, no deps |
| `kaku-relay` | WebSocket relay for desktop-mobile NAT traversal | Standalone deploy (excluded from workspace), axum + tokio |
| `kaku-remote` | Remote access: screen share, input forwarding, AI chat proxy, QR provisioning | 32-hex token auth, screen capture at 16ms intervals, max 4 in-flight AI requests |

---

### 4.6 Utility Crates

Inherited from WezTerm (20+ crates). Key ones:

| Crate | Purpose |
|-------|---------|
| `wezterm-escape-parser` | Escape sequence parsing (CSI/OSC/APC/DCS) |
| `wezterm-font` | Font loading (FreeType/HarfBuzz), rasterization, shaping |
| `wezterm-ssh` | SSH client (libssh2-based) with SFTP support |
| `wezterm-surface` | Terminal surface model (lines, cells, changes) |
| `wezterm-cell` | Cell attributes, color, image cell |
| `wezterm-dynamic` | Dynamic value types with derive macros |
| `bidi` | Unicode bidirectional algorithm |
| `vtparse` | VT parser state machine tables |
| `promise` | Future/promise with spawn utilities |
| `lfucache` / `frecency` | Cache eviction strategies |
| `codec` | Terminal encoding/decoding |

---

## 5. Build System

### Toolchain
- **Rust:** 1.93.0 stable, nightly rustfmt
- **macOS target:** 11.0 (Big Sur)
- **Profiles:** `release` (opt-level 3, fat LTO), `release-opt` (size-optimized "z")

### Key Make Targets
| Target | Description |
|--------|-------------|
| `make build` | Build kaku, kaku-gui, mux-server-impl |
| `make app` | macOS .app bundle |
| `make dev` | Hot-reload via cargo-watch |
| `make test` | cargo nextest (excludes flaky ligatures) |
| `make check` | Multi-crate cargo check |
| `make fmt` | Nightly rustfmt |
| `make dmg` | Build + notarize DMG |
| `make release` | Full release pipeline |

### Build Pipeline (`scripts/build.sh`)
7-stage macOS-only: compile → .app bundle → download vendor plugins → copy resources → code signing → update ZIP → DMG creation.

### Release Pipeline (`scripts/release.sh`)
Full pipeline with resume support: pre-flight checks → build → notarize → git tag → GitHub Release → Homebrew tap update with verification polling.

---

## 6. Code Quality Summary

### Strengths

1. **Excellent documentation** — module-level `//!` docs, doc comments on public APIs, architecture decision explanations
2. **Comprehensive tests** — approval system (30+ tests), path security, conversation store, theme resolution, doctor checks
3. **Security-conscious design** — sensitive path guards, shell command safety analysis, atomic file writes, 0o600 permissions on secrets
4. **Defensive coding** — timeout-protected subprocesses, graceful fallbacks, retry with exponential backoff
5. **Robust AI tool system** — modular decomposition, per-tool byte budgets, cancellation support, spill files for overflow
6. **Sophisticated release engineering** — pre-flight checks, resume support, notarization retry, Homebrew tap verification
7. **Well-structured agent architecture** — 25-round limit, micro-compaction, memory curator with separate model

### Areas for Improvement

1. **Large files needing decomposition:**
   - `kaku/src/ai_config/tui.rs` — 7,428 lines (acknowledged, V0.11 refactor planned)
   - `kaku-gui/src/termwindow/mod.rs` — 6,227 lines
   - `mux/src/tab.rs` — 3,112 lines
   - `kaku-gui/src/commands.rs` — 2,799 lines
   - `config/src/config.rs` — 2,827 lines
   - `kaku/src/config_tui/mod.rs` — 2,282 lines

2. **Duplicated utilities:** `is_executable()`, `blend()`, `wrapper_path()` implemented in multiple files

3. **Excessive clippy suppressions:** ~60 `#![allow(clippy::...)]` in kaku-gui, 14 in kaku (inherited from WezTerm)

4. **Platform pruning incomplete:** Wayland, Windows, ConPTY references remain in macOS-focused fork

5. **Blocking HTTP in tool context:** `reqwest::blocking` used in ai_client.rs; runs on dedicated threads but prevents mid-request cancellation

---

## 7. Known Issues & Technical Debt

### Explicit TODO/FIXME Items

| Location | Type | Description |
|----------|------|-------------|
| `kaku-gui/src/frontend.rs:523` | FIXME | notification.focus should focus pane on click |
| `kaku-gui/src/frontend.rs:1230` | TODO | AppKit menubar cannot be rebuilt on config reload |
| `kaku-gui/src/commands.rs:515,545` | FIXME | domain_label should replace domain_name |
| `kaku-gui/src/commands.rs:1349,1360` | FIXME | PaneSelect SwapWithActive modes need key assignments |
| `kaku-gui/src/scripting/guiwin.rs:132` | FIXME | Only partial state exposed in Lua API |
| `kaku-gui/src/termwindow/render/pane.rs:385` | TODO | Only single scrollbar in single position |
| `kaku-gui/src/termwindow/render/pane.rs:749` | TODO | No visual "jump to prior prompt" indicator |
| `kaku-gui/src/termwindow/render/fancy_tab_bar.rs:337` | FIXME | macOS traffic light button width hardcoded |
| `kaku-gui/src/termwindow/render/screen_line.rs:477,552` | TODO | Logical/visual mapping, pixel clipping |

### Architectural Debt

| Item | Location | Notes |
|------|----------|-------|
| Giant ai_config TUI | `kaku/src/ai_config/tui.rs` | 7.4K lines, ProviderAdapter trait prepared but unused |
| Legacy inline block sync | `kaku/src/reset.rs:40` | 148-line constant must stay in sync with shell script |
| Provider detection duplication | `kaku/src/assistant_config.rs:24` | Canonical in `kaku-gui/src/ai_client.rs`, old copy bit-rotted |
| clap hidden alias leak | `kaku/src/main.rs:119` | Known clap issue #1335 |
| `unicode_names.rs` compile cost | `kaku-gui/src/unicode_names.rs` | 140K lines auto-generated, dominates compile time |
| No shell command sandbox | `kaku-gui/src/ai_tools/shell.rs` | Arbitrary commands with full user environment |
| kaku-relay no auth | `crates/kaku-relay/src/main.rs` | URL-path tokens only, no rate limiting |
| Term default duality | config vs term | "xterm-256color" in config, "kaku" in terminalstate |

---

## 8. Key File Quick Reference

### Entry Points
```
kaku/src/main.rs                 — CLI entry, clap parsing, main menu
kaku-gui/src/main.rs             — GUI entry, single-instance handoff
kaku-gui/src/bin/k.rs            — AI chat CLI entry
crates/kaku-relay/src/main.rs    — WebSocket relay server
```

### Core Rendering
```
kaku-gui/src/frontend.rs          — Event loop coordinator
kaku-gui/src/termwindow/mod.rs    — Window: rendering, input, tabs, panes
kaku-gui/src/renderstate.rs       — GPU state management
kaku-gui/src/glyphcache.rs        — Font glyph atlas caching
```

### AI System
```
kaku-gui/src/ai_client.rs         — HTTP client, config, SSE streaming
kaku-gui/src/ai_chat_engine/mod.rs     — Agent loop (25 rounds)
kaku-gui/src/ai_chat_engine/approval.rs — Shell command safety
kaku-gui/src/ai_chat_engine/compact.rs  — Output truncation/spill
kaku-gui/src/ai_tools/            — Tool framework (7 submodules)
kaku-gui/src/overlay/ai_chat/     — Chat TUI overlay (Cmd+L)
kaku-gui/src/soul.rs              — User identity system
kaku-gui/src/ai_conversations.rs  — Persistent conversation store
kaku-gui/src/ai_auth.rs           — Copilot/Codex OAuth
```

### Terminal Engine
```
mux/src/lib.rs              — Mux singleton, pane lifecycle
mux/src/tab.rs              — Split tree, pane navigation
mux/src/pane.rs             — Pane trait, PTY output routing
term/src/terminalstate/mod.rs — VT100/xterm state machine
term/src/screen.rs           — Screen buffer, scrollback
termwiz/src/lib.rs            — Terminal primitives
config/src/config.rs          — ~200 config fields
config/src/lib.rs             — Lua context, file watcher
```

### AI Config TUI
```
kaku/src/ai_config/tui.rs   — 9-provider AI config TUI (7.4K lines)
kaku/src/ai_config/tui/ui.rs — Config TUI rendering
kaku/src/assistant_config.rs — assistant.toml management
```

### Infrastructure
```
kaku/src/doctor.rs          — Health diagnostics
kaku/src/update.rs          — Self-updater
kaku/src/init.rs            — Shell integration bootstrap
kaku/src/reset.rs           — State cleanup
window/src/os/macos/        — macOS native window impl
scripts/build.sh            — 7-stage macOS build
scripts/release.sh          — Full release pipeline
Makefile                    — Build targets
```

### Tests (Key Locations)
```
kaku-gui/src/ai_chat_engine/approval.rs  — 30+ shell safety tests
kaku-gui/src/ai_tools/mod.rs             — 10+ integration tests
kaku-gui/src/ai_conversations.rs         — Store eviction/round-trip tests
kaku-gui/src/overlay/ai_chat/tests.rs    — Chat overlay tests
kaku/src/doctor.rs                       — 12 diagnostic tests
kaku/src/kaku_theme.rs                   — 9 theme resolution tests
kaku/src/reset.rs                        — 4 cleanup tests
kaku/src/utils.rs                        — 7 utility tests
term/src/test/                           — Terminal emulation conformance tests
```
