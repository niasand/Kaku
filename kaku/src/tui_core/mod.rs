pub mod components;
pub mod form;
pub mod theme;

use crossterm::event::Event;
use ratatui::layout::Rect;
use ratatui::Frame;

pub enum EventResult {
    Ignored,
    Consumed,
    Changed,
    Exit,
}

pub trait Widget {
    fn render(&mut self, frame: &mut Frame, area: Rect);
    fn handle_event(&mut self, event: &Event) -> EventResult;
}
