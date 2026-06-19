use crate::direction::Direction;

#[derive(Default, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub struct Level(pub i8);

impl Level {
    pub fn direction(self) -> Direction {
        Direction::with_level(self.0)
    }

    pub fn removed_by_x9(self) -> bool {
        false
    }

    pub fn max(self, other: Level) -> Level {
        Level(self.0.max(other.0))
    }
}
