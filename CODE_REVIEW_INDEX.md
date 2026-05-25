# Kaku Code Review Index

> Auto-generated code review and architecture map for developer onboarding and reference.
> Based on codebase snapshot at v0.12.2 (branch: main).

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture & Dependency Graph](#2-architecture--dependency-graph)
3. [Workspace Layout](#3-workspace-layout)
4. [Module Deep Dives](#4-module-deep-dives)
   - [4.1 kaku (CLI Binary)](#41-kaku-cli-binary)
   - [4.2 kaku-gui (GUI Application)](#42-kaku-gui-gui-application)
   - [4.3 Core Crates](#43-core-crates)
   - [4.4 Utility Crates](#44-utility-crates)
5. [Build System](#5-build-system)
6. [Code Quality Summary](#6-code-quality-summary)
7. [Known Issues & Technical Debt](#7-known-issues--technical-debt)
8. [Key File Quick Reference](#8-key-file-quick-reference)

---

## 1. Project Overview

**Kaku** is a GPU-accelerated terminal emulator forked from WezTerm, rebranded by author Tw93. A fast, polished macOS terminal focused on performance and usability.

| Attribute | Value |
|-----------|-------|
| Language | Rust (edition 2018/2021) |
| Platform | macOS only (current focus) |
| Version | 0.12.2 (latest tag) |
| License | MIT (inherited from WezTerm) |
| Rendering | OpenGL + WebGPU (Metal backend) |
| Config | Lua 5.4 via mlua |

### Binaries

| Binary | Crate | Purpose |
|--------|-------|---------|
| `kaku` | `kaku/` | CLI: launcher, shell integration, config TUI, doctor, reset |
| `kaku-gui` | `kaku-gui/` | GUI terminal emulator with GPU rendering |

---

## 2. Architecture & Dependency Graph

```
                      ┌──────────────────┐
                      │    kaku (CLI)    │
                      └────────┬─────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
                    ▼                     ▼
              wezterm-gui-          kaku-remote
              subcommands          (feature-gated)
                    │
                    ▼
            ┌───────────────┐
            │   kaku-gui    │
            │  (GUI app)    │
            └───────┬───────┘
                    │
         ┌──────────┼──────────┐
         │          │          │
         ▼          ▼          ▼
      window      mux      config
    (OS window) (tab/pane) (Lua cfg)
         │          │
         │    ┌─────┼─────┐
         │    │     │     │
         │    ▼     ▼     ▼
         │  term  wezterm-ssh
         │ (state) (SSH)
         │    │
         │    ▼
         └─► termwiz
           (terminal primitives)
```

**Dependency rule:** arrows point from "depends on" to "depended upon." `termwiz` and `config` are the foundational layers.

---

## 3. Workspace Layout

```
Kaku/
├── kaku/                    # CLI binary (~5.6K LoC)
│   └── src/
│       ├── main.rs          # Entry, clap CLI, main menu TUI (557 lines)
│       ├── config_tui/      # Lua config file editor TUI (mod.rs + ui.rs)
│       ├── config_cmd.rs    # Config subcommand handler
│       ├── doctor.rs        # Diagnostic health checks (1,499 lines)
│       ├── init.rs          # Shell integration bootstrap
│       ├── reset.rs         # Remove all Kaku managed state (839 lines)
│       ├── shell.rs         # Shell type detection
│       ├── kaku_theme.rs    # Theme resolution (588 lines)
│       ├── tui_core/        # Shared TUI primitives (theme)
│       ├── tui_splash.rs    # Startup splash screen
│       └── utils.rs         # Utility functions
├── kaku-gui/                # GUI application
│   └── src/
│       ├── main.rs          # GUI entry, single-instance handoff (1,045 lines)
│       ├── lib.rs           # Shared lib (exports thread_util only)
│       ├── frontend.rs      # Event loop coordinator (1,139 lines)
│       ├── termwindow/      # Core rendering, input, tabs, panes
│       │   ├── mod.rs       # Window lifecycle, tab mgmt, rendering (5,961 lines)
│       │   ├── keyevent.rs  # Keyboard input routing (1,323 lines)
│       │   ├── mouseevent.rs # Mouse input handling (1,898 lines)
│       │   ├── resize.rs    # Window/pane resize logic (1,567 lines)
│       │   ├── box_model.rs # Layout box model (1,254 lines)
│       │   ├── background.rs # Background image/color (601 lines)
│       │   ├── tab_rename.rs # Tab rename overlay (924 lines)
│       │   ├── palette.rs   # Color palette (1,238 lines)
│       │   ├── paneselect.rs # Pane selection (295 lines)
│       │   ├── clipboard.rs # Clipboard integration (204 lines)
│       │   ├── webgpu.rs    # WebGPU backend (556 lines)
│       │   ├── modal.rs     # Modal dialog support
│       │   ├── prevcursor.rs # Cursor state tracking
│       │   ├── spawn.rs     # Process spawning
│       │   └── render/      # Rendering submodules
│       │       ├── mod.rs       # Render orchestrator (1,104 lines)
│       │       ├── pane.rs      # Pane rendering (874 lines)
│       │       ├── fancy_tab_bar.rs # Tab bar (702 lines)
│       │       ├── screen_line.rs   # Screen line drawing (984 lines)
│       │       ├── draw.rs        # Draw primitives
│       │       ├── borders.rs     # Pane borders
│       │       ├── corners.rs     # Corner rendering
│       │       ├── split.rs       # Split indicators
│       │       ├── tab_bar.rs     # Tab bar fallback
│       │       ├── paint.rs       # Paint operations
│       │       └── window_buttons.rs # Window control buttons
│       ├── overlay/         # Overlay system (6 overlay types)
│       │   ├── copy.rs      # Copy mode (2,158 lines)
│       │   ├── quickselect.rs # Quick selection (980 lines)
│       │   ├── launcher.rs  # Activity launcher (803 lines)
│       │   ├── confirm.rs   # Confirmation dialogs (279 lines)
│       │   ├── confirm_close_pane.rs # Close confirmations
│       │   └── prompt.rs    # Input prompts
│       ├── commands.rs      # Key bindings, menu bar (2,702 lines)
│       ├── inputmap.rs      # Input mapping (548 lines)
│       ├── renderstate.rs   # GPU state management (788 lines)
│       ├── glyphcache.rs    # Font glyph atlas caching (1,398 lines)
│       ├── customglyph.rs   # Custom glyph rendering (6,049 lines)
│       ├── tabbar.rs        # Tab bar widget (1,307 lines)
│       ├── shapecache.rs    # Shape caching (768 lines)
│       ├── selection.rs     # Text selection logic (360 lines)
│       ├── session_restore.rs # Session persistence (556 lines)
│       ├── quad.rs          # Quad geometry (403 lines)
│       ├── stats.rs         # Rendering stats (438 lines)
│       ├── scrollbar.rs     # Scrollbar widget (207 lines)
│       ├── spawn.rs         # Process spawning (168 lines)
│       ├── colorease.rs     # Color interpolation (142 lines)
│       ├── download.rs      # Download handling (72 lines)
│       ├── utilsprites.rs   # Sprite utilities (249 lines)
│       ├── uniforms.rs      # Shader uniforms (57 lines)
│       ├── resize_increment_calculator.rs (29 lines)
│       ├── startup_trace.rs # Startup tracing (23 lines)
│       ├── thread_util.rs   # Thread utilities (25 lines)
│       └── scripting/       # Lua scripting bridge
├── mux/                     # Multiplexer: tab/pane/window/PTY mgmt (~9.3K LoC)
├── term/                    # Terminal state machine: VT100/xterm emulation (~4.3K LoC)
├── termwiz/                 # Terminal primitives: parsing, caps, widgets
├── config/                  # Lua 5.4 config system (~4.3K LoC)
│   └── derive/              # ConfigMeta procedural macro
├── window/                  # Cross-platform window abstraction (macOS focus)
├── crates/
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

**Purpose:** Thin launcher that delegates to `kaku-gui` for the GUI, plus management CLI commands.

| Command | File | Lines | Responsibility |
|---------|------|-------|----------------|
| `(default)` | `main.rs` | 557 | Interactive main menu TUI when stdin is a tty |
| `kaku start` | `main.rs` | — | Delegates to `kaku-gui` binary |
| `kaku config` | `config_tui/mod.rs` | 2,253 | Config file editor TUI |
| `kaku init` | `init.rs` | 246 | Shell integration (zsh/fish on macOS) |
| `kaku doctor` | `doctor.rs` | 1,499 | Health diagnostics with `--fix` auto-fix |
| `kaku reset` | `reset.rs` | 839 | Remove all Kaku managed state |
| `kaku set-working-directory` | `main.rs` | — | OSC 7 escape sequence handler |

**Main menu** presents 4 options when stdin is a tty:
1. Config — Lua config editor
2. Init — Shell integration setup
3. Doctor — Runtime diagnostics
4. Reset — Clean state removal

**Key observations:**
- 14 crate-level clippy lint suppressions in `main.rs` (inherited from WezTerm)
- `doctor.rs` is the largest file at 1,499 lines — comprehensive health checks
- `reset.rs` at 839 lines includes inline shell integration scripts
- Duplicated utility: `blend()` in both `kaku_theme.rs` and `tui_core/theme.rs`
- Known clap issue #1335 with hidden aliases

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
- **Copy Mode** (`copy.rs`, 2,158 lines) — Vi-like copy mode with search, selection
- **Launcher** (`launcher.rs`, 803 lines) — Activity launcher, tab/spawn menu
- **QuickSelect** (`quickselect.rs`, 980 lines) — Fuzzy text selection
- **Confirm** (`confirm.rs`, 279 lines) — Confirmation dialogs
- **ConfirmClosePane** (`confirm_close_pane.rs`, 103 lines) — Pane close confirmation
- **Prompt** (`prompt.rs`, 105 lines) — Input prompts

**Key modules (by size):**

| Module | Lines | Responsibility |
|--------|-------|----------------|
| `termwindow/mod.rs` | 5,961 | Core window: rendering, input, tabs, panes, splits |
| `customglyph.rs` | 6,049 | Custom glyph rendering (powerline, nerd font fallbacks) |
| `commands.rs` | 2,702 | Key bindings, menu bar, command palette |
| `termwindow/mouseevent.rs` | 1,898 | Mouse input handling, selection, scrolling |
| `termwindow/resize.rs` | 1,567 | Window/pane resize logic |
| `termwindow/box_model.rs` | 1,254 | Layout box model computation |
| `tabbar.rs` | 1,307 | Tab bar widget rendering |
| `glyphcache.rs` | 1,398 | Font glyph atlas caching |
| `frontend.rs` | 1,139 | Event loop coordinator |
| `termwindow/palette.rs` | 1,238 | Color palette management |
| `termwindow/keyevent.rs` | 1,323 | Keyboard input routing |
| `termwindow/tab_rename.rs` | 924 | Tab rename overlay |
| `shapecache.rs` | 768 | Shape caching for rendering |
| `renderstate.rs` | 788 | GPU state management |
| `termwindow/webgpu.rs` | 556 | WebGPU backend |
| `session_restore.rs` | 556 | Session persistence across restarts |
| `inputmap.rs` | 548 | Input mapping table |
| `quad.rs` | 403 | Quad geometry for rendering |
| `termwindow/background.rs` | 601 | Background image/color management |

**TermWindow submodules** (19 files total):
The core `termwindow/` module handles rendering, input (keyboard/mouse), tab management, pane splitting, clipboard, background, resize, and WebGPU backend.

---

### 4.3 Core Crates

#### mux (Multiplexer) — ~9,300 LoC

**Purpose:** Tab/pane/window management layer — the "window manager" of the terminal.

| File | Lines | Key Content |
|------|-------|-------------|
| `lib.rs` | 1,924 | `Mux` singleton, `MuxNotification` (18 variants), pane lifecycle |
| `tab.rs` | 3,136 | Binary tree split management, pane navigation, codec serialization |
| `pane.rs` | ~1,179 | `Pane` trait (~50 methods), PTY output routing |
| `domain.rs` | ~813 | `Domain` trait, `LocalDomain` PTY management |
| `window.rs` | ~269 | Window workspace, tab list, position |

**Notable:** PTY output uses 256KB socketpair buffers with 8ms throttle. Synchronized output (BSU mode 2026) with 1MB cap.

#### term (wezterm-term) — ~4,300 LoC

**Purpose:** Terminal state machine — VT100/xterm escape sequence processing.

| File | Lines | Key Content |
|------|-------|-------------|
| `terminalstate/mod.rs` | 2,936 | Escape dispatch, cursor control, DEC modes, SGR, scroll regions |
| `screen.rs` | 1,235 | `VecDeque<Line>` with scrollback, line rewrapping, stable row indexing |
| `lib.rs` | 134 | Type aliases (`PhysRowIndex` vs `VisibleRowIndex` — different sizes to prevent mixing) |

**Notable:** Type-safe row indices (usize/i64/i32/isize) prevent arithmetic bugs. Screen recycles lines instead of alloc+free. Bundled terminfo via `include_bytes!`.

#### config — ~4,276 LoC

**Purpose:** Lua 5.4 configuration with filesystem watching and live reload.

| File | Lines | Key Content |
|------|-------|-------------|
| `config.rs` | 2,646 | ~200+ fields with `ConfigMeta` derive, `RemoteConfig`, font rules |
| `lib.rs` | 1,630 | Lua context management, bytecode cache (`KLBC` magic), file watcher |

**Notable:** Bytecode cache with source hash validation. File watcher watches parent dirs (not files) for atomic rename detection. Main-thread-only Lua execution.

#### window — ~515 LoC (header)

**Purpose:** Cross-platform window abstraction (macOS focus).

Key trait: `WindowOps` — 30+ methods for lifecycle, input, rendering, clipboard. macOS implementation wraps cocoa/objc with AppKit integration.

#### termwiz

**Purpose:** Foundational terminal primitives — escape parsing, capability probing, input events, surface/cell modeling. Nearly every crate depends on it.

---

### 4.4 Utility Crates

Inherited from WezTerm (20+ crates). Key ones:

| Crate | Purpose |
|-------|---------|
| `wezterm-escape-parser` | Escape sequence parsing (CSI/OSC/APC/DCS) |
| `wezterm-font` | Font loading (FreeType/HarfBuzz), rasterization, shaping |
| `wezterm-ssh` | SSH client (libssh2-based) with SFTP support |
| `wezterm-surface` | Terminal surface model (lines, cells, changes) |
| `wezterm-cell` | Cell attributes, color, image cell |
| `wezterm-dynamic` | Dynamic value types with derive macros |
| `wezterm-client` | Mux client for remote terminal sessions |
| `wezterm-input-types` | Input event type definitions (308 symbols) |
| `wezterm-gui-subcommands` | Shared CLI subcommand definitions |
| `wezterm-mux-server-impl` | Mux server implementation |
| `bidi` | Unicode bidirectional algorithm |
| `vtparse` | VT parser state machine tables |
| `promise` | Future/promise with spawn utilities |
| `lfucache` / `frecency` | Cache eviction strategies |
| `codec` | Terminal encoding/decoding |
| `pty` | PTY management (unix) |
| `async_ossl` | Async OpenSSL wrappers |
| `base91` | Base91 encoding |
| `rangeset` | Range set data structure |
| `ratelim` | Rate limiting |
| `procinfo` | Process information (macOS) |

---

## 5. Build System

### Toolchain
- **Rust:** stable, nightly rustfmt
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
2. **Comprehensive tests** — theme resolution, doctor checks, terminal conformance tests
3. **Defensive coding** — timeout-protected subprocesses, graceful fallbacks, retry with exponential backoff
4. **Sophisticated release engineering** — pre-flight checks, resume support, notarization retry, Homebrew tap verification
5. **Clean architecture** — well-separated concerns between mux/term/config/window layers
6. **Rich overlay system** — modular overlay framework for Copy/Launcher/QuickSelect/Confirm/Prompt

### Areas for Improvement

1. **Large files needing decomposition:**
   - `kaku-gui/src/customglyph.rs` — 6,049 lines (auto-generated glyph data)
   - `kaku-gui/src/termwindow/mod.rs` — 5,961 lines (core window monolith)
   - `kaku-gui/src/commands.rs` — 2,702 lines
   - `mux/src/tab.rs` — 3,136 lines
   - `config/src/config.rs` — 2,646 lines
   - `kaku-gui/src/termwindow/mouseevent.rs` — 1,898 lines

2. **Duplicated utilities:** `blend()` in both `kaku_theme.rs` and `tui_core/theme.rs`

3. **Excessive clippy suppressions:** ~30+ `#![allow(clippy::...)]` in kaku-gui, 14 in kaku (inherited from WezTerm)

4. **Platform pruning incomplete:** Wayland, Windows, ConPTY references remain in macOS-focused fork

---

## 7. Known Issues & Technical Debt

### Explicit TODO/FIXME Items

| Location | Type | Description |
|----------|------|-------------|
| `kaku-gui/src/frontend.rs` | FIXME | notification.focus should focus pane on click |
| `kaku-gui/src/commands.rs` | FIXME | domain_label should replace domain_name |
| `kaku-gui/src/commands.rs` | FIXME | PaneSelect SwapWithActive modes need key assignments |
| `kaku-gui/src/scripting/guiwin.rs` | FIXME | Only partial state exposed in Lua API |
| `kaku-gui/src/termwindow/render/pane.rs` | TODO | Only single scrollbar in single position |
| `kaku-gui/src/termwindow/render/pane.rs` | TODO | No visual "jump to prior prompt" indicator |
| `kaku-gui/src/termwindow/render/fancy_tab_bar.rs` | FIXME | macOS traffic light button width hardcoded |
| `kaku-gui/src/termwindow/render/screen_line.rs` | TODO | Logical/visual mapping, pixel clipping |

### Architectural Debt

| Item | Location | Notes |
|------|----------|-------|
| TermWindow monolith | `kaku-gui/src/termwindow/mod.rs` | 5,961 lines, handles too many concerns |
| Legacy inline block sync | `kaku/src/reset.rs` | Shell integration scripts must stay in sync |
| clap hidden alias leak | `kaku/src/main.rs` | Known clap issue #1335 |
| Term default duality | config vs term | "xterm-256color" in config, "kaku" in terminalstate |
| lib.rs underutilized | `kaku-gui/src/lib.rs` | Only exports `thread_util`, was originally for shared AI modules |

---

## 8. Key File Quick Reference

### Entry Points
```
kaku/src/main.rs                 — CLI entry, clap parsing, main menu
kaku-gui/src/main.rs             — GUI entry, single-instance handoff
```

### Core Rendering
```
kaku-gui/src/frontend.rs               — Event loop coordinator
kaku-gui/src/termwindow/mod.rs         — Window: rendering, input, tabs, panes
kaku-gui/src/renderstate.rs            — GPU state management
kaku-gui/src/glyphcache.rs             — Font glyph atlas caching
kaku-gui/src/customglyph.rs            — Custom glyph rendering
kaku-gui/src/shapecache.rs             — Shape caching
kaku-gui/src/quad.rs                   — Quad geometry
kaku-gui/src/uniforms.rs               — Shader uniforms
```

### Input & Interaction
```
kaku-gui/src/commands.rs               — Key bindings, menu bar
kaku-gui/src/inputmap.rs               — Input mapping
kaku-gui/src/termwindow/keyevent.rs    — Keyboard routing
kaku-gui/src/termwindow/mouseevent.rs  — Mouse handling
kaku-gui/src/selection.rs              — Text selection
kaku-gui/src/termwindow/clipboard.rs   — Clipboard integration
```

### Overlays
```
kaku-gui/src/overlay/mod.rs            — Overlay framework
kaku-gui/src/overlay/copy.rs           — Copy mode (vi-like)
kaku-gui/src/overlay/launcher.rs       — Activity launcher
kaku-gui/src/overlay/quickselect.rs    — Quick selection
kaku-gui/src/overlay/confirm.rs        — Confirmation dialogs
kaku-gui/src/overlay/prompt.rs         — Input prompts
```

### Terminal Engine
```
mux/src/lib.rs                          — Mux singleton, pane lifecycle
mux/src/tab.rs                          — Split tree, pane navigation
mux/src/pane.rs                         — Pane trait, PTY output routing
term/src/terminalstate/mod.rs           — VT100/xterm state machine
term/src/screen.rs                      — Screen buffer, scrollback
termwiz/src/lib.rs                      — Terminal primitives
config/src/config.rs                    — ~200 config fields
config/src/lib.rs                       — Lua context, file watcher
```

### Window & Layout
```
kaku-gui/src/termwindow/resize.rs       — Resize logic
kaku-gui/src/termwindow/box_model.rs    — Layout box model
kaku-gui/src/termwindow/background.rs   — Background management
kaku-gui/src/termwindow/webgpu.rs       — WebGPU backend
kaku-gui/src/tabbar.rs                  — Tab bar widget
kaku-gui/src/termwindow/tab_rename.rs   — Tab rename
kaku-gui/src/scrollbar.rs               — Scrollbar widget
```

### Infrastructure
```
kaku/src/doctor.rs          — Health diagnostics
kaku/src/init.rs            — Shell integration bootstrap
kaku/src/reset.rs           — State cleanup
kaku/src/kaku_theme.rs      — Theme resolution
kaku/src/config_tui/mod.rs  — Config editor TUI
window/src/os/macos/        — macOS native window impl
scripts/build.sh            — 7-stage macOS build
scripts/release.sh          — Full release pipeline
Makefile                    — Build targets
```

### Tests (Key Locations)
```
kaku/src/doctor.rs                       — Diagnostic tests
kaku/src/kaku_theme.rs                   — Theme resolution tests
kaku/src/reset.rs                        — Cleanup tests
kaku/src/utils.rs                        — Utility tests
term/src/test/                           — Terminal emulation conformance tests
crates/wezterm-dynamic/tests/            — Dynamic type tests
crates/wezterm-ssh/tests/                — SSH client tests
assets/shell-integration/tests/          — Shell integration tests
```
