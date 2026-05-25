fn main() {
    println!("cargo:rerun-if-changed=build.rs");

    use gl_generator::{Api, Fallbacks, Profile, Registry};
    use std::env;
    use std::fs::File;
    use std::path::PathBuf;

    let dest = PathBuf::from(&env::var("OUT_DIR").unwrap());
    let mut file = File::create(dest.join("egl_bindings.rs")).unwrap();
    let reg = Registry::new(
        Api::Egl,
        (1, 5),
        Profile::Core,
        Fallbacks::All,
        [
            "EGL_KHR_create_context",
            "EGL_EXT_create_context_robustness",
            "EGL_KHR_create_context_no_error",
            "EGL_EXT_platform_base",
            "EGL_EXT_platform_device",
            "EGL_KHR_swap_buffers_with_damage",
        ],
    );

    // macOS: dynamic linking; iOS/Android would use static
    reg.write_bindings(gl_generator::StructGenerator, &mut file)
        .unwrap();

    println!("cargo:rustc-link-lib=framework=Carbon");
}
