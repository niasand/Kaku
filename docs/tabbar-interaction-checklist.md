# Tabbar Interaction Regression Checklist

> Manual regression checklist for tab bar interactions.
> Run through before each release that touches tabbar, mouseevent, or termwindow code.

## Drag Reorder

- [ ] Drag tab left past midpoint of previous tab → tab swaps position
- [ ] Drag tab right past midpoint of next tab → tab swaps position
- [ ] Drag stays put when cursor hasn't crossed a neighbour's midpoint
- [ ] First tab cannot drag further left (no crash, no ghost)
- [ ] Last tab cannot drag further right (no crash, no ghost)
- [ ] Rapid drag across 3+ tabs → all intermediate swaps complete without stuck state
- [ ] Drag threshold: tiny movements (< 6px) do NOT start drag
- [ ] Release after drag: tab settles at final position, animation completes
- [ ] Drag with non-left button ignored

## Double-click Rename

- [ ] Double-click on tab opens rename modal
- [ ] Rename modal pre-fills current title
- [ ] Press Enter confirms rename → tab title updates
- [ ] Press Escape cancels rename → tab title unchanged
- [ ] Rename modal closes cleanly, focus returns to terminal
- [ ] Cannot trigger rename while another modal is open

## Middle-click Close

- [ ] Middle-click on tab closes that tab
- [ ] Middle-click on active (last) tab closes it, switches to neighbour
- [ ] Middle-click on new-tab (+) button → no crash (action depends on config)
- [ ] Close confirmation (if enabled) appears before closing

## Right-click Navigator

- [ ] Right-click on tab opens tab navigator overlay
- [ ] Navigator shows all open tabs, current tab highlighted
- [ ] Click a tab in navigator → switches to that tab
- [ ] Escape dismisses navigator without switching
- [ ] Right-click on new-tab button → no crash

## Scroll Wheel Tab Switch

- [ ] Scroll up on tab bar switches to previous tab (if `mouse_wheel_scrolls_tabs` enabled)
- [ ] Scroll down on tab bar switches to next tab
- [ ] Scrolling past first/last tab does not wrap around
- [ ] Scroll wheel ignored when `mouse_wheel_scrolls_tabs` is false

## Close Button (X)

- [ ] Hover over tab shows close button on inactive tabs
- [ ] Click close button closes the tab
- [ ] Close button not shown on active tab (or shown per config)
- [ ] Close button hit area matches visual bounds

## New Tab Button (+)

- [ ] Left-click opens new tab in current domain
- [ ] Button always visible at right end of tab bar
- [ ] Button disabled/reduced when tab bar is full (if applicable)

## Click / Activation

- [ ] Single left-click on inactive tab activates it
- [ ] Single left-click on active tab does not interfere
- [ ] Click starts drag state (drag may not activate if below threshold)

## Keyboard Shortcuts (cross-reference)

| Shortcut | Action |
|----------|--------|
| `⌘+1`…`⌘+8` | Activate tab 1–8 |
| `⌘+9` | Activate last tab |
| `⌘+[` / `⌘+]` | Previous/next tab |
| `Ctrl+Tab` | Next tab |
| `Ctrl+Shift+PageUp/Down` | Move tab left/right |

## Edge Cases

- [ ] Single tab: all interactions safe (no crash on drag, middle-click, etc.)
- [ ] Many tabs (10+): tab bar scrolls/truncates, drag still works in visible area
- [ ] Tab title with emoji/CJK characters: rename and display correct
- [ ] Tab with SSH session: title shows SSH destination, interactions work
- [ ] Rapid open/close of tabs: no use-after-free, no stale drag state
