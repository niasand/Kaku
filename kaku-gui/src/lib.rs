// Shared library target for kaku-gui: exposes non-GUI modules to the `k` CLI binary.
// GUI-only modules (overlay, termwindow, renderstate, etc.) are not included here.

pub mod thread_util;
