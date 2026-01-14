fn main() {
    // Embed Windows resources (icon and manifest)
    #[cfg(target_os = "windows")]
    {
        embed_resource::compile("resources/app.rc", embed_resource::NONE);
    }

    // Rerun if resources change
    println!("cargo:rerun-if-changed=resources/splash.png");
    println!("cargo:rerun-if-changed=resources/Zayit.msi");
    println!("cargo:rerun-if-changed=resources/app.rc");
    println!("cargo:rerun-if-changed=resources/app.manifest");
}
