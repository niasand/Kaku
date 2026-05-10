# Kaku 功能清单

> 供产品决策使用。按模块分类，标注 Core（终端核心）/ Extended（扩展）/ AI / Platform（平台特定）。
> 决策列：`保留` / `移除` / `待定`，请直接编辑此文件标记。

---

## 一、CLI 命令 (`kaku`)

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 1 | `kaku start` | 启动 GUI 终端，execvp 委托给 kaku-gui | Core | 保留|
| 2 | `kaku` (无参数) | 交互式主菜单 TUI，键盘导航选子命令 | Extended |保留 |
| 3 | `kaku config` | Lua 配置文件编辑 TUI（主题/字体/快捷键等 18 项设置） | Core | 保留|
| 4 | `kaku init` | Shell 集成初始化（zsh/fish） | Core | 保留|
| 5 | `kaku init --update-only` | 静默刷新集成文件，供 doctor 调用 | Core | 保留|
| 6 | `kaku doctor` | 健康诊断（13 项检查），`--fix` 自动修复 | Core | 保留|
| 7 | `kaku update` | 自更新（GitHub Release / Homebrew），SHA256 校验 | Extended | 移除|
| 8 | `kaku reset` | 卸载清理（移除所有 Kaku 托管状态） | Extended | 保留|
| 9 | `kaku ai` | AI 工具配置 TUI（9 个 provider） | AI | 移除|
| 10 | `kaku chat` | exec 到 `k` 二进制，启动 AI 对话 | AI | 移除|
| 11 | `kaku cli` | 实验性 mux server 交互（19 个子命令，隐藏） | Extended |移除 |
| 12 | `kaku remote` | 显示 iOS 连接 QR 码（feature-gated） | Extended | 移除|
| 13 | `kaku shell-completion` | 生成 shell 补全脚本 | Extended | 保留|
| 14 | `kaku set-working-directory` | 发射 OSC 7 转义序列 | Core | 保留|
| 15 | 全局 flag | `--skip-config`, `--config-file`, `--config k=v` | Core | 保留|

---

## 二、AI 系统

### 2.1 AI 对话核心

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 16 | AI Chat Overlay (Cmd+L) | 全屏 AI 对话 TUI | AI |移除 |
| 17 | SSE 流式输出 | 实时逐 token 显示 | AI | 移除|
| 18 | Agent 循环 | 最多 25 轮工具调用 | AI |移除 |
| 19 | 工具调用审批 | 危险操作需用户确认（Enter/Esc） | AI | 移除|
| 20 | 对话持久化 | 保存/恢复对话，上限 100 条，LRU 淘汰 | AI | 移除|
| 21 | Markdown 渲染 | AI 回复渲染为格式化 markdown | AI | 移除|
| 22 | 语法高亮 | 代码块语法着色 | AI | 移除|
| 23 | 多行输入 | Shift+Enter 换行 | AI | 移除|
| 24 | IME 支持 | CJK 输入法合成文本支持 | AI |移除 |
| 25 | Shift+Tab 切换模型 | 在可用模型间循环 | AI |移除 |
| 26 | Chat/Fast 双模型 | 对话模型和后台任务用不同模型 | AI | 移除|
| 27 | 微压缩 (micro-compact) | 截断过长工具输出，溢出到临时文件 | AI | 移除|
| 28 | 记忆提取 | 每轮后用独立模型提取记忆写入 MEMORY.md | AI | 移除|
| 29 | 对话标题生成 | 后台自动生成对话标题 | AI | 移除|
| 30 | 终端上下文注入 | 最近 20 行终端输出 + 失败命令注入 prompt | AI | 移除|

### 2.2 AI 对话 Slash 命令

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 31 | `/new` | 新建对话 | AI |移除 |
| 32 | `/resume` | 恢复历史对话 | AI |移除 |
| 33 | `/clear` | 清空当前对话 | AI |移除 |
| 34 | `/export` | 复制对话到剪贴板 | AI |移除 |
| 35 | `/model` | 查看/切换模型 | AI |移除 |
| 36 | `/config` | 显示当前 AI 配置 | AI |移除 |
| 37 | `/memory` | 显示记忆文件路径 | AI |移除 |
| 38 | `/status` | 显示会话状态 | AI |移除 |
| 39 | `/btw` | 临时提问（不存历史） | AI |移除 |
| 40 | `/check` | 代码审查（检查 diff 风险） | AI |移除 |
| 41 | `/hunt` | 根因诊断 | AI |移除 |
| 42 | `/think` | 功能规划 | AI |移除 |
| 43 | `/read` | 网页/文档阅读 | AI |移除 |
| 44 | `/write` | 文案润色 | AI |移除 |
| 45 | `/learn` | 研究解释 | AI |移除 |
| 46 | `/design` | UI 设计评审 | AI |移除 |
| 47 | `/health` | AI 配置审计 | AI |移除 |

### 2.3 AI 工具集

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 48 | `fs_read` | 读文件（可选行范围） | AI |移除 |
| 49 | `fs_list` | 列目录 | AI |移除 |
| 50 | `fs_write` | 写文件（自动创建父目录） | AI |移除 |
| 51 | `fs_patch` | 查找替换文本 | AI |移除 |
| 52 | `fs_mkdir` | 创建目录（递归） | AI |移除 |
| 53 | `fs_delete` | 删除文件/目录 | AI |移除 |
| 54 | `shell_exec` | 执行 shell 命令（60s 超时） | AI |移除 |
| 55 | `shell_bg` / `shell_poll` | 后台进程 + 轮询 | AI |移除 |
| 56 | `pwd` | 获取当前工作目录 | AI |移除 |
| 57 | `grep_search` | 正则搜索（rg/grep fallback） | AI |移除 |
| 58 | `symbol_search` | 语言感知的符号搜索 | AI |移除 |
| 59 | `web_search` | 网页搜索（Brave/PipeLLM/Tavily） | AI |移除 |
| 60 | `web_fetch` | 网页抓取为 Markdown | AI |移除 |
| 61 | `http_request` | 通用 HTTP 客户端 | AI |移除 |
| 62 | `read_url` | 优化的网页阅读（JS-heavy 支持） | AI |移除 |
| 63 | `project_summary` | 项目摘要（语言/构建检测） | AI |移除 |
| 64 | `file_tree` | 目录树（深度限制，过滤噪音） | AI |移除 |
| 65 | `memory_read` | 读持久化记忆文件 | AI |移除 |
| 66 | `soul_read` | 读用户身份文件 | AI |移除 |
| 67 | 路径安全守卫 | 阻止访问 ~/.ssh、~/.aws 等敏感路径 | AI |移除 |
| 68 | Shell 命令安全分析 | allowlist + 完整 shell parser 分类命令风险 | AI |移除 |
| 69 | 输出预算系统 | 按 tool 分配字节上限，三级（brief/default/full） | AI |移除 |

### 2.4 AI 对话附件

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 70 | `@cwd` | 附加当前目录结构 + git status + 关键文件预览 | AI |移除 |
| 71 | `@tab` | 附加当前 pane 终端输出快照 | AI |移除 |
| 72 | `@selection` | 附加当前选中文本 | AI |移除 |

### 2.5 Soul / 身份系统

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 73 | SOUL.md | 用户身份文件（注入每个 system prompt，2KB 上限） | AI |移除 |
| 74 | STYLE.md | 偏好回复风格 | AI |移除 |
| 75 | SKILL.md | 工作模式描述 | AI |移除 |
| 76 | MEMORY.md | 跨会话持久记忆（4KB 上限） | AI |移除 |
| 77 | Onboarding Bootstrap | 首次使用时 AI 自动拆分回答生成身份文件 | AI |移除 |

### 2.6 AI Provider 集成（`kaku ai` TUI 管理）

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 78 | Kaku Assistant | 内置 AI，API key + base URL + 模型选择 | AI |移除 |
| 79 | Claude Code | 读 ~/.claude/settings.json，显示订阅/配额 | AI |移除 |
| 80 | Codex | 读 ~/.codex/ 配置，显示推理等级/双窗口配额 | AI |移除 |
| 81 | Kimi Code | OAuth 认证，显示模型/双窗口配额 | AI |移除 |
| 82 | Antigravity (Codeium) | protobuf-over-HTTP 通信，配额窗口显示 | AI |移除 |
| 83 | Gemini CLI | API key / OAuth / ADC 多认证，配额显示 | AI |移除 |
| 84 | Copilot CLI | 月度配额 + 剩余次数显示 | AI |移除 |
| 85 | Factory Droid | 模型 + 推理等级 + 自治等级 | AI |移除 |
| 86 | OpenClaw | 认证 + 模型配置（含 legacy 路径 fallback） | AI |移除 |
| 87 | Copilot/Codex OAuth | GitHub device-code OAuth + 60s 预过期刷新 | AI |移除 |

---

## 三、GUI 核心交互

### 3.1 窗口 & 标签页

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 88 | Cmd+N 新建窗口 | | Core | 保留|
| 89 | Cmd+T 新建标签 | | Core | 保留|
| 90 | Cmd+Shift+W 关闭标签 | | Core | 保留|
| 91 | Cmd+W 关闭面板 | | Core | 保留|
| 92 | Cmd+Shift+T 重开上次关闭的标签 | 恢复 cwd | Core | 保留|
| 93 | Cmd+1..9 切换标签 | | Core | 保留|
| 94 | Cmd+Shift+[ / ] 前后切换标签 | | Core | 保留|
| 95 | Ctrl+Tab / Ctrl+Shift+Tab 切换标签 | | Core | 保留|
| 96 | Cmd+Shift+O 标签导航器 | 交互式标签切换 | Core | 保留|
| 97 | Ctrl+Shift+PageUp/Down 移动标签 | | Core | 保留|
| 98 | Fancy Tab Bar | 富像素标签栏（图标/标题/关闭按钮） | Core | 保留|
| 99 | Tab Bar 位置 | 顶部或底部（默认底部） | Core | 保留|
| 100 | 标签索引显示 | 可配置显示/隐藏 | Extended | 保留|
| 101 | 新建标签按钮 | 可配置显示/隐藏 | Extended | 保留|
| 102 | 仅单标签时隐藏 Tab Bar | 可配置 | Extended | 保留|
| 103 | Bell 指示器 | 未聚焦标签显示 bell 点 | Core | 保留|

### 3.2 面板分割

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 104 | Cmd+D 左右分割 | | Core | 保留|
| 105 | Cmd+Shift+D 上下分割 | | Core | 保留|
| 106 | Cmd+Alt+方向键 切换面板 | | Core | 保留|
| 107 | Cmd+Ctrl+方向键 调整分割 | | Core | 保留|
| 108 | Cmd+Shift+Enter 缩放面板 | 全屏切换 | Core | 保留|
| 109 | Cmd+Alt+P 选择面板 | 交互式 | Extended | 保留|
| 110 | 面板交换 (Swap Pane) | 与活跃面板交换 | Extended | 保留|
| 111 | 面板移到新标签 | Cmd+Alt+Shift+T | Extended | 保留|
| 112 | 面板移到新窗口 | Cmd+Alt+Shift+N | Extended | 保留|
| 113 | 面板旋转 | 顺/逆时针旋转 | Extended |保留 |
| 114 | 切换分割方向 | Cmd+Shift+S | Extended | 保留|
| 115 | 按鼠标聚焦面板 | 可配置 pane_focus_follows_mouse | Extended | 保留|
| 116 | 非活跃面板 HSB 变暗 | 可配置色相/饱和度/亮度偏移 | Extended | 保留|

### 3.3 输入 & 剪贴板

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 117 | Cmd+C 复制 | | Core | 保留|
| 118 | Cmd+V 粘贴 | | Core | 保留|
| 119 | 选中即复制 (copy_on_select) | 默认开启 | Core | 保留|
| 120 | Cmd+K 清空 scrollback | | Core | 保留|
| 121 | Cmd+Z Shell Undo | 转发 Ctrl+_ 到 shell | Extended | 保留|
| 122 | 输入广播 | Cmd+Alt+I (当前标签) / Cmd+Shift+I (全部面板) | Extended | 保留|
| 123 | 编码切换 | 面板字符编码设置 | Extended | 保留|

### 3.4 滚动 & 搜索

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 124 | Cmd+F 搜索 | | Core | 保留|
| 125 | Shift+PageUp/Down 翻页 | | Core | 保留|
| 126 | Cmd+Home/End 滚到顶/底 | | Core | 保留|
| 127 | 按语义提示符跳转 | ScrollToPrompt | Core | 保留|
| 128 | 滚动条 | 可配置显示/隐藏 | Core | 保留|

### 3.5 字体 & 显示

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 129 | Cmd+=/- 调整字体大小 | | Core |保留 |
| 130 | Cmd+0 重置字体大小 | | Core |保留 |
| 131 | Ctrl+Cmd+F 全屏切换 | | Core |保留 |
| 132 | Cmd+Shift+Up 窗口置顶 | Always on Top | Extended | 保留|
| 133 | Cmd+M 最小化 | | Core |保留 |
| 134 | DPI 感知 | 按显示器 DPI 缩放 | Core |保留 |
| 135 | 背景图片 | 可配置终端背景图 | Extended | |
| 136 | 窗口透明度 | window_background_opacity | Core |保留 |
| 137 | 背景模糊 | macOS 原生 blur | Core |保留 |
| 138 | 多层背景系统 | 渐变/图片/纯色叠加 | Extended | 保留|

---

## 四、Overlay 系统

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 139 | Copy Mode (Ctrl+Shift+X) | Vim 风格键盘选择 + 搜索 | Core |保留 |
| 140 | QuickSelect (Ctrl+Shift+Space) | 快速选择 URL/路径/Hash 等 | Core |保留 |
| 141 | Command Palette (Cmd+Shift+P) | 命令面板 | Core |保留 |
| 142 | Launcher | 命令/标签/工作区/域启动器 | Core |保留 |
| 143 | Selector | Lua 定义的选择器 | Extended | 移除|
| 144 | Character Select (Ctrl+Shift+U) | Emoji/Nerdfont 字符选择 | Extended | 移除|
| 145 | Confirm / Prompt | 确认和输入对话框 | Extended |保留 |
| 146 | ConfirmClosePane | 关闭面板确认 | Core |保留 |

---

## 五、配置系统

### 5.1 终端配置项

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 147 | 主题选择 | Auto (跟随 macOS) / Kaku Dark / Kaku Light | Core |保留 |
| 148 | 1001 内置配色方案 | scheme_data.rs 中的大量预设 | Core |保留 |
| 149 | 自定义配色方案 | 用户 TOML 文件 | Core |保留 |
| 150 | 字体族/大小/行高/字宽 | JetBrains Mono 默认 | Core | 保留|
| 151 | 字体规则 (font_rules) | 按单元格属性选字体（粗体/斜体等） | Core | 保留|
| 152 | 光标样式 | 闪烁条/块/下划线，闪烁速率 | Core |保留 |
| 153 | Scrollback 大小 | 默认 3500 行 | Core |保留 |
| 154 | 终端编码 | 默认 UTF-8 | Core |保留 |
| 155 | kitty 图形协议 | 默认开启 | Core |保留 |
| 156 | 双向文本 (bidi) | 可配置 | Extended | |
| 157 | 超链接规则 | URL / file:line:col 自动检测 | Core |保留 |
| 158 | Bell 设置 | 视觉/听觉 + Dock badge | Core |保留 |

### 5.2 基础设施

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 159 | Lua 5.4 配置文件 (kaku.lua) | 全功能脚本化配置 | Core | 保留|
| 160 | 字节码缓存 (KLBC) | 源码 hash 校验的预编译缓存 | Core | 保留|
| 161 | 热重载 | 文件变更自动重载配置 | Core | 保留|
| 162 | XDG 支持 | XDG_CONFIG_HOME / XDG_CONFIG_DIRS | Core | 保留|
| 163 | 运行时覆盖 | `--config k=v` 不改文件 | Core | 保留|

---

## 六、Lua 脚本 API

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 164 | `wezterm.action` | 全部 KeyAssignment 构造器 | Core |保留 |
| 165 | `wezterm.font()` / `wezterm.color` | 字体和颜色 API | Core |保留 |
| 166 | `wezterm.mux` | 工作区/窗口/标签/面板/域管理 | Core |保留 |
| 167 | `wezterm.gui` | 屏幕/外观/GPU 枚举/窗口操作 | Core |保留 |
| 168 | `wezterm.serde` | JSON/YAML/TOML 编解码 | Core |保留 |
| 169 | `wezterm.time` | 时间操作 + call_after 定时器 | Core |保留 |
| 170 | `wezterm.plugin` | Git URL 插件加载 | Core |保留 |
| 171 | `wezterm.ssh_funcs` | SSH 配置枚举 | Core |保留 |
| 172 | `wezterm.procinfo` | 进程信息查询 | Core |保留 |
| 173 | `wezterm.battery_info` | 电池状态 | Extended | |
| 174 | `wezterm.nerdfonts` | Nerd Font 图标查找 | Core |保留 |
| 175 | `wezterm.GLOBAL` | 跨配置重载的共享可变状态 | Core |保留 |
| 176 | 事件驱动 Lua API | gui-startup / gui-attached / 生命周期事件 | Core |保留 |

---

## 七、Shell 集成

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 177 | zsh 集成 | 托管 init 文件 + PATH 注入 | Core | 保留|
| 178 | fish 集成 | 托管 conf.d + init 文件 | Core | 保留|
| 179 | zsh 插件捆绑 | fast-syntax-highlighting / zsh-autosuggestions / zsh-completions / zsh-z | Core | 保留|
| 180 | Starship 提示符 | 托管 starship.toml 配置 | Core | 保留|
| 181 | 智能历史记录 | HISTSIZE=50000, 去重, 空格前缀忽略 | Core |保留 |
| 182 | 目录导航 | auto_cd / auto_pushd / pushd_ignore_dups | Core | 保留|
| 183 | Git 快捷别名 | 20+ git aliases (g/ga/gc/gco/gd/gl/gp/gst...) | Core | 保留|
| 184 | 目录快捷跳转 | `...` 到 `......` 多级 cd | Core | 保留|
| 185 | OSC 7 (工作目录跟踪) | 告知终端当前 cwd | Core | 保留|
| 186 | OSC 133 (语义区域) | 命令边界标记 | Core | 保留|
| 187 | OSC 1337 (用户变量) | Shell 到终端的元数据传递 | Core | 保留|
| 188 | tmux 集成 | 托管 tmux.conf + OSC passthrough | Extended | 保留|
| 189 | CLI 工具安装 | Homebrew 安装 starship/delta/lazygit/yazi/zoxide | Extended |保留 |
| 190 | 外部 autosuggest 兼容 | 检测 kiro-cli / Amazon Q 并避免 widget 冲突 | Extended |保留 |
| 191 | Yazi 主题同步 | kaku-dark/light yazi flavor | Extended | 保留|
| 192 | First Run 引导 | ASCII art 欢迎 + 初始化 + 可选工具安装 | Extended | 保留|

---

## 八、远程 & 连接

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 193 | SSH 域 | SSH 远程连接（libssh2 后端） | Core | 保留|
| 194 | TLS 域 | 加密 TCP 连接 | Extended | 保留|
| 195 | Unix 域 | 本地 socket 连接 | Extended | 保留|
| 196 | WSL 域 | Windows Subsystem for Linux | Extended | 保留|
| 197 | Exec 域 | 自定义进程域 | Extended | 保留|
| 198 | Serial Port 域 | 串口连接 | Extended | 保留|
| 199 | Kaku Remote (iOS Bridge) | WebSocket 服务器 + 屏幕共享 + 输入转发 | Extended | 移除|
| 200 | Kaku Relay | NAT 穿透 WebSocket 中继（独立部署） | Extended |保留 |
| 201 | 远程 AI 对话 | iOS 客户端通过 kaku-remote 发起 AI 对话 | AI |移除 |
| 202 | QR 码配置 | 终端/HTML QR 码生成 | Extended | 移除|

---

## 九、自动更新

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 203 | CLI 自更新 | `kaku update` 从 GitHub 下载 + SHA256 校验 | Extended |移除 |
| 204 | Homebrew 更新 | 自动检测 Homebrew 安装并用 brew upgrade | Extended | 移除|
| 205 | GUI 后台更新检查 | 周期性检查新版本（默认 3 小时间隔） | Extended | 移除|
| 206 | 暂存更新 (Staged Update) | 后台下载验证，重启即用 | Extended | 移除|
| 207 | macOS 通知 | 更新可用/已就绪 Toast | Extended |保留 |
| 208 | 多实例协调 | 仅主实例显示更新通知 | Extended | 保留|
| 209 | 7 天过期清理 | 超过 7 天的暂存更新自动清理 | Extended | 保留|
| 210 | 系统代理支持 | scutil 检测 macOS 代理设置 | Extended | 保留|

---

## 十、会话恢复

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 211 | 窗口快照持久化 | 保存标签/面板树 + 工作目录 | Extended | 保留|
| 212 | 500ms 防抖保存 | 避免频繁写入 | Extended |保留 |
| 213 | 原子写入 | temp + rename 防崩溃 | Extended | 保留|
| 214 | 版本门控 | 快照版本不兼容时静默忽略 | Extended |保留 |
| 215 | Cmd+Alt+Shift+T 恢复窗口 | 恢复上次保存的完整窗口 | Extended | 保留|

---

## 十一、平台特定 (macOS)

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 216 | 全局热键 (Ctrl+Alt+Cmd+K) | 系统级显示/隐藏 | Platform | 保留|
| 217 | macOS 原生菜单栏 | NSMenu/NSMenuItem 完整管理 | Platform | 保留|
| 218 | Dock Badge | Bell 事件 Dock 图标数字 | Platform |保留 |
| 219 | macOS 原生全屏 | 含可选 Notch 扩展 | Platform | 保留|
| 220 | 外观检测 | 系统 Dark/Light 模式 | Platform | 保留|
| 221 | IME 集成 | 日/中/韩输入法支持 | Platform | 保留|
| 222 | 系统代理检测 | scutil --proxy 解析 | Platform |保留 |
| 223 | 窗口位置持久化 | 0.35s 防抖保存几何信息 | Platform |保留 |
| 224 | 无障碍 (AXTextArea) | 屏幕阅读器 + 语音输入支持 | Platform | 保留|
| 225 | 剪贴板图片 | TIFF→PNG 转换 + 24h 缓存 | Platform | 保留|
| 226 | 设为默认终端 | macOS 系统设置 | Platform | 保留|
| 227 | OpenGL + MetalANGLE 后端 | CGL / EGL 自动选择 | Platform | 保留|
| 228 | WebGPU 渲染后端 | Metal backend GPU 加速 | Core | 保留|
| 229 | GPU 适配器选择 | LowPower / HighPerformance + 指定适配器 | Extended | 保留|

---

## 十二、渲染引擎

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 230 | GPU 渲染管线 | OpenGL / WebGPU 双后端 | Core |保留 |
| 231 | Glyph Atlas | GPU 纹理图集缓存 | Core |保留 |
| 232 | HarfBuzz 字形整形 | 复杂文本排版 | Core |保留 |
| 233 | 自定义块字形渲染 | Nerd Font / Emoji / 连字 | Core |保留 |
| 234 | 终端仿真器 | VT100/xterm 完整转义序列处理 | Core |保留 |
| 235 | Sixel 图形协议 | 图像显示支持 | Core |保留 |
| 236 | Kitty 图形协议 | 现代图像协议 | Core |保留 |
| 237 | 同步输出 (Mode 2026) | BSU 模式减少闪烁 | Core |保留 |
| 238 | 行重排 (Resize Rewrap) | 窗口大小改变时正确重排文本 | Core |保留 |
| 239 | 自定义字形 (customglyph.rs) | 6K 行手写字形渲染 | Core |保留 |

---

## 十三、其他 WezTerm 继承功能

| # | 功能 | 描述 | 类型 | 决策 |
|---|------|------|------|------|
| 240 | SFTP 支持 | SSH 内置 SFTP 文件操作 | Extended | 保留|
| 241 | Wayland 支持 | Linux Wayland 窗口系统 | Extended | 保留|
| 242 | X11 支持 | Linux X11 窗口系统 | Extended | 保留|
| 243 | Windows 支持 | Win32 控制台 + MSVC | Extended | 移除|
| 244 | 串口终端 | Serial port 连接 | Extended | 保留|
| 245 | 工作区 (Workspace) | 多工作区切换 | Extended |保留 |
| 246 | tmux 控制模式 | tmux -CC 集成 | Extended | 保留|
| 247 | 领导键 (Leader Key) | 嵌套键表前缀 | Extended |保留 |
| 248 | 键表 (Key Tables) | 命名键绑定组 | Extended | 保留|
| 249 | 插件系统 | Git URL 插件 require | Extended | 保留|

---

## 统计

| 类别 | 数量 |
|------|------|
| Core (终端核心) | ~70 |
| Extended (扩展) | ~60 |
| AI | ~55 |
| Platform (macOS 特定) | ~14 |
| 继承 WezTerm (非 macOS 核心) | ~10 |
| **总计** | **~249** |
