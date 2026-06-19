# CLI Reference

Run `kaku` in your terminal to see all available commands.

## kaku

When run without arguments (and stdin is a terminal), Kaku shows an interactive menu:

```
➤ 1. config     Manage terminal settings
  2. init       Initialize shell integration
  3. doctor     Run diagnostics for shell and runtime health
  4. reset      Remove Kaku shell integration and managed defaults
```

Use arrow keys, number keys (1-4), or the first letter of each command to select. Press Enter to confirm, Q or Esc to quit.

## kaku start

Launch the Kaku GUI terminal. This is the default when `kaku` is run non-interactively (e.g. from scripts or when stdin is not a terminal).

```bash
kaku start            # launch Kaku GUI
kaku                  # equivalent if stdin is not a tty
```

## kaku config

Open the Kaku configuration file (`~/.config/kaku/kaku.lua`) in your default editor. Also accessible from the settings panel with `Cmd + ,`.

```bash
kaku config
```

## kaku init

Set up Kaku's shell integration for zsh and/or fish. Creates `~/.config/kaku/zsh/kaku.zsh` and optionally `~/.config/kaku/fish/kaku.fish`. Also installs optional CLI tools (Starship, Delta, Lazygit) via Homebrew.

```bash
kaku init
```

If the `kaku` command goes missing from your shell, restore it with:

```bash
/Applications/Kaku.app/Contents/MacOS/kaku init --update-only
exec zsh -l
```

## kaku doctor

Run diagnostics and verify that Kaku's shell integration, PATH entries, and optional tool installations are healthy. Use this first if something feels broken.

```bash
kaku doctor
```

## kaku reset

Reset Kaku's config and state files to defaults. Use with caution, this overwrites `~/.config/kaku/kaku.lua`.

```bash
kaku reset
```
