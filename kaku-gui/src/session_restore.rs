use crate::termwindow::TermWindowNotif;
use crate::{frontend, TermWindow};
use anyhow::{anyhow, Context};
use config::GuiPosition;
use mux::pane::{Pane, PaneId};
use mux::tab::{PaneEntry, PaneNode, SerdeUrl, SplitDirectionAndSize, Tab, TabId};
use mux::window::WindowId as MuxWindowId;
use mux::Mux;
use parking_lot::Mutex;
use promise::spawn::spawn;
use serde::{Deserialize, Serialize};
use smol::Timer;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, OnceLock};
use std::time::Duration;
use wezterm_term::TerminalSize;
use wezterm_toast_notification::persistent_toast_notification;
use window::WindowOps;

const SNAPSHOT_VERSION: u32 = 3;
const LEGACY_SNAPSHOT_VERSION: u32 = 2;
const SAVE_DEBOUNCE: Duration = Duration::from_millis(500);

/// On-disk session. One document holds every live window so a save from
/// window B cannot replace the snapshot of window A.
#[derive(Debug, Serialize, Deserialize, PartialEq)]
struct SavedSessionDocument {
    version: u32,
    windows: Vec<SavedWindowSnapshot>,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Clone)]
struct SavedWindowSnapshot {
    active_tab_idx: usize,
    window_title: String,
    tabs: Vec<SavedTabSnapshot>,
}

/// Version 2 wrote a single window object to `last_window_session.json`.
#[derive(Debug, Deserialize)]
struct LegacySavedWindowSnapshot {
    version: u32,
    active_tab_idx: usize,
    window_title: String,
    tabs: Vec<SavedTabSnapshot>,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Clone)]
struct SavedTabSnapshot {
    title: String,
    pane_tree: SavedPaneNode,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Clone)]
enum SavedPaneNode {
    Empty,
    Split {
        left: Box<SavedPaneNode>,
        right: Box<SavedPaneNode>,
        node: SplitDirectionAndSize,
    },
    Leaf(SavedPaneEntry),
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct SavedPaneEntry {
    window_id: MuxWindowId,
    tab_id: TabId,
    pane_id: PaneId,
    title: String,
    size: TerminalSize,
    working_dir: Option<SerdeUrl>,
    domain_name: String,
    is_active_pane: bool,
    is_zoomed_pane: bool,
    workspace: String,
}

impl SavedPaneNode {
    fn is_empty(&self) -> bool {
        matches!(self, Self::Empty)
    }

    fn join_split(left: Self, right: Self, node: SplitDirectionAndSize) -> Self {
        match (left.is_empty(), right.is_empty()) {
            (true, true) => Self::Empty,
            (true, false) => right,
            (false, true) => left,
            (false, false) => Self::Split {
                left: Box::new(left),
                right: Box::new(right),
                node,
            },
        }
    }

    /// A missing pane or domain used to fail the whole window via `?`.
    /// Skip that leaf and keep the rest of the tree.
    fn from_live(node: PaneNode, mux: &Mux) -> Self {
        match node {
            PaneNode::Empty => Self::Empty,
            PaneNode::Split { left, right, node } => {
                let left = Self::from_live(*left, mux);
                let right = Self::from_live(*right, mux);
                Self::join_split(left, right, node)
            }
            PaneNode::Leaf(entry) => match SavedPaneEntry::from_live(entry, mux) {
                Ok(saved) => Self::Leaf(saved),
                Err(err) => {
                    log::warn!("skipping pane while saving session: {err:#}");
                    Self::Empty
                }
            },
        }
    }

    fn root_size(&self) -> Option<TerminalSize> {
        match self {
            Self::Empty => None,
            Self::Split { node, .. } => Some(node.size()),
            Self::Leaf(entry) => Some(entry.size),
        }
    }

    fn into_pane_node(self) -> PaneNode {
        match self {
            Self::Empty => PaneNode::Empty,
            Self::Split { left, right, node } => PaneNode::Split {
                left: Box::new(left.into_pane_node()),
                right: Box::new(right.into_pane_node()),
                node,
            },
            Self::Leaf(entry) => PaneNode::Leaf(entry.into_pane_entry()),
        }
    }
}

impl SavedPaneEntry {
    fn from_live(entry: PaneEntry, mux: &Mux) -> anyhow::Result<Self> {
        let pane = mux
            .get_pane(entry.pane_id)
            .ok_or_else(|| anyhow!("pane {} not found while building snapshot", entry.pane_id))?;
        let domain = mux.get_domain(pane.domain_id()).ok_or_else(|| {
            anyhow!(
                "domain {} not found while building snapshot for pane {}",
                pane.domain_id(),
                entry.pane_id
            )
        })?;

        Ok(Self {
            window_id: entry.window_id,
            tab_id: entry.tab_id,
            pane_id: entry.pane_id,
            title: entry.title,
            size: entry.size,
            working_dir: entry.working_dir,
            domain_name: domain.domain_name().to_string(),
            is_active_pane: entry.is_active_pane,
            is_zoomed_pane: entry.is_zoomed_pane,
            workspace: entry.workspace,
        })
    }

    fn into_pane_entry(self) -> PaneEntry {
        PaneEntry {
            window_id: self.window_id,
            tab_id: self.tab_id,
            pane_id: self.pane_id,
            title: self.title,
            size: self.size,
            working_dir: self.working_dir,
            is_active_pane: self.is_active_pane,
            is_zoomed_pane: self.is_zoomed_pane,
            workspace: self.workspace,
            cursor_pos: Default::default(),
            physical_top: 0,
            top_row: 0,
            left_col: 0,
            tty_name: None,
        }
    }
}

fn config_dir_file(name: &str) -> PathBuf {
    config::CONFIG_DIRS
        .first()
        .cloned()
        .unwrap_or_else(|| config::HOME_DIR.join(".config").join("kaku"))
        .join(name)
}

fn snapshot_file() -> PathBuf {
    config_dir_file("last_window_session.json")
}

fn previous_snapshot_file() -> PathBuf {
    config_dir_file("previous_window_session.json")
}

/// The live session file is rewritten as soon as a window exists, including a
/// single-pane window. Copy it once per process before that rewrite so
/// Restore Previous Window still opens the layout from the last run.
fn preserve_previous_snapshot_once() {
    static PRESERVED: OnceLock<()> = OnceLock::new();
    PRESERVED.get_or_init(|| {
        let current = snapshot_file();
        if !current.exists() {
            return;
        }
        let previous = previous_snapshot_file();
        if let Some(parent) = previous.parent() {
            if let Err(err) = config::create_user_owned_dirs(parent) {
                log::warn!(
                    "failed to create session dir while preserving previous snapshot: {err:#}"
                );
                return;
            }
        }
        let tmp = previous.with_file_name(format!(
            "{}.{}.tmp",
            previous.file_stem().unwrap_or_default().to_string_lossy(),
            std::process::id()
        ));
        let copied = std::fs::copy(&current, &tmp).and_then(|_| std::fs::rename(&tmp, &previous));
        if let Err(err) = copied {
            let _ = std::fs::remove_file(&tmp);
            log::warn!(
                "failed to preserve previous session snapshot {} -> {}: {err}",
                current.display(),
                previous.display()
            );
        }
    });
}

fn session_write_lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

fn collect_leaf_entries(node: &SavedPaneNode, out: &mut Vec<SavedPaneEntry>) {
    match node {
        SavedPaneNode::Empty => {}
        SavedPaneNode::Split { left, right, .. } => {
            collect_leaf_entries(left, out);
            collect_leaf_entries(right, out);
        }
        SavedPaneNode::Leaf(entry) => out.push(entry.clone()),
    }
}

fn cwd_from_working_dir(working_dir: Option<&SerdeUrl>) -> Option<String> {
    let url = working_dir?;
    if url.url.scheme() != "file" {
        return None;
    }
    url.url
        .to_file_path()
        .ok()
        .map(|path| path.to_string_lossy().into_owned())
}

fn focused_window_id() -> Option<MuxWindowId> {
    frontend::try_front_end()
        .and_then(|fe| fe.focused_mux_window_id())
        .or_else(|| {
            let mux = Mux::get();
            let mut windows = mux.iter_windows();
            windows.sort();
            windows.pop()
        })
}

fn build_snapshot_for_window(window_id: MuxWindowId) -> Option<SavedWindowSnapshot> {
    let mux = Mux::get();
    let window = mux.get_window(window_id)?;
    if window.is_empty() {
        return None;
    }

    let selected = window.get_active_idx();
    let mut active_tab_idx = 0;
    let mut tabs = Vec::new();
    for (idx, tab) in window.iter().enumerate() {
        let pane_tree = SavedPaneNode::from_live(tab.codec_pane_tree(), &mux);
        if pane_tree.is_empty() {
            log::warn!("skipping tab in window {window_id}: no restorable panes");
            continue;
        }
        if idx == selected {
            active_tab_idx = tabs.len();
        }
        tabs.push(SavedTabSnapshot {
            title: tab.get_title(),
            pane_tree,
        });
    }

    if tabs.is_empty() {
        return None;
    }

    Some(SavedWindowSnapshot {
        active_tab_idx,
        window_title: window.get_title().to_string(),
        tabs,
    })
}

fn write_json_atomic(file_name: &Path, value: &impl Serialize) -> anyhow::Result<()> {
    if let Some(parent) = file_name.parent() {
        config::create_user_owned_dirs(parent)
            .with_context(|| format!("create snapshot dir {}", parent.display()))?;
    }

    let encoded = serde_json::to_string_pretty(value).context("encode window snapshot")?;
    // Atomic write: a crash mid-write would otherwise leave a truncated JSON
    // file that fails to parse on the next launch. Write to a sibling temp
    // file and rename on top, which is atomic on POSIX.
    let tmp = file_name.with_file_name(format!(
        "{}.{}.{}.tmp",
        file_name.file_stem().unwrap_or_default().to_string_lossy(),
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0)
    ));
    std::fs::write(&tmp, format!("{encoded}\n"))
        .with_context(|| format!("write {}", tmp.display()))?;
    std::fs::rename(&tmp, &file_name)
        .with_context(|| format!("rename {} -> {}", tmp.display(), file_name.display()))?;
    Ok(())
}

fn write_session(windows: &[SavedWindowSnapshot]) -> anyhow::Result<()> {
    write_json_atomic(
        &snapshot_file(),
        &SavedSessionDocument {
            version: SNAPSHOT_VERSION,
            windows: windows.to_vec(),
        },
    )
}

pub fn save_window_snapshot(_window_id: MuxWindowId) -> anyhow::Result<()> {
    let _guard = session_write_lock().lock();
    preserve_previous_snapshot_once();

    let mux = Mux::get();
    let mut ids = mux.iter_windows();
    ids.sort();
    let mut windows = Vec::new();
    for id in ids {
        if let Some(snapshot) = build_snapshot_for_window(id) {
            windows.push(snapshot);
        }
    }

    write_session(&windows)
}

pub fn save_focused_window_snapshot() -> anyhow::Result<()> {
    let Some(window_id) = focused_window_id() else {
        return Ok(());
    };

    save_window_snapshot(window_id)
}

fn debounce_state() -> &'static Mutex<HashMap<MuxWindowId, u64>> {
    static STATE: OnceLock<Mutex<HashMap<MuxWindowId, u64>>> = OnceLock::new();
    STATE.get_or_init(|| Mutex::new(HashMap::new()))
}

pub fn request_save_window_snapshot(window_id: MuxWindowId) {
    let generation = {
        let mut map = debounce_state().lock();
        let entry = map.entry(window_id).or_insert(0);
        *entry = entry.wrapping_add(1);
        *entry
    };

    spawn(async move {
        Timer::after(SAVE_DEBOUNCE).await;
        let latest = debounce_state()
            .lock()
            .get(&window_id)
            .copied()
            .unwrap_or(0);
        if latest != generation {
            return;
        }
        if let Err(err) = save_window_snapshot(window_id) {
            log::debug!("failed to save window snapshot for {window_id}: {err:#}");
        }
    })
    .detach();
}

/// Reads the on-disk snapshot.
///
/// Returns `Ok(None)` when there is nothing usable to restore (no file, an
/// older/newer version we don't recognize, a corrupt JSON, or an empty tab
/// list). The menu treats this as a soft "nothing to restore" instead of
/// surfacing a noisy error toast — version bumps would otherwise greet every
/// upgrading user with a failure.
///
/// `Err` is reserved for unexpected I/O errors that the user should know
/// about (e.g. permission denied).
fn usable_windows(windows: Vec<SavedWindowSnapshot>) -> Option<Vec<SavedWindowSnapshot>> {
    let windows: Vec<_> = windows
        .into_iter()
        .filter(|window| !window.tabs.is_empty())
        .collect();
    if windows.is_empty() {
        None
    } else {
        Some(windows)
    }
}

fn load_windows_from_path(file_name: &Path) -> anyhow::Result<Option<Vec<SavedWindowSnapshot>>> {
    let contents = match std::fs::read_to_string(file_name) {
        Ok(s) => s,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(err) => {
            return Err(anyhow::Error::new(err).context(format!("read {}", file_name.display())));
        }
    };

    if let Ok(document) = serde_json::from_str::<SavedSessionDocument>(&contents) {
        if document.version != SNAPSHOT_VERSION {
            log::warn!(
                "ignoring window snapshot at {} with unsupported version {} (expected {})",
                file_name.display(),
                document.version,
                SNAPSHOT_VERSION
            );
            return Ok(None);
        }
        return Ok(usable_windows(document.windows));
    }

    if let Ok(legacy) = serde_json::from_str::<LegacySavedWindowSnapshot>(&contents) {
        if legacy.version != LEGACY_SNAPSHOT_VERSION {
            log::warn!(
                "ignoring window snapshot at {} with unsupported version {} (expected {})",
                file_name.display(),
                legacy.version,
                SNAPSHOT_VERSION
            );
            return Ok(None);
        }
        return Ok(usable_windows(vec![SavedWindowSnapshot {
            active_tab_idx: legacy.active_tab_idx,
            window_title: legacy.window_title,
            tabs: legacy.tabs,
        }]));
    }

    log::warn!(
        "ignoring corrupt window snapshot at {}",
        file_name.display()
    );
    Ok(None)
}

fn load_snapshot() -> anyhow::Result<Option<Vec<SavedWindowSnapshot>>> {
    // Seed `previous_window_session.json` from the last run before any save
    // in this process rewrites `last_window_session.json`. Restore reads only
    // the previous file, so opening a new window cannot duplicate the live
    // session or erase the layout from the last run.
    preserve_previous_snapshot_once();
    load_windows_from_path(&previous_snapshot_file())
}

async fn spawn_panes_for_tab(
    root: &SavedPaneNode,
) -> anyhow::Result<HashMap<PaneId, Arc<dyn Pane>>> {
    let mux = Mux::get();
    let encoding = config::configuration().default_encoding;
    let mut entries = Vec::new();
    collect_leaf_entries(root, &mut entries);

    let mut panes = HashMap::new();
    for entry in entries {
        let domain = mux
            .get_domain_by_name(&entry.domain_name)
            .ok_or_else(|| anyhow!("snapshot domain `{}` is not available", entry.domain_name))?;
        let pane = domain
            .spawn_pane(
                &mux,
                entry.size,
                None,
                cwd_from_working_dir(entry.working_dir.as_ref()),
                encoding,
            )
            .await
            .with_context(|| {
                format!(
                    "spawn pane for snapshot pane {} in domain `{}`",
                    entry.pane_id, entry.domain_name
                )
            })?;
        panes.insert(entry.pane_id, pane);
    }

    Ok(panes)
}

async fn get_existing_terminal_size() -> Option<TerminalSize> {
    let window = frontend::try_front_end()?
        .gui_windows()
        .into_iter()
        .next()
        .map(|w| w.window.clone())?;
    let (tx, rx) = smol::channel::bounded::<TerminalSize>(1);
    window.notify(TermWindowNotif::Apply(Box::new(
        move |tw: &mut TermWindow| {
            let _ = tx.try_send(tw.get_terminal_size());
        },
    )));
    rx.recv().await.ok()
}

async fn restore_snapshot(snapshot: SavedWindowSnapshot) -> anyhow::Result<()> {
    let mux = Mux::get();
    let SavedWindowSnapshot {
        active_tab_idx,
        window_title,
        tabs,
    } = snapshot;
    let workspace = mux.active_workspace();

    let builder = mux.new_empty_window(Some(workspace), None::<GuiPosition>);
    let window_id = *builder;

    let actual_size = get_existing_terminal_size().await;

    let restore_result = async {
        for saved_tab in tabs {
            let size = saved_tab.pane_tree.root_size().unwrap_or_default();
            let tab = Arc::new(Tab::new(&size));
            let panes = spawn_panes_for_tab(&saved_tab.pane_tree).await?;
            let pane_tree = saved_tab.pane_tree.into_pane_node();

            tab.try_sync_with_pane_tree(size, pane_tree, |entry| {
                panes
                    .get(&entry.pane_id)
                    .cloned()
                    .ok_or_else(|| anyhow!("missing restored pane {}", entry.pane_id))
            })?;

            if !saved_tab.title.is_empty() {
                tab.set_title(&saved_tab.title);
            }

            mux.add_tab_no_panes(&tab);
            mux.add_tab_to_window(&tab, window_id)?;

            if let Some(s) = actual_size {
                tab.resize(s);
            }
        }

        if let Some(mut window) = mux.get_window_mut(window_id) {
            if !window_title.is_empty() {
                window.set_title(&window_title);
            }
            if window.len() > 0 {
                let max_idx = window.len() - 1;
                window.set_active_without_saving(active_tab_idx.min(max_idx));
            }
        }

        Ok::<(), anyhow::Error>(())
    }
    .await;

    match restore_result {
        Ok(()) => {
            drop(builder);
            Ok(())
        }
        Err(err) => {
            builder.cancel();
            Err(err)
        }
    }
}

#[allow(dead_code)]
pub fn restore_previous_window_from_menu() {
    spawn(async move {
        let result = async {
            match load_snapshot()? {
                Some(windows) => {
                    let mut restored = 0usize;
                    let mut first_err = None;
                    for snapshot in windows {
                        match restore_snapshot(snapshot).await {
                            Ok(()) => restored += 1,
                            Err(err) => {
                                log::warn!("failed to restore a window from session: {err:#}");
                                if first_err.is_none() {
                                    first_err = Some(err);
                                }
                            }
                        }
                    }
                    if restored > 0 {
                        Ok::<bool, anyhow::Error>(true)
                    } else if let Some(err) = first_err {
                        Err(err)
                    } else {
                        Ok(false)
                    }
                }
                None => Ok(false),
            }
        }
        .await;

        match result {
            Ok(true) => {}
            Ok(false) => {
                persistent_toast_notification(
                    "Restore Previous Window",
                    "No previous window snapshot is available to restore.",
                );
            }
            Err(err) => {
                log::warn!("failed to restore previous window: {err:#}");
                persistent_toast_notification("Restore Previous Window", &format!("{err:#}"));
            }
        }
    })
    .detach();
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_window(title: &str) -> SavedWindowSnapshot {
        SavedWindowSnapshot {
            active_tab_idx: 0,
            window_title: title.to_string(),
            tabs: vec![SavedTabSnapshot {
                title: format!("{title} tab"),
                pane_tree: SavedPaneNode::Empty,
            }],
        }
    }

    fn sample_document(windows: Vec<SavedWindowSnapshot>) -> SavedSessionDocument {
        SavedSessionDocument {
            version: SNAPSHOT_VERSION,
            windows,
        }
    }

    #[test]
    fn snapshot_round_trips_every_window() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("last_window_session.json");
        let document = sample_document(vec![sample_window("One"), sample_window("Two")]);
        write_json_atomic(&path, &document).unwrap();

        let loaded = load_windows_from_path(&path).unwrap().expect("snapshot");
        assert_eq!(loaded.len(), 2);
        assert_eq!(loaded[0].window_title, "One");
        assert_eq!(loaded[1].window_title, "Two");
        assert_eq!(loaded[0].tabs[0].title, "One tab");
        assert_eq!(loaded[1].active_tab_idx, 0);
    }

    #[test]
    fn single_pane_window_is_kept_in_the_document() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("last_window_session.json");
        write_json_atomic(&path, &sample_document(vec![sample_window("Solo")])).unwrap();

        let loaded = load_windows_from_path(&path).unwrap().expect("snapshot");
        assert_eq!(loaded.len(), 1);
        assert_eq!(loaded[0].tabs.len(), 1);
    }

    #[test]
    fn legacy_v2_snapshot_loads_as_one_window() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("last_window_session.json");
        std::fs::write(
            &path,
            r#"{"version":2,"active_tab_idx":1,"window_title":"Old","tabs":[{"title":"T","pane_tree":"Empty"}]}"#,
        )
        .unwrap();

        let loaded = load_windows_from_path(&path).unwrap().expect("legacy");
        assert_eq!(loaded.len(), 1);
        assert_eq!(loaded[0].window_title, "Old");
        assert_eq!(loaded[0].active_tab_idx, 1);
        assert_eq!(loaded[0].tabs[0].title, "T");
    }

    #[test]
    fn corrupt_snapshot_is_ignored() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("last_window_session.json");
        std::fs::write(&path, "{not json").unwrap();

        assert!(load_windows_from_path(&path).unwrap().is_none());
    }

    #[test]
    fn unsupported_snapshot_version_is_ignored() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("last_window_session.json");
        let mut document = sample_document(vec![sample_window("Future")]);
        document.version = SNAPSHOT_VERSION + 1;
        std::fs::write(&path, serde_json::to_string(&document).unwrap()).unwrap();

        assert!(load_windows_from_path(&path).unwrap().is_none());
    }

    #[test]
    fn empty_session_is_ignored() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("last_window_session.json");
        write_json_atomic(&path, &sample_document(vec![])).unwrap();

        assert!(load_windows_from_path(&path).unwrap().is_none());
    }

    #[test]
    fn skipped_side_of_a_split_keeps_the_other_pane() {
        let node = SplitDirectionAndSize {
            direction: mux::tab::SplitDirection::Vertical,
            first: TerminalSize::default(),
            second: TerminalSize::default(),
        };
        let leaf = SavedPaneNode::Leaf(SavedPaneEntry {
            window_id: 1,
            tab_id: TabId::new(2),
            pane_id: PaneId::new(3),
            title: "kept".to_string(),
            size: TerminalSize::default(),
            working_dir: None,
            domain_name: "local".to_string(),
            is_active_pane: true,
            is_zoomed_pane: false,
            workspace: "default".to_string(),
        });

        match SavedPaneNode::join_split(SavedPaneNode::Empty, leaf.clone(), node) {
            SavedPaneNode::Leaf(entry) => assert_eq!(entry.title, "kept"),
            other => panic!("expected the surviving pane, got {other:?}"),
        }
        assert!(
            SavedPaneNode::join_split(SavedPaneNode::Empty, SavedPaneNode::Empty, node).is_empty()
        );
        assert!(matches!(
            SavedPaneNode::join_split(leaf.clone(), leaf, node),
            SavedPaneNode::Split { .. }
        ));
    }
}
