#!/usr/bin/env bash
# `kaku init` rewrites ~/.zshrc through a temp file. `mv` onto a symlink
# replaces the link with a regular file. These runs keep the link in place
# for both the normalize rewrite and the first-time append.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
source "$SCRIPT_DIR/common.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/kaku-zshrc-symlink.XXXXXX")"
cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

vendor_dir="$tmp_dir/vendor"
create_stub_vendor_dir "$vendor_dir"
mkdir -p "$vendor_dir/zsh-syntax-highlighting"

run_setup() {
  local home="$1"
  local setup_out setup_status
  setup_status=0
  setup_out="$(
    HOME="$home" \
    ZDOTDIR="$home" \
    KAKU_INIT_INTERNAL=1 \
    KAKU_SKIP_TOOL_BOOTSTRAP=1 \
    KAKU_SKIP_TERMINFO_BOOTSTRAP=1 \
    KAKU_VENDOR_DIR="$vendor_dir" \
    bash "$REPO_ROOT/assets/shell-integration/setup_zsh.sh" --update-only 2>&1
  )" || setup_status=$?
  if [[ "$setup_status" -ne 0 ]]; then
    echo "$setup_out" >&2
    fail "setup_zsh.sh exited $setup_status for $home"
  fi
}

# Rewrite path: legacy Kaku lines, chained relative symlink.
rewrite_home="$tmp_dir/rewrite-home"
mkdir -p "$rewrite_home" "$tmp_dir/dotfiles"
cat >"$tmp_dir/dotfiles/zshrc" <<'EOF'
# keep me
[[ ":$PATH:" != *":$HOME/.config/kaku/zsh/bin:"* ]] && export PATH="$HOME/.config/kaku/zsh/bin:$PATH"
[[ -f "$HOME/.config/kaku/zsh/kaku.zsh" ]] && source "$HOME/.config/kaku/zsh/kaku.zsh"
EOF
ln -s ../dotfiles/zshrc "$rewrite_home/.zshrc-link"
ln -s .zshrc-link "$rewrite_home/.zshrc"

run_setup "$rewrite_home"

[[ -L "$rewrite_home/.zshrc" ]] || fail "rewrite: ~/.zshrc is no longer a symlink"
[[ -L "$rewrite_home/.zshrc-link" ]] || fail "rewrite: intermediate symlink was replaced"
[[ "$(readlink "$rewrite_home/.zshrc")" == ".zshrc-link" ]] || fail "rewrite: ~/.zshrc target changed"
[[ "$(readlink "$rewrite_home/.zshrc-link")" == "../dotfiles/zshrc" ]] || fail "rewrite: intermediate target changed"
[[ ! -L "$tmp_dir/dotfiles/zshrc" ]] || fail "rewrite: canonical file became a symlink"
grep -Fqx '# keep me' "$tmp_dir/dotfiles/zshrc" || fail "rewrite: user line was dropped"
grep -F 'Kaku PATH Integration' "$tmp_dir/dotfiles/zshrc" >/dev/null || fail "rewrite: PATH line was not updated in the target"
grep -F 'Kaku Shell Integration' "$tmp_dir/dotfiles/zshrc" >/dev/null || fail "rewrite: source line was not updated in the target"

# Append path: existing file with no Kaku lines. `echo >>` must follow the link.
append_home="$tmp_dir/append-home"
mkdir -p "$append_home" "$tmp_dir/append-real"
printf '%s\n' '# user config' >"$tmp_dir/append-real/zshrc"
ln -s ../append-real/zshrc "$append_home/.zshrc"

run_setup "$append_home"

[[ -L "$append_home/.zshrc" ]] || fail "append: ~/.zshrc is no longer a symlink"
[[ "$(readlink "$append_home/.zshrc")" == "../append-real/zshrc" ]] || fail "append: symlink target changed"
[[ ! -L "$tmp_dir/append-real/zshrc" ]] || fail "append: canonical file became a symlink"
grep -Fqx '# user config' "$tmp_dir/append-real/zshrc" || fail "append: user line was dropped"
grep -F 'Kaku Shell Integration' "$tmp_dir/append-real/zshrc" >/dev/null || fail "append: source line was not written through the symlink"

echo "zshrc symlink smoke tests passed"
