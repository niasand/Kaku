#!/usr/bin/env python3
"""Apply a Kaku/WezTerm color scheme to ~/.config/kaku/kaku.lua.

Usage:
    python3 scripts/apply_kaku_theme.py "Tokyo Night"      # apply
    python3 scripts/apply_kaku_theme.py "Tokyo Night" -n   # dry-run (no write)

- Validates the name against config/src/scheme_data.rs (1001 schemes)
- Backs up kaku.lua to kaku.lua.bak before editing
- Rewrites the active (non-commented) config.color_scheme assignment;
  inserts one before `return config` if none exists
- kaku hot-reloads the file on save (no restart needed)
"""
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEME_FILE = ROOT / "config" / "src" / "scheme_data.rs"
KAKU_LUA = Path.home() / ".config" / "kaku" / "kaku.lua"

# Matches an active assignment (commented lines start with `--`, so the leading
# `\s*config` anchor never matches them — they are safely skipped).
ASSIGN_RE = re.compile(r'^(\s*config\.color_scheme\s*=\s*)(["\'])(.*?)\2\s*$',
                       re.MULTILINE)


def valid_schemes():
    data = SCHEME_FILE.read_text(encoding="utf-8", errors="replace")
    names = {m.group(1) for ln in data.splitlines()
             if (m := re.match(r'^\("([^"]*)",', ln))}
    return names


def quote(name):
    return '"' + name + '"' if "'" in name else "'" + name + "'"


def apply(name, dry_run=False):
    valid = valid_schemes()
    if name not in valid:
        print(f"✗ '{name}' 不在已知主题列表（共 {len(valid)} 个）", file=sys.stderr)
        close = sorted(n for n in valid if name.lower() in n.lower())[:8]
        if close:
            print("你是不是要找：", file=sys.stderr)
            for c in close:
                print(f"    {c}", file=sys.stderr)
        sys.exit(1)

    if not KAKU_LUA.exists():
        print(f"✗ 找不到 {KAKU_LUA}", file=sys.stderr)
        sys.exit(1)

    text = KAKU_LUA.read_text(encoding="utf-8")
    new_line = f"config.color_scheme = {quote(name)}"
    matches = list(ASSIGN_RE.finditer(text))

    if matches:
        m = matches[-1]  # last assignment wins in Lua
        old = m.group(0).strip()
        result = text[:m.start()] + new_line + text[m.end():]
        action = f"替换现有赋值：{old}"
    else:
        ret = re.search(r'^return\s+config\s*$', text, re.MULTILINE)
        if ret:
            result = text[:ret.start()] + new_line + "\n\n" + text[ret.start():]
        else:
            result = text.rstrip() + "\n" + new_line + "\n"
        action = "新增（原文件无 color_scheme 赋值）"

    if dry_run:
        print(f"[dry-run] 目标文件：{KAKU_LUA}")
        print(f"[dry-run] 操作    ：{action}")
        print(f"[dry-run] 新值    ：{new_line}")
        return

    backup = KAKU_LUA.with_suffix(".lua.bak")
    shutil.copy2(KAKU_LUA, backup)
    KAKU_LUA.write_text(result, encoding="utf-8")
    print(f"✓ 已应用：{new_line}")
    print(f"  操作：{action}")
    print(f"  备份：{backup}")
    print(f"  kaku 将自动热加载（无需重启）")


if __name__ == "__main__":
    args = sys.argv[1:]
    dry_run = "-n" in args or "--dry-run" in args
    args = [a for a in args if a not in ("-n", "--dry-run")]
    if len(args) != 1:
        print('用法：python3 scripts/apply_kaku_theme.py "<主题名>" [-n|--dry-run]',
              file=sys.stderr)
        sys.exit(2)
    apply(args[0], dry_run)
