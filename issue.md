[AI-REVIEW] Large commit detected: 206 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 4663 lines added. Consider reviewing for AI Psychosis.

## [2026-05-24] Tab 标题显示格式修复

**问题**: v0.12.2 标签页未按 `~/Documents/Kaku  ⌘+1` 格式显示。实际显示 `Documents/Kaku` 且 badge 为 `⌘1`。

**修复**:
- `pane_cwd_title()`: 提取 `tilde_path()` + `format_path_segment()` 工具函数，home 目录下路径用 `~` 前缀
- Badge 格式: `⌘{idx}` → `⌘+{idx}`
- 同时修复 `tab_multi_pane_title()` 保持一致

**关键文件**:
- `kaku-gui/src/tabbar.rs:263` — `tilde_path()`, `format_path_segment()`
- `kaku-gui/src/termwindow/render/fancy_tab_bar.rs:377` — badge format

---

## [2026-05-24] Tab 拖拽 target 计算纯函数测试

**问题**: `drag_tab_target_idx` 逻辑耦合 TermWindow 运行时状态，无法单元测试。

**修复**:
- 提取 `compute_drag_target()` 纯函数，接受 prev/next UIItem 引用
- 补 11 个测试用例：midpoint 边界、首尾 tab、单 tab、不等宽、多 tab 快速扫过
- 添加 `docs/tabbar-interaction-checklist.md` 覆盖全部交互场景

**关键文件**:
- `kaku-gui/src/termwindow/mouseevent.rs:1740` — `compute_drag_target()`
- `kaku-gui/src/termwindow/mouseevent.rs:1840` — 测试模块

---

## [2026-05-23] Tab 拖拽换位不生效

**问题**: 鼠标拖拽标签页无法换位，拖拽无任何反应。

**代码分析**: Tab 拖拽功能已完整实现（`start_tab_drag` / `drag_tab` / `move_tab`），代码路径理论正确：
1. `mouse_event_tab_bar` WMEK::Press → `start_tab_drag` 设置 `tab_drag_state`
2. WMEK::Move → `drag_tab` 被调用（mouseevent.rs:568）
3. 超过 6px 阈值后触发 `move_tab` 执行换位

**排查过程**:
- macOS 事件链完整：`mouseDragged:` → `mouse_moved_or_dragged` → `mouse_common` → `mouse_event_impl`
- `mouseDownCanMoveWindow` 返回 NO，不会触发原生窗口拖拽
- `request_drag_move()` 仅在 TabBarItem::None/LeftStatus/RightStatus 调用，Tab 项不调用
- `is_window_dragging` 在 `mouse_event_tab_bar` 中为 Tab 项正确清除
- 编译通过，无编译错误

**当前状态**: 已添加 debug 日志（`TAB_DRAG:` 前缀），打 tag v0.12.1 推送远程构建。需在真机测试确认事件是否正确触达。

**关键文件**:
- `kaku-gui/src/termwindow/mouseevent.rs:108` — `start_tab_drag`
- `kaku-gui/src/termwindow/mouseevent.rs:172` — `drag_tab`
- `kaku-gui/src/termwindow/mouseevent.rs:568` — WMEK::Move 中调用 `drag_tab`
- `kaku-gui/src/termwindow/mod.rs:3524` — `move_tab`
- `kaku-gui/src/termwindow/mod.rs:3549` — `compute_tab_render_offsets`

**备选方案**: 键盘快捷键 `Ctrl+Shift+PageUp` / `Ctrl+Shift+PageDown` 可移动 tab。

---

## [2026-05-23] feishu-cli perm/msg "Invalid parameter" 根因分析

**问题**: `feishu-cli perm add` 和 `feishu-cli msg send` 使用 email 类型失败。

**根因**: 应用租户 (cli_a94c29994e381cd4) 中只有一个用户 (ou_c4d77609d5fba99f3edb9a2fba1e14bc / "用户315882")，目标用户 zhiwei.yang@ribbon.ai 不在此租户中。飞书权限/消息 API 使用 tenant_access_token 时，只能操作同租户内的用户。

**解决方案**:
1. `perm add`: 用文档公开权限 (`link_share_entity: anyone_readable`) 替代协作者添加，已设置成功。
2. `msg send`: 无法跨租户发送，需用 `im:message.send_as_user` scope + user access token，但当前 app 缺少此 scope。
3. 长期方案: 将目标用户加入应用所在租户，或使用目标用户所在租户的 app。
[AI-REVIEW] Large commit detected: 255 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 253 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 254 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 255 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 297 lines added. Consider reviewing for AI Psychosis.
