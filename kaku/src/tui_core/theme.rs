use config::configuration;
use ratatui::style::Color;
use std::sync::Mutex;
use wezterm_term::color::SrgbaTuple;

#[derive(Clone, Copy)]
struct Theme {
    primary: Color,
    secondary: Color,
    accent: Color,
    error: Color,
    text: Color,
    muted: Color,
    bg: Color,
    panel: Color,
}

static THEME_CACHE: Mutex<Option<(usize, Theme)>> = Mutex::new(None);

fn theme_from_palette(palette: &crate::kaku_theme::ThemePalette) -> Theme {
    // Derive panel from bg+text blend so popups have enough contrast vs the
    // Preserve the existing background formula regardless of external tool integrations.
    let panel_blend = if palette.is_light { 0.05 } else { 0.08 };
    let panel = crate::kaku_theme::blend(palette.bg, palette.text, panel_blend);

    Theme {
        primary: to_color(palette.primary),
        secondary: to_color(palette.secondary),
        accent: to_color(palette.accent),
        error: to_color(palette.error),
        text: to_color(palette.text),
        muted: to_color(palette.muted),
        bg: to_color(palette.bg),
        panel: to_color(panel),
    }
}

fn current_theme() -> Theme {
    let config = configuration();
    let generation = config.generation();

    let mut cached = match THEME_CACHE.lock() {
        Ok(guard) => guard,
        Err(e) => {
            log::warn!("THEME_CACHE lock poisoned, recovering: {e}");
            e.into_inner()
        }
    };
    if let Some((cached_generation, theme)) = *cached {
        if cached_generation == generation {
            return theme;
        }
    }

    let palette = crate::kaku_theme::current_theme_palette();
    let theme = theme_from_palette(&palette);
    *cached = Some((generation, theme));
    theme
}

pub fn primary() -> Color {
    current_theme().primary
}

pub fn success() -> Color {
    current_theme().secondary
}

pub fn accent() -> Color {
    current_theme().accent
}

pub fn red() -> Color {
    current_theme().error
}

pub fn text_fg() -> Color {
    current_theme().text
}

pub fn muted() -> Color {
    current_theme().muted
}

pub fn bg() -> Color {
    current_theme().bg
}

pub fn panel() -> Color {
    current_theme().panel
}
