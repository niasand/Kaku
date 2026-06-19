//! Shared helpers used across multiple mux modules.

use std::sync::Mutex;
use termwiz::cell::unicode_column_width;
use termwiz::lineedit::*;

/// Acquire a mutex lock, recovering from poison if necessary.
pub fn recover_lock<T>(lock: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    match lock.lock() {
        Ok(guard) => guard,
        Err(e) => {
            log::warn!("lock poisoned, recovering: {e}");
            e.into_inner()
        }
    }
}

/// Line editor host that optionally obscures input (for password prompts).
///
/// When `echo` is false, typed characters are replaced with a placeholder
/// so the password is not visible in the terminal widget.
#[derive(Default)]
pub struct PasswordPromptHost {
    history: BasicHistory,
    pub echo: bool,
}

impl LineEditorHost for PasswordPromptHost {
    fn history(&mut self) -> &mut dyn History {
        &mut self.history
    }

    fn highlight_line(&self, line: &str, cursor_position: usize) -> (Vec<OutputElement>, usize) {
        if self.echo {
            (vec![OutputElement::Text(line.to_string())], cursor_position)
        } else {
            let placeholder = "🔑";
            let grapheme_count = unicode_column_width(line, None);
            let mut output = vec![];
            for _ in 0..grapheme_count {
                output.push(OutputElement::Text(placeholder.to_string()));
            }
            (
                output,
                unicode_column_width(placeholder, None) * cursor_position,
            )
        }
    }
}
