//! Minimal stub for wezterm-bidi.
//!
//! BiDi (bidirectional text) processing has been removed — this crate
//! only provides the type signatures that downstream crates depend on,
//! always resolving to LeftToRight.

use core::ops::Range;
use wezterm_dynamic::{FromDynamic, ToDynamic};

// ---------------------------------------------------------------------------
// Re-exported leaf types (used by config, rendering pipeline)
// ---------------------------------------------------------------------------

mod direction;
mod level;

pub use direction::Direction;
pub use level::Level;

// ---------------------------------------------------------------------------
// ParagraphDirectionHint
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, FromDynamic, ToDynamic)]
pub enum ParagraphDirectionHint {
    LeftToRight,
    RightToLeft,
    AutoLeftToRight,
    AutoRightToLeft,
}

impl Default for ParagraphDirectionHint {
    fn default() -> Self {
        Self::LeftToRight
    }
}

impl ParagraphDirectionHint {
    pub fn direction(self) -> Direction {
        Direction::LeftToRight
    }
}

// ---------------------------------------------------------------------------
// BidiContext — stub that reports everything as a single LTR run
// ---------------------------------------------------------------------------

#[derive(Debug, Default)]
pub struct BidiContext;

impl BidiContext {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_reorder_non_spacing_marks(&mut self, _reorder: bool) {}

    pub fn resolve_paragraph(&mut self, _paragraph: &[char], _hint: ParagraphDirectionHint) {}

    pub fn set_char_types(&mut self, _char_types: &[Level], _hint: ParagraphDirectionHint) {}

    /// Returns a single LTR run covering the whole paragraph.
    pub fn runs(&self) -> impl Iterator<Item = BidiRun> {
        core::iter::empty() // callers should use reordered_runs instead
    }

    /// Returns a single LTR run covering the requested line range.
    pub fn line_runs(&self, _line_range: Range<usize>) -> impl Iterator<Item = BidiRun> {
        core::iter::empty()
    }

    /// Returns a single reordered run in natural (LTR) order.
    pub fn reordered_runs(&self, line_range: Range<usize>) -> Vec<ReorderedRun> {
        let len = line_range.end - line_range.start;
        if len == 0 {
            return vec![];
        }
        vec![ReorderedRun {
            direction: Direction::LeftToRight,
            level: Level(0),
            range: line_range.clone(),
            indices: (line_range.start..line_range.end).collect(),
        }]
    }
}

// ---------------------------------------------------------------------------
// BidiRun / ReorderedRun — kept for API compatibility
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BidiRun {
    pub direction: Direction,
    pub level: Level,
    pub range: Range<usize>,
    pub removed_by_x9: Vec<usize>,
}

impl BidiRun {
    pub fn indices(&self) -> impl Iterator<Item = usize> + '_ {
        self.range.clone()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ReorderedRun {
    pub direction: Direction,
    pub level: Level,
    pub range: Range<usize>,
    pub indices: Vec<usize>,
}
