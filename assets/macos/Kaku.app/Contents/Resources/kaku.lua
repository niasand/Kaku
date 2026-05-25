-- Kaku Configuration

local wezterm = require 'wezterm'

local config = {}

-- `config_builder` validates every assignment and is expensive on large configs.
-- Keep startup fast by default; enable strict validation only when debugging config.
if os.getenv('KAKU_STRICT_CONFIG') == '1' and wezterm.config_builder then
  config = wezterm.config_builder()
end

local function basename(path)
  return path:match('([^/]+)$')
end

local function pane_hint_key(pane)
  if not pane then
    return nil
  end

  local ok, pane_id = pcall(function()
    return pane:pane_id()
  end)
  if not ok or not pane_id then
    return nil
  end

  return tostring(pane_id)
end

-- URL decode helper for Chinese characters in paths
-- Converts %E9%9F%B3%E4%B9%90 -> 音乐
local function url_decode(str)
  if not str then
    return str
  end
  -- First, handle UTF-8 encoded sequences (%XX%YY%ZZ)
  local result = str:gsub('%%([0-9A-Fa-f][0-9A-Fa-f])', function(hex)
    return string.char(tonumber(hex, 16))
  end)
  return result
end

local function default_kaku_user_config_path()
  local xdg = os.getenv('XDG_CONFIG_HOME')
  if xdg and xdg ~= '' then
    return xdg .. '/kaku/kaku.lua'
  end
  local home = os.getenv('HOME')
  if home then
    return home .. '/.config/kaku/kaku.lua'
  end
  return nil
end

local function is_bundled_kaku_config_path(path)
  if type(path) ~= 'string' or path == '' then
    return false
  end

  local normalized = path:gsub('\\', '/')
  return normalized:match('/Kaku%.app/Contents/Resources/kaku%.lua$') ~= nil
    or normalized:match('/assets/macos/Kaku%.app/Contents/Resources/kaku%.lua$') ~= nil
end

-- Detect if user has custom config overrides in their config file.
-- Prefer the actively loaded config file, but ignore the bundled defaults file.
local function kaku_user_config_path()
  local runtime_config = nil
  if type(wezterm.config_file) == 'string' and wezterm.config_file ~= '' then
    runtime_config = wezterm.config_file
  end
  if (not runtime_config or runtime_config == '') then
    local env_config = os.getenv('KAKU_CONFIG_FILE')
    if env_config and env_config ~= '' then
      runtime_config = env_config
    end
  end

  if runtime_config and runtime_config ~= '' and not is_bundled_kaku_config_path(runtime_config) then
    return runtime_config
  end

  return default_kaku_user_config_path()
end

local user_has_custom_padding = false
local user_has_custom_font = false
local user_has_custom_font_rules = false
local user_has_custom_window_frame = false

local function check_user_custom_config()
  local user_config_path = kaku_user_config_path()
  if not user_config_path then
    return
  end
  local file = io.open(user_config_path, 'r')
  if not file then
    return
  end
  -- Check if user explicitly sets these configs (skip comment lines).
  for line in file:lines() do
    local trimmed = line:match('^%s*(.-)%s*$')
    if trimmed and not trimmed:match('^%-%-') then
      if trimmed:match('^config%.window_padding%s*=') then
        user_has_custom_padding = true
      end
      if trimmed:match('^config%.font%s*=') then
        user_has_custom_font = true
      end
      if trimmed:match('^config%.font_rules%s*=') then
        user_has_custom_font_rules = true
      end
      if trimmed:match('^config%.window_frame%s*=') then
        user_has_custom_window_frame = true
      end
    end
  end
  file:close()
end
check_user_custom_config()

local function should_remember_last_cwd()
  return config.remember_last_cwd ~= false
end

-- Detect macOS appearance via `defaults read` as a reliable fallback when
-- wezterm.gui is not yet available (early Lua init, TUI processes like
-- `kaku config` / `kaku ai`). Mirrors the Rust-side is_macos_dark_mode().
local function is_macos_dark_appearance()
  local handle = io.popen('defaults read -g AppleInterfaceStyle 2>/dev/null')
  if not handle then
    return true
  end
  local result = handle:read('*a') or ''
  handle:close()
  return result:find('Dark') ~= nil
end

local function resolve_appearance_color_scheme()
  local gui = wezterm.gui
  if gui and type(gui.get_appearance) == 'function' then
    local ok, appearance = pcall(gui.get_appearance)
    if ok and type(appearance) == 'string' then
      return appearance:find('Dark', 1, true) and 'Kaku Dark' or 'Kaku Light'
    end
  end
  return is_macos_dark_appearance() and 'Kaku Dark' or 'Kaku Light'
end

local function resolve_kaku_color_scheme(scheme)
  if scheme == 'Auto' then
    return resolve_appearance_color_scheme()
  end
  if not scheme or scheme == '' then
    return resolve_appearance_color_scheme()
  end
  return scheme
end

-- Optional Claude Code theme sync with Kaku color scheme.
-- Disabled by default because it writes ~/.claude.json.
-- Set KAKU_CLAUDE_SYNC=1 to opt in.
local kaku_last_synced_cc_theme = nil

local function sync_claude_code_theme(is_light)
  if os.getenv('KAKU_CLAUDE_SYNC') ~= '1' then return end
  local target = is_light and 'light-ansi' or 'dark-ansi'
  if kaku_last_synced_cc_theme == target then return end
  local home = os.getenv('HOME')
  if not home or home == '' then return end
  kaku_last_synced_cc_theme = target
  wezterm.run_child_process({
    'python3', '-c',
    [[
import json, os, pathlib, sys
p = pathlib.Path(os.environ['HOME']) / '.claude.json'
if not p.exists():
    sys.exit(0)
try:
    d = json.loads(p.read_text())
    if d.get('theme') == sys.argv[1]:
        sys.exit(0)
    d['theme'] = sys.argv[1]
    tmp = p.with_suffix('.json.tmp')
    tmp.write_text(json.dumps(d))
    tmp.replace(p)
except Exception:
    pass
]],
    target,
  })
end

-- Two-tier display detection.
-- low resolution screens use smaller spacing and 15px font.
-- high resolution screens use default spacing and 17px font.
local function is_low_resolution_screen()
  local success, screens = pcall(function()
    return wezterm.gui.screens()
  end)
  if success and screens and screens.main then
    local main = screens.main
    local width = tonumber(main.width or 0) or 0
    local height = tonumber(main.height or 0) or 0
    local short_edge = math.min(width, height)
    -- Inline builtin screen detection.
    local name = string.lower(tostring(main.name or ''))
    local is_builtin = name == 'color lcd'
      or string.find(name, 'built-in', 1, true)
      or string.find(name, 'built in', 1, true)
      or string.find(name, '内建', 1, true)
    if short_edge > 0 then
      if is_builtin then
        return short_edge <= 1700
      end
      return short_edge < 1800
    end
  end
  return false
end

-- Compute once; all spacing helpers below share this result.
local low_resolution_screen = is_low_resolution_screen()

local function get_default_padding()
  if low_resolution_screen then
    return { left = '26px', right = '26px', top = '26px', bottom = '0px' }
  end
  return { left = '40px', right = '40px', top = '40px', bottom = '0px' }
end

-- get_fullscreen_padding has been removed.
-- Fullscreen padding adjustments are now handled synchronously in Rust (resize.rs)
-- to avoid async layout jitter.

-- Per-window resize debounce state.
-- Weak keys ensure closed windows don't leak state.
local resize_state_by_window = setmetatable({}, { __mode = 'k' })

local function monotonic_now()
  -- Keep this numeric for debounce arithmetic.
  -- Some runtime environments expose os.clock() as a non-number value.
  local ok, value = pcall(os.clock)
  if ok and type(value) == 'number' then
    return value
  end
  return os.time()
end

local function dims_hash(dims)
  return dims.pixel_width .. "x" .. dims.pixel_height
end

local function update_window_config(window, is_full_screen, _pane)
  local now = monotonic_now()
  local dims = window:get_dimensions()
  local current_hash = dims_hash(dims)
  local state = resize_state_by_window[window]
  if not state then
    state = {
      last_resize_time = 0,
      last_dims_hash = "",
      last_is_full_screen = is_full_screen == true,
    }
    resize_state_by_window[window] = state
  end
  -- macOS Space/focus transitions can briefly report `is_full_screen=false`
  -- while the window is unfocused. If we immediately downgrade overrides
  -- in that transient frame, returning focus causes a visible padding jump.
  local effective_full_screen = is_full_screen
  if not effective_full_screen and state.last_is_full_screen then
    local ok_focused, focused = pcall(function()
      return window:is_focused()
    end)
    if ok_focused and not focused then
      effective_full_screen = true
    end
  end
  local overrides = window:get_config_overrides() or {}
  local needs_update = false

  -- Padding is now owned by the base config plus Rust layout policy.
  -- Keep overrides cleared so focus/resize transitions don't trigger a
  -- redundant config reload just to restate the default value.
  local padding_needs_update = overrides.window_padding ~= nil

  -- Top-tab fullscreen layout is now computed entirely in Rust so Space/app
  -- switches do not bounce through a second config reload that changes
  -- hide_tab_bar_if_only_one_tab or window_content_alignment.
  local tab_bar_needs_update = overrides.hide_tab_bar_if_only_one_tab ~= nil
  local alignment_needs_update = overrides.window_content_alignment ~= nil

  needs_update = padding_needs_update
    or tab_bar_needs_update
    or alignment_needs_update

  -- Skip update if dimensions changed rapidly (within 1 second) and state is stable
  -- This prevents padding flicker during fullscreen animation
  if current_hash ~= state.last_dims_hash then
    local time_since_last = now - state.last_resize_time
    if time_since_last < 1.0 and not needs_update then
      -- Rapid change detected, skip this update
      state.last_dims_hash = current_hash
      state.last_resize_time = now
      state.last_is_full_screen = effective_full_screen
      return
    end
    state.last_dims_hash = current_hash
    state.last_resize_time = now
  end

  if not needs_update then
    state.last_is_full_screen = effective_full_screen
    return
  end

  overrides.window_padding = nil
  overrides.hide_tab_bar_if_only_one_tab = nil
  overrides.window_content_alignment = nil

  state.last_is_full_screen = effective_full_screen
  window:set_config_overrides(overrides)
end

local function extract_path_from_cwd(cwd)
  if not cwd then
    return ''
  end

  local path = ''
  if type(cwd) == 'userdata' then
    -- pane:get_current_working_dir() returns a Url userdata (not a table).
    -- .file_path gives the already percent-decoded local path.
    path = (cwd.file_path or ''):gsub('/$', '')
    return path
  elseif type(cwd) == 'table' then
    path = cwd.file_path or cwd.path or tostring(cwd)
  else
    path = tostring(cwd)
  end

  path = path:gsub('^file://[^/]*', ''):gsub('/$', '')
  -- Decode URL-encoded characters (e.g., %E9%9F%B3%E4%B9%90 -> 音乐)
  path = url_decode(path)
  return path
end

local active_tab_cwd_cache = {}
-- os.time() returns integer wall-clock seconds; 1s granularity is fine for tab title throttle
local active_tab_cwd_refresh_interval = 1
local function now_secs()
  return os.time()
end
local runtime_cwd_startup_grace_secs = 3
local runtime_cwd_warmup_until_secs = now_secs() + runtime_cwd_startup_grace_secs

local home_dir = os.getenv("HOME")
local kaku_state_dir = home_dir and (home_dir .. "/.config/kaku") or nil
local lazygit_state_file = kaku_state_dir and (kaku_state_dir .. "/lazygit_state.json") or nil
local last_cwd_file = kaku_state_dir and (kaku_state_dir .. "/last_cwd") or nil
local last_saved_cwd = nil

local function save_last_cwd(path)
  if not last_cwd_file or not path or path == '' then return end
  if path == last_saved_cwd then return end
  local f = io.open(last_cwd_file, 'w')
  if f then
    f:write(path .. '\n')
    f:close()
    last_saved_cwd = path
  end
end

local function read_last_cwd()
  if not last_cwd_file then return nil end
  local f = io.open(last_cwd_file, 'r')
  if not f then return nil end
  local path = f:read('l')
  f:close()
  if not path or path == '' then return nil end
  return path
end
local lazygit_state_cache = nil
local lazygit_repo_probe_cache = {}
local lazygit_repo_probe_interval_secs = 5
local lazygit_command_probe = { value = nil, command = nil, checked_at = 0 }
local lazygit_command_probe_interval_secs = 30
local lazygit_hint_startup_grace_secs = 3
local lazygit_hint_warmup_until_secs = now_secs() + lazygit_hint_startup_grace_secs
local lazygit_hint_schedule_cooldown_secs = 8
local lazygit_hint_probe_state_by_pane = {}

local function trim_trailing_whitespace(value)
  if type(value) ~= "string" then
    return ""
  end
  return value:gsub("%s+$", "")
end

local function trim_surrounding_whitespace(value)
  if type(value) ~= "string" then
    return ""
  end
  return value:gsub("^%s+", ""):gsub("%s+$", "")
end

local function run_process(args)
  local ok, success, stdout, stderr = pcall(function()
    return wezterm.run_child_process(args)
  end)
  if not ok then
    return false, "", tostring(success or "")
  end
  return success, tostring(stdout or ""), tostring(stderr or "")
end

local function strip_wrapping_quotes(value)
  if type(value) ~= "string" then
    return ""
  end
  local trimmed = trim_surrounding_whitespace(value)
  local first = trimmed:sub(1, 1)
  local last = trimmed:sub(-1)
  if #trimmed >= 2 and ((first == "'" and last == "'") or (first == '"' and last == '"')) then
    return trimmed:sub(2, -2)
  end
  return trimmed
end

local function ensure_kaku_state_dir()
  if not kaku_state_dir or kaku_state_dir == "" then
    return
  end
  os.execute(string.format("mkdir -p %q", kaku_state_dir))
end

local function load_lazygit_state()
  if lazygit_state_cache then
    return lazygit_state_cache
  end

  local state = { repos = {} }
  if not lazygit_state_file then
    lazygit_state_cache = state
    return state
  end

  local file = io.open(lazygit_state_file, "r")
  if file then
    local raw = file:read("*all")
    file:close()
    if raw and raw ~= "" then
      local ok, parsed = pcall(wezterm.json_parse, raw)
      if ok and type(parsed) == "table" then
        state = parsed
      end
    end
  end

  if type(state.repos) ~= "table" then
    state.repos = {}
  end

  lazygit_state_cache = state
  return state
end

local function save_lazygit_state()
  if not lazygit_state_file then
    return
  end

  ensure_kaku_state_dir()
  local state = load_lazygit_state()
  local ok, encoded = pcall(wezterm.json_encode, state)
  if not ok or type(encoded) ~= "string" or encoded == "" then
    return
  end

  local file = io.open(lazygit_state_file, "w")
  if not file then
    return
  end
  file:write(encoded .. "\n")
  file:close()
end

local function get_lazygit_repo_flags(repo_root)
  local repos = load_lazygit_state().repos
  local flags = repos[repo_root]
  if type(flags) ~= "table" then
    flags = { hinted = false, used = false }
    repos[repo_root] = flags
  end
  flags.hinted = flags.hinted == true
  flags.used = flags.used == true
  return flags
end

local function mark_repo_lazygit_hinted(repo_root)
  if not repo_root or repo_root == "" then
    return
  end
  local flags = get_lazygit_repo_flags(repo_root)
  if flags.hinted then
    return
  end
  flags.hinted = true
  save_lazygit_state()
end

local function mark_repo_lazygit_used(repo_root)
  if not repo_root or repo_root == "" then
    return
  end
  local flags = get_lazygit_repo_flags(repo_root)
  if flags.used then
    return
  end
  flags.used = true
  save_lazygit_state()
end

local function pane_cwd(pane)
  if not pane then
    return ""
  end

  local ok, runtime_cwd = pcall(function()
    return pane:get_current_working_dir()
  end)
  if ok and runtime_cwd then
    local path = extract_path_from_cwd(runtime_cwd)
    if path ~= "" then
      return path
    end
  end

  return extract_path_from_cwd(pane.current_working_dir)
end

local function compact_home_path(path)
  if not path or path == '' then
    return ''
  end
  if home_dir and home_dir ~= '' then
    if path == home_dir then
      return '~'
    end
    local prefix = home_dir .. '/'
    if path:sub(1, #prefix) == prefix then
      return '~/' .. path:sub(#prefix + 1)
    end
  end
  return path
end

local function cwd_title(path, basename_only)
  if not path or path == '' then
    return ''
  end
  if basename_only then
    return basename(path) or path
  end
  return compact_home_path(path)
end

local function detect_git_repo_root(path)
  if not path or path == "" then
    return nil
  end

  local ok, stdout = wezterm.run_child_process({
    "git",
    "-C",
    path,
    "rev-parse",
    "--show-toplevel",
  })
  if not ok then
    return nil
  end

  local repo_root = trim_trailing_whitespace(stdout)
  if repo_root == "" then
    return nil
  end
  return repo_root
end

local function repo_has_pending_changes(repo_root)
  local ok, stdout = wezterm.run_child_process({
    "git",
    "-C",
    repo_root,
    "status",
    "--porcelain",
    "--untracked-files=no",
    "--no-optional-locks",
  })
  if not ok then
    return false
  end
  return trim_trailing_whitespace(stdout) ~= ""
end

local function git_repo_context(path)
  local now = now_secs()
  local cached = lazygit_repo_probe_cache[path]
  if cached and (now - cached.checked_at) < lazygit_repo_probe_interval_secs then
    return cached.repo_root, cached.has_changes
  end

  local repo_root = detect_git_repo_root(path)
  local has_changes = false
  if repo_root then
    has_changes = repo_has_pending_changes(repo_root)
  end

  lazygit_repo_probe_cache[path] = {
    checked_at = now,
    repo_root = repo_root,
    has_changes = has_changes,
  }

  return repo_root, has_changes
end

local function resolve_lazygit_command()
  local now = now_secs()
  local cached_value = lazygit_command_probe.value
  if cached_value ~= nil then
    local age = now - lazygit_command_probe.checked_at
    if cached_value or age < lazygit_command_probe_interval_secs then
      return lazygit_command_probe.command
    end
  end

  local candidates = {
    "lazygit",
    "/opt/homebrew/bin/lazygit",
    "/usr/local/bin/lazygit",
  }
  local resolved = nil
  for _, cmd in ipairs(candidates) do
    local call_ok, run_result = pcall(function()
      return select(1, wezterm.run_child_process({ cmd, "--version" }))
    end)
    if call_ok and run_result then
      resolved = cmd
      break
    end
  end

  lazygit_command_probe.value = resolved ~= nil
  lazygit_command_probe.command = resolved
  lazygit_command_probe.checked_at = now
  return resolved
end

local function is_lazygit_installed()
  return resolve_lazygit_command() ~= nil
end

local function pane_is_lazygit(pane)
  if not pane then
    return false
  end

  local ok, proc = pcall(function()
    return pane:get_foreground_process_name()
  end)
  if not ok or type(proc) ~= "string" or proc == "" then
    return false
  end

  return basename(proc) == "lazygit"
end

local function resolve_active_pane(window, pane)
  if pane then
    return pane
  end
  if not window then
    return nil
  end

  local ok_tab, tab = pcall(function()
    return window:active_tab()
  end)
  if ok_tab and tab then
    local ok_pane, active_pane = pcall(function()
      return tab:active_pane()
    end)
    if ok_pane then
      return active_pane
    end
  end

  return nil
end

local function show_lazygit_toast(window, pane, event_name)
  if not window then
    return
  end
  pcall(function()
    window:perform_action(wezterm.action.EmitEvent(event_name), pane)
  end)
end

local function maybe_show_lazygit_hint(window, pane)
  if now_secs() < lazygit_hint_warmup_until_secs then
    return
  end

  pane = resolve_active_pane(window, pane)

  local path = pane_cwd(pane)
  if path == "" then
    return
  end

  local repo_root, has_changes = git_repo_context(path)
  if not repo_root then
    return
  end

  if pane_is_lazygit(pane) then
    mark_repo_lazygit_used(repo_root)
    return
  end

  local flags = get_lazygit_repo_flags(repo_root)
  if flags.hinted or flags.used or not has_changes then
    return
  end

  if not is_lazygit_installed() then
    return
  end

  show_lazygit_toast(window, pane, "kaku-toast-try-lazygit")
  mark_repo_lazygit_hinted(repo_root)
end

local function schedule_lazygit_hint_probe(window, pane)
  local active_pane = resolve_active_pane(window, pane)
  if not active_pane then
    return
  end

  local pane_id_ok, pane_id_value = pcall(function()
    return active_pane:pane_id()
  end)
  if not pane_id_ok or not pane_id_value then
    return
  end

  local pane_id = tostring(pane_id_value)
  local state = lazygit_hint_probe_state_by_pane[pane_id] or {}
  lazygit_hint_probe_state_by_pane[pane_id] = state

  if state.scheduled then
    return
  end

  local now = now_secs()
  if state.last_scheduled_at and (now - state.last_scheduled_at) < lazygit_hint_schedule_cooldown_secs then
    return
  end

  state.scheduled = true
  state.last_scheduled_at = now

  local delay = math.max(0, lazygit_hint_warmup_until_secs - now)
  wezterm.time.call_after(delay, function()
    state.scheduled = false
    pcall(function()
      maybe_show_lazygit_hint(window, active_pane)
    end)
  end)
end

local function launch_lazygit(window, pane)
  pane = resolve_active_pane(window, pane)
  if not pane then
    show_lazygit_toast(window, pane, "kaku-toast-lazygit-no-pane")
    return
  end

  local path = pane_cwd(pane)
  if path == "" then
    show_lazygit_toast(window, pane, "kaku-toast-lazygit-no-cwd")
    return
  end

  local repo_root = detect_git_repo_root(path)
  if not repo_root then
    show_lazygit_toast(window, pane, "kaku-toast-lazygit-not-git")
    return
  end

  local lazygit_cmd = resolve_lazygit_command()
  if not lazygit_cmd then
    show_lazygit_toast(window, pane, "kaku-toast-lazygit-missing")
    return
  end

  local ok = pcall(function()
    -- Send Ctrl+U first to clear any partially typed input at the prompt,
    -- preventing the command from being appended to existing line content.
    window:perform_action(
      wezterm.action.SendString("\x15" .. lazygit_cmd .. "\r"),
      pane
    )
  end)
  if not ok then
    show_lazygit_toast(window, pane, "kaku-toast-lazygit-dispatch-failed")
    return
  end
  mark_repo_lazygit_used(repo_root)
end

local function evict_stale_cache(live_pane_ids)
  for pane_id in pairs(active_tab_cwd_cache) do
    if not live_pane_ids[pane_id] then
      active_tab_cwd_cache[pane_id] = nil
    end
  end
end

local function pane_title_path(pane)
  if not pane then
    return ''
  end
  local path = extract_path_from_cwd(pane.current_working_dir)
  if path == '' then
    return ''
  end
  return path
end

local function tab_title_path(tab)
  local pane = tab.active_pane
  if not pane then
    return ''
  end

  local source_cwd = pane.current_working_dir
  local source_path = extract_path_from_cwd(source_cwd)
  local path = source_path

  if tab.is_active then
    local pane_id = tostring(pane.pane_id)
    local now = now_secs()
    local runtime_cwd_ready = now >= runtime_cwd_warmup_until_secs
    local cached = active_tab_cwd_cache[pane_id]
    local should_refresh = (not cached)
      or path == ''
      or source_path ~= cached.source_path
      or (now - cached.updated_at) >= active_tab_cwd_refresh_interval

    if should_refresh then
      local ok, runtime_cwd = pcall(function()
        if not runtime_cwd_ready then
          return nil
        end
        return pane:get_current_working_dir()
      end)
      if ok and runtime_cwd then
        local runtime_path = extract_path_from_cwd(runtime_cwd)
        if runtime_path ~= '' then
          path = runtime_path
        end
      end

      active_tab_cwd_cache[pane_id] = {
        path = path,
        source_path = source_path,
        updated_at = now,
      }
    elseif cached and cached.path ~= '' then
      path = cached.path
    end
  elseif path == '' and now_secs() >= runtime_cwd_warmup_until_secs then
    local ok, runtime_cwd = pcall(function()
      return pane:get_current_working_dir()
    end)
    if ok and runtime_cwd then
      path = extract_path_from_cwd(runtime_cwd)
    end
  end

  if path == '' then
    return ''
  end

  return path
end

-- ===== Kaku Palette =====
-- Highlight hues sit between Aura's vivid defaults and Aura "Soft":
-- a third of the way back toward vivid, so colors stay punchy but not
-- fluorescent on a #15141b background. Foreground white is dimmed by
-- ~10% from Aura's #edecee for lower glare without losing legibility.
local KAKU = {
  BLACK = '#15141b',
  ANSI_BLACK = '#110f18',
  WHITE = '#d5d4d6',
  GRAY = '#6d6d6d',
  PURPLE = '#8e6ad9',
  -- Use rgba() here because config::RgbaColor does not accept #RRGGBBAA.
  PURPLE_FADING = 'rgba(61,55,94,0.5)',
  SURFACE = '#1f1d28',
  SURFACE_ACTIVE = '#29263c',
  GREEN = '#58d8ad',
  ORANGE = '#daae76',
  PINK = '#d383da',
  BLUE = '#68afda',
  BRIGHT_BLUE = '#90c9e6',
  RED = '#d85d5d',
}

-- Track bell events per pane for tab notification indicator.
-- Unlike has_unseen_output (which fires on any output, making the indicator
-- permanently lit for TUI apps like Claude Code), bell events only fire when
-- a program explicitly sends BEL (\a), making them suitable as completion signals.
local _bell_panes = {}
local _last_bell_evict_secs = 0

wezterm.on('bell', function(window, pane)
  _bell_panes[tostring(pane:pane_id())] = true
end)

local function evict_stale_bell_panes(live_pane_ids)
  for pane_id in pairs(_bell_panes) do
    if not live_pane_ids[pane_id] then
      _bell_panes[pane_id] = nil
    end
  end
end

local function tab_pane_keys(tab)
  local keys = {}
  if not tab then
    return keys
  end

  if type(tab.panes) == 'table' then
    for _, pane in ipairs(tab.panes) do
      if pane and pane.pane_id then
        keys[#keys + 1] = tostring(pane.pane_id)
      end
    end
  end

  if #keys == 0 and tab.active_pane and tab.active_pane.pane_id then
    keys[1] = tostring(tab.active_pane.pane_id)
  end

  return keys
end

local function tab_has_bell_from_keys(pane_keys)
  for _, pane_key in ipairs(pane_keys) do
    if _bell_panes[pane_key] then
      return true
    end
  end
  return false
end

local function clear_tab_bells_from_keys(pane_keys)
  for _, pane_key in ipairs(pane_keys) do
    _bell_panes[pane_key] = nil
  end
end

local function tab_display_title(tab, effective_config)
  local active_pane = tab and tab.active_pane or nil
  local text = tab and tab.tab_title or ''

  if text == '' and tab then
    local basename_only = effective_config and effective_config.tab_title_show_basename_only
    text = cwd_title(tab_title_path(tab), basename_only)
  end

  if text == '' and active_pane then
    text = active_pane.title or ''
  end
  if text == '' and active_pane then
    text = active_pane.title or ''
  end
  if text == '' then
    text = 'no cwd'
  end

  return text, active_pane
end

wezterm.on('format-tab-title', function(tab, tabs, panes, effective_config, hover, max_width)
  -- Evict stale cache only on the first tab to avoid O(n²) across the render cycle
  if tab.tab_index == 0 then
    local live_pane_ids = {}
    for _, t in ipairs(tabs) do
      for _, pane_key in ipairs(tab_pane_keys(t)) do
        live_pane_ids[pane_key] = true
      end
    end
    evict_stale_cache(live_pane_ids)
    local now = now_secs()
    if now - _last_bell_evict_secs >= 5 then
      evict_stale_bell_panes(live_pane_ids)
      _last_bell_evict_secs = now
    end
  end

  local tab_bar_colors = effective_config.resolved_palette.tab_bar
  local pane_keys = tab_pane_keys(tab)
  local has_bell = tab_has_bell_from_keys(pane_keys)
  if has_bell and tab.is_active then
    clear_tab_bells_from_keys(pane_keys)
    has_bell = false
  end

  local tab_bg = tab_bar_colors and tab_bar_colors.background
  local is_light = tab_bg == '#FFFCF0' or tab_bg == '#fffcf0'
  local dot_color = is_light and '#AD8301' or KAKU.ORANGE

  local fg_active = KAKU.WHITE
  local fg_inactive_pane = KAKU.GRAY
  if tab_bar_colors then
    local entry = tab.is_active and tab_bar_colors.active_tab
      or (hover and (tab_bar_colors.inactive_tab_hover or tab_bar_colors.inactive_tab))
      or tab_bar_colors.inactive_tab
    if entry and entry.fg_color then
      fg_active = entry.fg_color
    end
  end
  if not tab.is_active and not hover then
    fg_active = KAKU.GRAY
  end

  -- Collect panes for this tab, sorted by pane_id for stable order
  local own_panes = {}
  if type(tab.panes) == 'table' then
    for _, p in ipairs(tab.panes) do
      own_panes[#own_panes + 1] = p
    end
    table.sort(own_panes, function(a, b) return a.pane_id < b.pane_id end)
  end

  -- Multi-pane path: render each pane's cwd, active segment highlighted
  if #own_panes > 1 and tab.tab_title == '' then
    local segments = {}
    local seg_index = {}
    local basename_only = effective_config and effective_config.tab_title_show_basename_only
    for _, p in ipairs(own_panes) do
      local seg_text = cwd_title(pane_title_path(p), basename_only)
      if seg_text == '' then
        seg_text = p.title or '?'
      end
      local idx = seg_index[seg_text]
      if idx then
        if p.is_active then
          segments[idx].active = true
        end
      else
        segments[#segments + 1] = { text = seg_text, active = p.is_active }
        seg_index[seg_text] = #segments
      end
    end

    -- Width budget: reserve 2 cells (leading space + trailing space/bell)
    local budget = math.max(4, max_width - 2)
    local sep = ' \u{00b7} '  -- U+00B7 middle dot with spaces
    local sep_len = 3          -- each separator is 3 chars

    -- Compute total length
    local total = 0
    for i, seg in ipairs(segments) do
      total = total + #seg.text
      if i < #segments then
        total = total + sep_len
      end
    end

    -- Trim non-active segments to a single char if over budget
    if total > budget then
      for _, seg in ipairs(segments) do
        if not seg.active and #seg.text > 1 then
          total = total - (#seg.text - 1)
          seg.text = '\u{2026}'  -- U+2026 ellipsis
        end
        if total <= budget then break end
      end
    end

    -- Build FormatItem sequence
    local items = { { Text = ' ' } }
    for i, seg in ipairs(segments) do
      if seg.active then
        items[#items + 1] = { Attribute = { Intensity = 'Bold' } }
        items[#items + 1] = { Foreground = { Color = fg_active } }
      else
        items[#items + 1] = { Attribute = { Intensity = 'Normal' } }
        items[#items + 1] = { Foreground = { Color = fg_inactive_pane } }
      end
      items[#items + 1] = { Text = seg.text }
      if i < #segments then
        items[#items + 1] = { Attribute = { Intensity = 'Normal' } }
        items[#items + 1] = { Foreground = { Color = fg_inactive_pane } }
        items[#items + 1] = { Text = sep }
      end
    end

    -- Trailing bell dot or space
    if effective_config.bell_tab_indicator ~= false then
      items[#items + 1] = { Foreground = { Color = has_bell and dot_color or fg_active } }
      items[#items + 1] = { Text = has_bell and '\u{2022}' or ' ' }
    else
      items[#items + 1] = { Text = ' ' }
    end

    return items
  end

  -- Single-pane path (original logic)
  local text, active_pane = tab_display_title(tab, effective_config)
  if active_pane and active_pane.is_zoomed then
    text = text .. ' [Z]'
  end
  text = wezterm.truncate_right(text, math.max(8, max_width - 2))

  local intensity = tab.is_active and 'Bold' or 'Normal'

  -- Bell indicator: the dot occupies the same 1-cell slot as the trailing space
  -- so tab width stays constant (N+2) whether or not a bell is pending.
  if effective_config.bell_tab_indicator ~= false then
    return {
      { Attribute = { Intensity = intensity } },
      { Foreground = { Color = fg_active } },
      { Text = ' ' .. text },
      { Foreground = { Color = has_bell and dot_color or fg_active } },
      { Text = has_bell and '\u{2022}' or ' ' },
    }
  end

  return {
    { Attribute = { Intensity = intensity } },
    { Foreground = { Color = fg_active } },
    { Text = ' ' .. text .. ' ' },
  }
end)

wezterm.on('format-window-title', function(tab, pane, tabs, _, effective_config)
  local active_tab = tab
  if not active_tab and type(tabs) == 'table' then
    for _, candidate in ipairs(tabs) do
      if candidate.is_active then
        active_tab = candidate
        break
      end
    end
  end

  local text = ''
  local active_pane = pane or (active_tab and active_tab.active_pane) or nil
  if active_tab then
    text, active_pane = tab_display_title(active_tab, effective_config)
  elseif active_pane then
    text = active_pane.title or ''
  end

  if text == '' then
    text = 'no cwd'
  end
  if active_pane and active_pane.is_zoomed and not text:match(' %[Z%]$') then
    text = text .. ' [Z]'
  end

  local tab_count = type(tabs) == 'table' and #tabs or 0
  if tab_count > 1 and active_tab and active_tab.tab_index ~= nil then
    return string.format('[%d/%d] %s', active_tab.tab_index + 1, tab_count, text)
  end

  if effective_config and effective_config.hide_tab_bar_if_only_one_tab and tab_count <= 1 then
    return text
  end

  return text
end)

wezterm.on('window-resized', function(window, _)
  local dims = window:get_dimensions()
  local active_pane = nil
  local ok_tab, tab = pcall(function()
    return window:active_tab()
  end)
  if ok_tab and tab then
    local ok_pane, pane = pcall(function()
      return tab:active_pane()
    end)
    if ok_pane then
      active_pane = pane
    end
  end
  update_window_config(window, dims.is_full_screen, active_pane)
end)

wezterm.on('kaku-launch-lazygit', function(window, pane)
  launch_lazygit(window, pane)
end)

wezterm.on('update-right-status', function(window, pane)
  pane = resolve_active_pane(window, pane)
  if should_remember_last_cwd() then
    local ok, cwd = pcall(function() return pane:get_current_working_dir() end)
    if ok and cwd then
      -- Url userdata: .host is nil for local file:/// URLs, hostname string for SSH.
      local is_local = (cwd.host == nil or cwd.host == '')
      if is_local then
        save_last_cwd(extract_path_from_cwd(cwd))
      end
    end
  end
  schedule_lazygit_hint_probe(window, pane)

  local dims = window:get_dimensions()
  update_window_config(window, dims.is_full_screen, pane)
  if not dims.is_full_screen then
    window:set_right_status('')
    return
  end

  local clock_icon = wezterm.nerdfonts.md_clock_time_four_outline
    or wezterm.nerdfonts.md_clock_outline
    or ''
  local text = wezterm.strftime('%H:%M')
  if clock_icon ~= '' then
    window:set_right_status(wezterm.format({
      { Foreground = { Color = KAKU.GRAY } },
      { Text = ' ' .. clock_icon .. ' ' .. text .. ' ' },
    }))
    return
  end
  window:set_right_status(wezterm.format({
    { Foreground = { Color = KAKU.GRAY } },
    { Text = ' ' .. text .. ' ' },
  }))
end)

-- ===== Font =====
-- Use slightly heavier font weight for light theme to improve readability.
-- Light theme: Medium base, SemiBold for bold.
-- Dark theme: Regular base, Medium for bold.
local function build_font_config(is_light)
  local base_weight = is_light and 'Medium' or 'Regular'
  local bold_weight = is_light and 'SemiBold' or 'Medium'

  local font = wezterm.font_with_fallback({
    { family = 'JetBrains Mono', weight = base_weight },
    { family = 'PingFang SC', weight = base_weight },
    'Apple Color Emoji',
  })

  local font_rules = {
    -- Prevent thin weight: use base weight instead of Light for Half intensity
    {
      intensity = 'Half',
      font = wezterm.font_with_fallback({
        { family = 'JetBrains Mono', weight = base_weight },
        { family = 'PingFang SC', weight = base_weight },
        'Apple Color Emoji',
      }),
    },
    -- Normal italic: disable real italics (keep upright)
    {
      intensity = 'Normal',
      italic = true,
      font = wezterm.font_with_fallback({
        { family = 'JetBrains Mono', weight = base_weight, italic = false },
        { family = 'PingFang SC', weight = base_weight },
        'Apple Color Emoji',
      }),
    },
    -- Bold: use heavier weight
    {
      intensity = 'Bold',
      font = wezterm.font_with_fallback({
        { family = 'JetBrains Mono', weight = bold_weight },
        { family = 'PingFang SC', weight = bold_weight },
        'Apple Color Emoji',
      }),
    },
  }

  return font, font_rules
end

-- Check user config to determine initial theme for font weight
local function is_user_light_theme()
  local user_config_path = kaku_user_config_path()
  if not user_config_path then
    return false
  end
  local file = io.open(user_config_path, 'r')
  if not file then
    return false
  end
  for line in file:lines() do
    local trimmed = line:match('^%s*(.-)%s*$')
    if trimmed and not trimmed:match('^%-%-') then
      if trimmed:match("^config%.color_scheme%s*=%s*['\"]Kaku Light['\"]") then
        file:close()
        return true
      end
      if trimmed:match("^config%.color_scheme%s*=%s*['\"]Kaku Dark['\"]") then
        file:close()
        return false
      end
      if trimmed:match('^config%.color_scheme%s*=') and trimmed:match('get_appearance') then
        file:close()
        return resolve_appearance_color_scheme() == 'Kaku Light'
      end
    end
  end
  file:close()
  -- No explicit theme selection means the bundled default should track macOS.
  return resolve_appearance_color_scheme() == 'Kaku Light'
end

-- Only seed the managed default font stack when the user hasn't overridden it.
-- The bundled font_rules are tightly coupled to the bundled JetBrains Mono stack,
-- so they must not remain active when the user selects a custom primary font.
do
  local font, font_rules = build_font_config(is_user_light_theme())
  if not user_has_custom_font then
    config.font = font
  end
  if not user_has_custom_font and not user_has_custom_font_rules then
    config.font_rules = font_rules
  end
end

-- Track last font theme per window to avoid redundant overrides
local window_font_theme = setmetatable({}, { __mode = 'k' })
local window_has_managed_font_override = setmetatable({}, { __mode = 'k' })
local window_has_managed_window_frame_override = setmetatable({}, { __mode = 'k' })
local get_window_frame_colors

local function copy_table(source)
  local copy = {}
  if type(source) ~= 'table' then
    return copy
  end

  for key, value in pairs(source) do
    copy[key] = value
  end
  return copy
end

local function build_managed_window_frame(scheme)
  local frame = copy_table(config.window_frame)
  local colors = get_window_frame_colors(scheme)
  frame.active_titlebar_bg = colors.active_titlebar_bg
  frame.inactive_titlebar_bg = colors.inactive_titlebar_bg
  frame.active_titlebar_fg = colors.active_titlebar_fg
  frame.inactive_titlebar_fg = colors.inactive_titlebar_fg
  frame.active_titlebar_border_bottom = colors.active_titlebar_border_bottom
  frame.inactive_titlebar_border_bottom = colors.inactive_titlebar_border_bottom
  frame.border_left_width = colors.border_left_width
  frame.border_right_width = colors.border_right_width
  frame.border_top_height = colors.border_top_height
  frame.border_bottom_height = colors.border_bottom_height
  frame.border_left_color = colors.border_left_color
  frame.border_right_color = colors.border_right_color
  frame.border_top_color = colors.border_top_color
  frame.border_bottom_color = colors.border_bottom_color
  return frame
end

local function window_frame_matches_theme(frame, scheme)
  if type(frame) ~= 'table' then
    return false
  end

  local colors = get_window_frame_colors(scheme)
  return frame.active_titlebar_bg == colors.active_titlebar_bg
    and frame.inactive_titlebar_bg == colors.inactive_titlebar_bg
    and frame.active_titlebar_fg == colors.active_titlebar_fg
    and frame.inactive_titlebar_fg == colors.inactive_titlebar_fg
    and frame.active_titlebar_border_bottom == colors.active_titlebar_border_bottom
    and frame.inactive_titlebar_border_bottom == colors.inactive_titlebar_border_bottom
    and frame.border_left_width == colors.border_left_width
    and frame.border_right_width == colors.border_right_width
    and frame.border_top_height == colors.border_top_height
    and frame.border_bottom_height == colors.border_bottom_height
    and frame.border_left_color == colors.border_left_color
    and frame.border_right_color == colors.border_right_color
    and frame.border_top_color == colors.border_top_color
    and frame.border_bottom_color == colors.border_bottom_color
end

-- Dynamically switch font weight when theme changes
wezterm.on('window-config-reloaded', function(window, pane)
  local overrides = window:get_config_overrides() or {}
  local scheme = resolve_kaku_color_scheme(overrides.color_scheme or config.color_scheme)
  local is_light = scheme == 'Kaku Light'
  sync_claude_code_theme(is_light)
  local overrides_changed = false

  if user_has_custom_font or user_has_custom_font_rules then
    window_font_theme[window] = nil
    if window_has_managed_font_override[window]
      and (overrides.font ~= nil or overrides.font_rules ~= nil) then
      overrides.font = nil
      overrides.font_rules = nil
      overrides_changed = true
    end
    window_has_managed_font_override[window] = nil
  elseif window_font_theme[window] ~= is_light then
    window_font_theme[window] = is_light

    local font, font_rules = build_font_config(is_light)
    overrides.font = font
    overrides.font_rules = font_rules
    window_has_managed_font_override[window] = true
    overrides_changed = true
  end

  if user_has_custom_window_frame then
    if window_has_managed_window_frame_override[window] and overrides.window_frame ~= nil then
      overrides.window_frame = nil
      overrides_changed = true
    end
    window_has_managed_window_frame_override[window] = nil
  else
    local effective_window_frame = overrides.window_frame or config.window_frame
    if not window_frame_matches_theme(effective_window_frame, scheme) then
      overrides.window_frame = build_managed_window_frame(scheme)
      window_has_managed_window_frame_override[window] = true
      overrides_changed = true
    else
      window_has_managed_window_frame_override[window] = overrides.window_frame ~= nil
    end
  end

  if overrides_changed then
    window:set_config_overrides(overrides)
  end

  local dims = window:get_dimensions()
  update_window_config(window, dims.is_full_screen, pane)
end)

config.bold_brightens_ansi_colors = false

-- Auto-adjust font size using main-screen pixel size.
-- low-resolution screens use 15px.
-- high-resolution screens use 17px.
local function get_font_size()
  if low_resolution_screen then
    return 15.0
  end

  local success, screens = pcall(function()
    return wezterm.gui.screens()
  end)
  if success and screens and screens.main then
    local main = screens.main
    -- Fallback when pixel dimensions are unavailable.
    local dpi = tonumber(main.effective_dpi or 72) or 72
    if dpi < 110 then
      return 15.0
    end
  end
  return 17.0
end

config.font_size = get_font_size()
config.line_height = 1.28
config.cell_width = 1.0
config.harfbuzz_features = { 'calt=0', 'clig=0', 'liga=0' }
config.use_cap_height_to_scale_fallback_fonts = false

config.custom_block_glyphs = true
config.unicode_version = 14

-- Do NOT set config.term = 'kaku' here.
-- Remote servers lack the 'kaku' terminfo entry, causing SSH issues like
-- broken backspace/delete keys. Let the default 'xterm-256color' apply.
-- See: https://github.com/tw93/Kaku/issues/130

-- ===== Cursor =====
config.default_cursor_style = 'BlinkingBar'
config.cursor_thickness = '2px'
config.cursor_blink_rate = 500
-- Sharp on/off blink without fade animation (like a standard terminal).
config.cursor_blink_ease_in = 'Constant'
config.cursor_blink_ease_out = 'Constant'

-- ===== Scrollback =====
config.scrollback_lines = 10000

-- ===== Text Selection =====
config.selection_word_boundary = ' \t\n{}[]()"\'-'  -- Smart selection boundaries

-- ===== Window Layout =====
-- Disable OS-level resize increments. AppKit applies these on the
-- Accessibility setFrame path (Raycast / Rectangle / Magnet / AeroSpace),
-- causing window-management "maximize" to round to cell-aligned dimensions
-- and leave a gap when visibleFrame is not a clean multiple of cell size
-- (reproducible on external displays).
-- Internal cell quantization is handled in apply_dimensions (slack absorbed
-- into top padding), so disabling this is safe.
-- <https://github.com/tw93/Kaku/issues/131>
config.use_resize_increments = false

config.initial_cols = 110
config.initial_rows = 22
-- Keep native macOS window shadow by default.
config.window_decorations = "INTEGRATED_BUTTONS|RESIZE"
-- Window frame colors will be set after color_scheme is determined

config.window_background_opacity = 1.0
config.text_background_opacity = 1.0

-- ===== Close Protection =====
config.window_close_confirmation = 'NeverPrompt'
-- These two booleans are the strict "always ask, even bare zsh" mode.
-- They stay off by default because Kaku's Cmd+W / Cmd+Shift+W keybinds
-- already invoke the *smart-skip* path (`confirm = true` is hard-coded
-- there): a pane closes silently when its process tree is just shells
-- (bash/zsh/fish/tmux/...), and pops a confirm overlay when anything
-- stateful is loaded (claude / codex / cursor-agent / vim / cargo / ...).
-- Set either of these to `true` in your kaku.lua to upgrade to the
-- always-ask mode.
config.tab_close_confirmation = false
config.pane_close_confirmation = false

-- ===== Tab Bar =====
config.enable_tab_bar = true
config.tab_bar_at_bottom = true
config.use_fancy_tab_bar = false
config.tab_max_width = 32
config.hide_tab_bar_if_only_one_tab = true
config.show_tab_index_in_tab_bar = true
config.show_new_tab_button_in_tab_bar = false

-- Compute padding after tab-bar placement is finalized so startup layout
-- matches the runtime override path.
config.window_padding = get_default_padding()

-- ===== Color Scheme =====
local kaku_theme = {
  -- Background
  foreground = KAKU.WHITE,
  background = KAKU.BLACK,

  -- Cursor
  cursor_bg = KAKU.PURPLE,
  cursor_fg = KAKU.BLACK,
  cursor_border = KAKU.PURPLE,

  -- Selection
  selection_bg = KAKU.PURPLE_FADING,
  selection_fg = 'none',

  -- Normal colors (ANSI 0-7)
  ansi = {
    KAKU.ANSI_BLACK, -- black
    KAKU.RED,     -- red
    KAKU.GREEN,   -- green
    KAKU.ORANGE,  -- yellow
    KAKU.BLUE,    -- blue
    KAKU.PURPLE,  -- magenta
    KAKU.GREEN,   -- cyan
    KAKU.WHITE,   -- white
  },

  -- Bright colors (ANSI 8-15)
  brights = {
    KAKU.GRAY,    -- bright black
    KAKU.RED,     -- bright red
    KAKU.GREEN,   -- bright green
    KAKU.ORANGE,  -- bright yellow
    KAKU.BRIGHT_BLUE, -- bright blue
    KAKU.PURPLE,  -- bright magenta
    KAKU.GREEN,   -- bright cyan
    KAKU.WHITE,   -- bright white
  },

  split = KAKU.SURFACE_ACTIVE,

  -- Tab bar colors
  tab_bar = {
    background = KAKU.BLACK,
    inactive_tab_edge = KAKU.BLACK,

    active_tab = {
      bg_color = KAKU.SURFACE_ACTIVE,
      fg_color = KAKU.WHITE,
      intensity = 'Bold',
      underline = 'None',
      italic = false,
      strikethrough = false,
    },

    inactive_tab = {
      bg_color = KAKU.BLACK,
      fg_color = KAKU.GRAY,
      intensity = 'Normal',
    },

    inactive_tab_hover = {
      bg_color = KAKU.SURFACE,
      fg_color = KAKU.WHITE,
      italic = false,
    },

    new_tab = {
      bg_color = KAKU.BLACK,
      fg_color = KAKU.GRAY,
    },

    new_tab_hover = {
      bg_color = KAKU.SURFACE,
      fg_color = KAKU.WHITE,
    },
  },

  -- Override Claude Code quote background for better contrast
  color_overrides = {
    ['#6d6d6d'] = '#3A3942',  -- ANSI 8 (bright black)
    ['#6E6E6E'] = '#3A3942',  -- Claude Code true color
    ['#8EC3FF'] = '#3A3942',  -- Claude Code blue header background
  },
}

-- ===== Kaku Light Theme =====
local kaku_light = {
  foreground = '#100F0F',
  background = '#FFFCF0',

  cursor_bg = '#343331',
  cursor_fg = '#FFFCF0',
  cursor_border = '#343331',

  selection_bg = '#E8E6DB',
  selection_fg = '#100F0F',

  ansi = {
    '#100F0F', -- black
    '#AF3029', -- red-600
    '#536907', -- green-700 (deepened for contrast)
    '#8E6B02', -- yellow-700 (deepened for contrast)
    '#205EA6', -- blue-600
    '#A02F6F', -- magenta-600
    '#1C6C66', -- cyan-700 (deepened for contrast)
    '#575653', -- base-700
  },

  brights = {
    '#6F6E69', -- base-600 (comments)
    '#C03E35', -- red-500
    '#66790D', -- green-600 (deepened for light-theme contrast ~5.3:1)
    '#8E6B02', -- yellow-700 (matches ansi yellow, contrast ~5.1:1)
    '#3171B2', -- blue-500
    '#B74583', -- magenta-500
    '#2F968D', -- cyan-500
    '#403E3C', -- base-800
  },

  scrollbar_thumb = '#C9C2B1',
  split = '#DDDBCF',

  tab_bar = {
    background = '#FFFCF0',
    inactive_tab_edge = '#FFFCF0',

    active_tab = {
      bg_color = '#E8E6DB',
      fg_color = '#100F0F',
      intensity = 'Bold',
      underline = 'None',
      italic = false,
      strikethrough = false,
    },

    inactive_tab = {
      bg_color = '#FFFCF0',
      fg_color = '#4A4946',
      intensity = 'Normal',
    },

    inactive_tab_hover = {
      bg_color = '#E8E6DB',
      fg_color = '#100F0F',
      italic = false,
    },

    new_tab = {
      bg_color = '#FFFCF0',
      fg_color = '#4A4946',
    },

    new_tab_hover = {
      bg_color = '#E8E6DB',
      fg_color = '#100F0F',
    },
  },

  -- Override Claude Code backgrounds for better contrast on cream
  color_overrides = {
    ['#575653'] = '#F2F0EB',  -- ANSI 7 (white): quote background
    ['#585754'] = '#F2F0EB',  -- Claude Code true color variant
    ['#225FA6'] = '#F2F0EB',  -- Claude Code blue header background
    ['#205EA6'] = '#F2F0EB',  -- ANSI 4 (blue bg): selection row
    ['#1C6C66'] = '#F2F0EB',  -- ANSI 6 (cyan bg): branch pill
    ['#536907'] = '#F2F0EB',  -- ANSI 2 (green bg): progress bar blocks
    ['#8E6B02'] = '#F2F0EB',  -- ANSI 3 (yellow bg): agent label background
  },

  -- Override pale agent text that is readable on dark themes but nearly
  -- invisible against Kaku Light's cream background.
  foreground_color_overrides = {
    ['#FFFFDB'] = '#575653',  -- Hermes pale yellow text
    ['#FFFFDC'] = '#575653',  -- Hermes pale yellow text variant
  },
}

config.color_schemes = config.color_schemes or {}
config.color_schemes['Kaku Dark'] = kaku_theme
config.color_schemes['Kaku Light'] = kaku_light
-- Legacy alias for compatibility
config.color_schemes['Kaku Theme'] = kaku_theme
config.color_scheme = resolve_kaku_color_scheme(config.color_scheme)

config.set_environment_variables = config.set_environment_variables or {}
config.set_environment_variables['COLORFGBG'] = (config.color_scheme == 'Kaku Light') and '0;15' or '15;0'

-- ===== Window Frame (theme-aware) =====
get_window_frame_colors = function(scheme)
  scheme = resolve_kaku_color_scheme(scheme)
  if scheme == 'Kaku Light' then
    return {
      active_titlebar_bg = '#FFFCF0',
      inactive_titlebar_bg = '#F8F5EA',
      active_titlebar_fg = '#100F0F',
      inactive_titlebar_fg = '#575653',
      active_titlebar_border_bottom = '#E8E1D0',
      inactive_titlebar_border_bottom = '#EDE6D6',
      border_left_width = 1,
      border_right_width = 1,
      border_top_height = 1,
      border_bottom_height = 1,
      border_left_color = '#E8E1D0',
      border_right_color = '#E8E1D0',
      border_top_color = '#FFFCF0',
      border_bottom_color = '#E8E1D0',
    }
  else
    return {
      active_titlebar_bg = KAKU.BLACK,
      inactive_titlebar_bg = KAKU.BLACK,
      active_titlebar_fg = KAKU.WHITE,
      inactive_titlebar_fg = KAKU.GRAY,
      active_titlebar_border_bottom = KAKU.BLACK,
      inactive_titlebar_border_bottom = KAKU.BLACK,
      border_left_width = 0,
      border_right_width = 0,
      border_top_height = 0,
      border_bottom_height = 0,
      border_left_color = nil,
      border_right_color = nil,
      border_top_color = nil,
      border_bottom_color = nil,
    }
  end
end

if not user_has_custom_window_frame then
  -- Use the user's intended scheme (scanned from their kaku.lua) rather than
  -- config.color_scheme, which has already been resolved against the system
  -- appearance and may not reflect a later override. Without this, on cold
  -- start a Dark-theme user on a Light system gets `#FFFCF0` titlebar/border
  -- colors painted on the first frame, producing a visible light strip at the
  -- top until window-config-reloaded re-runs build_managed_window_frame.
  local initial_scheme = is_user_light_theme() and 'Kaku Light' or 'Kaku Dark'
  local window_frame_colors = get_window_frame_colors(initial_scheme)
  config.window_frame = {
    font = wezterm.font({ family = 'JetBrains Mono', weight = 'Regular' }),
    font_size = 14.0,
    active_titlebar_bg = window_frame_colors.active_titlebar_bg,
    inactive_titlebar_bg = window_frame_colors.inactive_titlebar_bg,
    active_titlebar_fg = window_frame_colors.active_titlebar_fg,
    inactive_titlebar_fg = window_frame_colors.inactive_titlebar_fg,
    active_titlebar_border_bottom = window_frame_colors.active_titlebar_border_bottom,
    inactive_titlebar_border_bottom = window_frame_colors.inactive_titlebar_border_bottom,
    border_left_width = window_frame_colors.border_left_width,
    border_right_width = window_frame_colors.border_right_width,
    border_top_height = window_frame_colors.border_top_height,
    border_bottom_height = window_frame_colors.border_bottom_height,
    border_left_color = window_frame_colors.border_left_color,
    border_right_color = window_frame_colors.border_right_color,
    border_top_color = window_frame_colors.border_top_color,
    border_bottom_color = window_frame_colors.border_bottom_color,
  }
end

-- ===== Shell =====
local user_shell = os.getenv('SHELL')
if user_shell and #user_shell > 0 then
  config.default_prog = { user_shell, '-l' }
else
  config.default_prog = { '/bin/zsh', '-l' }
end

-- ===== macOS Specific =====
-- Keep Left Option as Meta so Alt-based Vim/Neovim keybindings work reliably.
config.send_composed_key_when_left_alt_is_pressed = false
-- Keep Right Option available for composing locale/symbol characters.
config.send_composed_key_when_right_alt_is_pressed = true
config.native_macos_fullscreen_mode = true
config.quit_when_all_windows_are_closed = false

-- ===== Key Bindings =====
-- Wrapped in an IIFE so the ~50-entry table constructor gets its own function
-- scope. The main chunk is near Lua 5.4's register budget, and inlining this
-- table triggered `function or expression needs too many registers near ','`.
config.keys = (function() return {
  -- Window & App
  -- Cmd+K: clear screen + scrollback
  {
    key = 'k',
    mods = 'CMD',
    action = wezterm.action.Multiple({
      wezterm.action.SendKey({ key = 'l', mods = 'CTRL' }),
      wezterm.action.ClearScrollback('ScrollbackAndViewport'),
    }),
  },

  -- Compatibility: keep Cmd+R for existing muscle memory
  {
    key = 'r',
    mods = 'CMD',
    action = wezterm.action.Multiple({
      wezterm.action.SendKey({ key = 'l', mods = 'CTRL' }),
      wezterm.action.ClearScrollback('ScrollbackAndViewport'),
    }),
  },

  -- Cmd+Q: quit
  {
    key = 'q',
    mods = 'CMD',
    action = wezterm.action.QuitApplication,
  },

  -- Cmd+N: new window
  {
    key = 'n',
    mods = 'CMD',
    action = wezterm.action.SpawnWindow,
  },

  -- Close Behavior
  -- Cmd+W: close pane > close tab > hide app
  --
  -- We always pass `confirm = true` so the smart-skip logic in Rust
  -- (`should_confirm`) can run: idle shells (zsh/bash/fish/tmux/...)
  -- close silently, but anything stateful in the pane process tree —
  -- AI agents (claude/codex/cursor-agent/gemini), editors (vim/nano),
  -- builds (cargo/make/npm), long-running scripts — pops a confirm.
  --
  -- Setting `pane_close_confirmation = true` / `tab_close_confirmation = true`
  -- in user config still upgrades to "always ask, even bare zsh".
  {
    key = 'w',
    mods = 'CMD',
    action = wezterm.action_callback(function(win, pane)
      local mux_win = win:mux_window()
      local tabs = mux_win and mux_win:tabs() or {}
      local current_tab = pane:tab()
      local panes = current_tab and current_tab:panes() or {}
      if #panes > 1 then
        win:perform_action(wezterm.action.CloseCurrentPane { confirm = true }, pane)
      else
        local should_close_tab = (#tabs > 1) or (#wezterm.mux.all_windows() > 1)
        if should_close_tab then
          win:perform_action(wezterm.action.CloseCurrentTab { confirm = true }, pane)
          return
        end
        win:perform_action(wezterm.action.HideApplication, pane)
      end
    end),
  },

  -- Cmd+Shift+W: close current tab (same smart-confirm contract as above)
  {
    key = 'w',
    mods = 'CMD|SHIFT',
    action = wezterm.action.CloseCurrentTab({ confirm = true }),
  },

  -- Tabs & Panes
  -- Cmd+T: new tab
  {
    key = 't',
    mods = 'CMD',
    action = wezterm.action.SpawnTab('CurrentPaneDomain'),
  },

  -- Cmd+Shift+G: launch lazygit in current pane
  {
    key = 'G',
    mods = 'CMD|SHIFT',
    action = wezterm.action.EmitEvent('kaku-launch-lazygit'),
  },

  -- Window Controls
  -- Cmd+Ctrl+F: toggle fullscreen
  {
    key = 'f',
    mods = 'CMD|CTRL',
    action = wezterm.action.ToggleFullScreen,
  },

  -- Cmd+M: minimize window
  {
    key = 'm',
    mods = 'CMD',
    action = wezterm.action.Hide,
  },

  -- Cmd+H: hide application
  {
    key = 'h',
    mods = 'CMD',
    action = wezterm.action.HideApplication,
  },

  -- Font Size
  -- Cmd+Equal/Minus/0: adjust font size
  {
    key = '=',
    mods = 'CMD',
    action = wezterm.action.IncreaseFontSize,
  },
  {
    key = '-',
    mods = 'CMD',
    action = wezterm.action.DecreaseFontSize,
  },
  {
    key = '0',
    mods = 'CMD',
    action = wezterm.action.ResetFontSize,
  },

  -- Shell Editing
  -- Alt+Left / Alt+Right: word jump
  {
    key = 'LeftArrow',
    mods = 'OPT',
    action = wezterm.action.SendKey({ key = 'b', mods = 'ALT' }),
  },
  {
    key = 'RightArrow',
    mods = 'OPT',
    action = wezterm.action.SendKey({ key = 'f', mods = 'ALT' }),
  },

  -- Cmd+Left / Cmd+Right: line start/end
  {
    key = 'LeftArrow',
    mods = 'CMD',
    action = wezterm.action.SendKey({ key = 'a', mods = 'CTRL' }),
  },
  {
    key = 'RightArrow',
    mods = 'CMD',
    action = wezterm.action.SendKey({ key = 'e', mods = 'CTRL' }),
  },

  -- Cmd+Backspace: delete to line start
  {
    key = 'Backspace',
    mods = 'CMD',
    action = wezterm.action.SendKey({ key = 'u', mods = 'CTRL' }),
  },

  -- Alt+Backspace: delete word
  {
    key = 'Backspace',
    mods = 'OPT',
    action = wezterm.action.SendKey({ key = 'w', mods = 'CTRL' }),
  },

  -- Layout
  -- Cmd+D: vertical split
  {
    key = 'd',
    mods = 'CMD',
    action = wezterm.action.SplitHorizontal({ domain = 'CurrentPaneDomain' }),
  },

  -- Cmd+Shift+D: horizontal split
  {
    key = 'D',
    mods = 'CMD|SHIFT',
    action = wezterm.action.SplitVertical({ domain = 'CurrentPaneDomain' }),
  },

  -- Cmd+Shift+[ / ]: prev/next tab
  {
    key = '[',
    mods = 'CMD|SHIFT',
    action = wezterm.action.ActivateTabRelative(-1),
  },
  {
    key = ']',
    mods = 'CMD|SHIFT',
    action = wezterm.action.ActivateTabRelative(1),
  },

  -- Pane Navigation
  -- Cmd+Option+Arrow: navigate between splits
  {
    key = 'LeftArrow',
    mods = 'CMD|OPT',
    action = wezterm.action.ActivatePaneDirection('Left'),
  },
  {
    key = 'RightArrow',
    mods = 'CMD|OPT',
    action = wezterm.action.ActivatePaneDirection('Right'),
  },
  {
    key = 'UpArrow',
    mods = 'CMD|OPT',
    action = wezterm.action.ActivatePaneDirection('Up'),
  },
  {
    key = 'DownArrow',
    mods = 'CMD|OPT',
    action = wezterm.action.ActivatePaneDirection('Down'),
  },

  -- Cmd+1~9: switch tab
  {
    key = '1',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(0),
  },
  {
    key = '2',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(1),
  },
  {
    key = '3',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(2),
  },
  {
    key = '4',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(3),
  },
  {
    key = '5',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(4),
  },
  {
    key = '6',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(5),
  },
  {
    key = '7',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(6),
  },
  {
    key = '8',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(7),
  },
  {
    key = '9',
    mods = 'CMD',
    action = wezterm.action.ActivateTab(8),
  },

  -- Command Input
  -- Cmd+Enter / Shift+Enter: newline without execute
  {
    key = 'Enter',
    mods = 'CMD',
    action = wezterm.action.SendString('\n'),
  },
  {
    key = 'Enter',
    mods = 'SHIFT',
    action = wezterm.action.SendString('\n'),
  },

  -- Cmd+Shift+Enter: Toggle Pane Zoom (Maximize active pane)
  {
    key = 'Enter',
    mods = 'CMD|SHIFT',
    action = wezterm.action.TogglePaneZoomState,
  },

  -- Cmd+Shift+S: Toggle split direction (horizontal <-> vertical)
  {
    key = 'S',
    mods = 'CMD|SHIFT',
    action = wezterm.action.TogglePaneSplitDirection,
  },

  -- Cmd+Ctrl+Arrows: Resize panes
  {
    key = 'LeftArrow',
    mods = 'CMD|CTRL',
    action = wezterm.action.AdjustPaneSize { 'Left', 5 },
  },
  {
    key = 'RightArrow',
    mods = 'CMD|CTRL',
    action = wezterm.action.AdjustPaneSize { 'Right', 5 },
  },
  {
    key = 'UpArrow',
    mods = 'CMD|CTRL',
    action = wezterm.action.AdjustPaneSize { 'Up', 5 },
  },
  {
    key = 'DownArrow',
    mods = 'CMD|CTRL',
    action = wezterm.action.AdjustPaneSize { 'Down', 5 },
  },


} end)()

-- ===== Mouse Bindings =====
-- Copy on select (equivalent to Kitty's copy_on_select)
-- config.copy_on_select = false -- uncomment to disable copy and toast on selection
config.mouse_bindings = {
  {
    event = { Up = { streak = 1, button = 'Left' } },
    mods = 'NONE',
    action = wezterm.action.CompleteSelection('ClipboardAndPrimarySelection'),
  },
  {
    event = { Up = { streak = 1, button = 'Left' } },
    mods = 'CMD',
    action = wezterm.action.OpenLinkAtMouseCursor,
  },
}

-- ===== Rendering & Performance =====
config.enable_scroll_bar = false
config.front_end = 'WebGpu'
config.webgpu_power_preference = 'LowPower'
config.animation_fps = 60
config.max_fps = 60
config.status_update_interval = 1000

-- ===== Pane Layout & Focus =====
-- Split pane gap: gutter = 1 + 2*gap cells, giving ~40px padding on each side
config.split_pane_gap = 2

-- Inactive panes: No dimming (consistent background)
config.inactive_pane_hsb = {
  saturation = 1.0,
  brightness = 1.0,
}

-- Prevent accidental clicks when focusing panes
config.swallow_mouse_click_on_pane_focus = true
config.swallow_mouse_click_on_window_focus = true

-- ===== First Run Experience & Config Version Check =====
wezterm.on('gui-startup', function(cmd)
  lazygit_hint_warmup_until_secs = now_secs() + lazygit_hint_startup_grace_secs
  runtime_cwd_warmup_until_secs = now_secs() + runtime_cwd_startup_grace_secs

  local home = os.getenv("HOME")
  local function read_current_config_version()
    local candidates = {
      wezterm.executable_dir:gsub("MacOS/?$", "Resources") .. "/config_version.txt",
      wezterm.executable_dir .. "/../../assets/shell-integration/config_version.txt",
    }

    for _, path in ipairs(candidates) do
      local version_file = io.open(path, "r")
      if version_file then
        local raw = version_file:read("*all")
        version_file:close()

        if raw then
          local version = tonumber(raw:match("%d+"))
          if version then
            return version
          end
        end
      end
    end

    wezterm.log_error("Failed to resolve bundled config version; falling back to v15")
    return 15
  end

  local current_version = read_current_config_version()

  local state_file = home .. "/.config/kaku/state.json"
  local needs_update = false

  local function ensure_state_dir()
    os.execute("mkdir -p " .. home .. "/.config/kaku")
  end

  local function write_state(version, geometry)
    ensure_state_dir()
    local state = {
      config_version = version,
    }
    if geometry and geometry.width and geometry.height then
      state.window_geometry = {
        width = geometry.width,
        height = geometry.height,
      }
    end

    local encoded = nil
    local ok, value = pcall(wezterm.json_encode, state)
    if ok and type(value) == "string" and value ~= "" then
      encoded = value
    end

    local wf = io.open(state_file, "w")
    if wf then
      if encoded then
        wf:write(encoded .. "\n")
      else
        -- Manual JSON fallback when json_encode is unavailable.
        -- Include geometry if present so state is not lost.
        if geometry and geometry.width and geometry.height then
          wf:write(string.format(
            '{\n  "config_version": %d,\n  "window_geometry": {\n    "width": %d,\n    "height": %d\n  }\n}\n',
            version, geometry.width, geometry.height))
        else
          wf:write(string.format('{\n  "config_version": %d\n}\n', version))
        end
      end
      wf:close()
    end
  end

  local user_version = nil
  local state_file_exists = false
  local sf = io.open(state_file, "r")
  if sf then
    state_file_exists = true
    local raw_state = sf:read("*all")
    sf:close()
    if raw_state and raw_state ~= "" then
      local ok, state = pcall(wezterm.json_parse, raw_state)
      if ok and type(state) == "table" then
        user_version = tonumber(state.config_version)
      end
    end
  end

  if not state_file_exists then
    -- Fresh install: write initial state and proceed to normal startup
    write_state(current_version, nil)
    user_version = current_version
  elseif user_version == nil then
    -- Corrupted or manually edited state file: repair with safe defaults.
    write_state(current_version, nil)
    user_version = current_version
  elseif user_version < current_version then
    needs_update = true
  end

  if needs_update then
    -- Apply incremental config updates on version upgrades
    local resource_dir = wezterm.executable_dir:gsub("MacOS/?$", "Resources")
    local update_script = resource_dir .. "/check_config_version.sh"

    -- Fallback for dev environment
    local u_script = io.open(update_script, "r")
    if not u_script then
      update_script = wezterm.executable_dir .. "/../../assets/shell-integration/check_config_version.sh"
    else
      u_script:close()
    end

    wezterm.mux.spawn_window {
      args = { 'bash', update_script },
      width = 106,
      height = 22,
    }
    return
  end

  -- Normal startup
  if not cmd then
    local start_cwd = nil
    if should_remember_last_cwd() then
      local saved = read_last_cwd()
      if saved and saved ~= '' then
        local result = os.execute(string.format('[ -d %q ] 2>/dev/null', saved))
        if result == true or result == 0 then
          start_cwd = saved
        end
      end
    end
    wezterm.mux.spawn_window(start_cwd and { cwd = start_cwd } or {})
  end
end)

return config
