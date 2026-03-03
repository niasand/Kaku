#!/bin/bash
# Kaku - OpenCode Theme Installation Script
# Installs a Kaku-matching color theme for OpenCode

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

OPENCODE_DIR="$HOME/.config/opencode"
THEMES_DIR="$OPENCODE_DIR/themes"
CONFIG_FILE="$OPENCODE_DIR/opencode.json"
THEME_FILE="$THEMES_DIR/kaku-match.json"

echo -e "${BOLD}OpenCode Theme Setup${NC}"
echo -e "${NC}Kaku-matching color palette for OpenCode${NC}"

if [[ -f "$CONFIG_FILE" ]]; then
	read -p "OpenCode config already exists. Overwrite with Kaku theme? [Y/n] " -n 1 -r
	echo
	if [[ $REPLY =~ ^[Nn]$ ]]; then
		echo -e "${YELLOW}Skipped${NC}"
		exit 0
	fi
fi

mkdir -p "$OPENCODE_DIR"
mkdir -p "$THEMES_DIR"

echo -n "  Installing OpenCode theme... "
cat >"$THEME_FILE" <<'THEME_EOF'
{
  "$schema": "https://opencode.ai/theme.json",
  "defs": {
    "dark_bg": "#15141b",
    "dark_panel": "#1f1d28",
    "dark_element": "#29263c",
    "dark_text": "#edecee",
    "dark_muted": "#6d6d6d",
    "dark_primary": "#a277ff",
    "dark_secondary": "#61ffca",
    "dark_accent": "#ffca85",
    "dark_error": "#ff6767",
    "dark_warning": "#ffca85",
    "dark_success": "#61ffca",
    "dark_info": "#5fa8ff",
    "dark_border": "#29263c",
    "dark_border_active": "#3d3a52",
    "dark_border_subtle": "#1f1d28",
    "light_bg": "#FFFCF0",
    "light_panel": "#FAF7EA",
    "light_element": "#F3EEDA",
    "light_text": "#403E3C",
    "light_muted": "#7A7872",
    "light_primary": "#5E3DB3",
    "light_secondary": "#24837B",
    "light_accent": "#9A7400",
    "light_error": "#AF3029",
    "light_warning": "#9A7400",
    "light_success": "#24837B",
    "light_info": "#205EA6",
    "light_border": "#DDD8C8",
    "light_border_active": "#C7C1AE",
    "light_border_subtle": "#ECE7D7"
  },
  "theme": {
    "primary": { "dark": "dark_primary", "light": "light_primary" },
    "secondary": { "dark": "dark_secondary", "light": "light_secondary" },
    "accent": { "dark": "dark_accent", "light": "light_accent" },
    "error": { "dark": "dark_error", "light": "light_error" },
    "warning": { "dark": "dark_warning", "light": "light_warning" },
    "success": { "dark": "dark_success", "light": "light_success" },
    "info": { "dark": "dark_info", "light": "light_info" },
    "text": { "dark": "dark_text", "light": "light_text" },
    "textMuted": { "dark": "dark_muted", "light": "light_muted" },
    "background": { "dark": "dark_bg", "light": "light_bg" },
    "backgroundPanel": { "dark": "dark_panel", "light": "light_panel" },
    "backgroundElement": { "dark": "dark_element", "light": "light_element" },
    "border": { "dark": "dark_border", "light": "light_border" },
    "borderActive": { "dark": "dark_border_active", "light": "light_border_active" },
    "borderSubtle": { "dark": "dark_border_subtle", "light": "light_border_subtle" },
    "diffAdded": { "dark": "dark_success", "light": "light_success" },
    "diffRemoved": { "dark": "dark_error", "light": "light_error" },
    "diffContext": { "dark": "dark_muted", "light": "light_muted" },
    "diffHunkHeader": { "dark": "dark_primary", "light": "light_primary" },
    "diffHighlightAdded": { "dark": "dark_success", "light": "light_success" },
    "diffHighlightRemoved": { "dark": "dark_error", "light": "light_error" },
    "diffAddedBg": { "dark": "#1b2a24", "light": "#EAF4EC" },
    "diffRemovedBg": { "dark": "#2a1b20", "light": "#F8EBEA" },
    "diffContextBg": { "dark": "dark_bg", "light": "light_bg" },
    "diffLineNumber": { "dark": "dark_muted", "light": "light_muted" },
    "diffAddedLineNumberBg": { "dark": "#1b2a24", "light": "#EAF4EC" },
    "diffRemovedLineNumberBg": { "dark": "#2a1b20", "light": "#F8EBEA" },
    "markdownText": { "dark": "dark_text", "light": "light_text" },
    "markdownHeading": { "dark": "dark_primary", "light": "light_primary" },
    "markdownLink": { "dark": "dark_info", "light": "light_info" },
    "markdownLinkText": { "dark": "dark_primary", "light": "light_primary" },
    "markdownCode": { "dark": "dark_accent", "light": "light_accent" },
    "markdownBlockQuote": { "dark": "dark_muted", "light": "light_muted" },
    "markdownEmph": { "dark": "dark_accent", "light": "light_accent" },
    "markdownStrong": { "dark": "dark_secondary", "light": "light_secondary" },
    "markdownHorizontalRule": { "dark": "dark_muted", "light": "light_muted" },
    "markdownListItem": { "dark": "dark_primary", "light": "light_primary" },
    "markdownListEnumeration": { "dark": "dark_accent", "light": "light_accent" },
    "markdownImage": { "dark": "dark_info", "light": "light_info" },
    "markdownImageText": { "dark": "dark_primary", "light": "light_primary" },
    "markdownCodeBlock": { "dark": "dark_text", "light": "light_text" },
    "syntaxComment": { "dark": "dark_muted", "light": "light_muted" },
    "syntaxKeyword": { "dark": "dark_primary", "light": "light_primary" },
    "syntaxFunction": { "dark": "dark_secondary", "light": "light_secondary" },
    "syntaxVariable": { "dark": "dark_text", "light": "light_text" },
    "syntaxString": { "dark": "dark_success", "light": "light_success" },
    "syntaxNumber": { "dark": "dark_accent", "light": "light_accent" },
    "syntaxType": { "dark": "dark_info", "light": "light_info" },
    "syntaxOperator": { "dark": "dark_primary", "light": "light_primary" },
    "syntaxPunctuation": { "dark": "dark_text", "light": "light_text" }
  }
}
THEME_EOF
echo -e "${GREEN}done ✅${NC}"

echo -n "  Writing OpenCode config... "
if [[ -f "$CONFIG_FILE" ]]; then
	# Merge theme into existing config to preserve user settings
	TMPFILE=$(mktemp)
	if command -v python3 &>/dev/null; then
		python3 -c "
import json, sys
try:
    with open('$CONFIG_FILE') as f:
        cfg = json.load(f)
except (json.JSONDecodeError, FileNotFoundError):
    cfg = {}
cfg['theme'] = 'kaku-match'
with open('$TMPFILE', 'w') as f:
    json.dump(cfg, f, indent=2)
" && mv "$TMPFILE" "$CONFIG_FILE"
	else
		# Fallback: overwrite if python3 not available
		rm -f "$TMPFILE"
		cat >"$CONFIG_FILE" <<'CONFIG_EOF'
{
  "theme": "kaku-match"
}
CONFIG_EOF
	fi
else
	cat >"$CONFIG_FILE" <<'CONFIG_EOF'
{
  "theme": "kaku-match"
}
CONFIG_EOF
fi
echo -e "${GREEN}done ✅${NC}"

echo ""
echo -e "${GREEN}${BOLD}✓ OpenCode theme configured successfully!${NC}"
echo ""
