# Zayit Installer

Programme Rust qui installe silencieusement le MSI Zayit tout en affichant un splash screen.

## Prérequis

- Rust toolchain (rustup)
- Target Windows x86_64: `rustup target add x86_64-pc-windows-msvc`

## Préparation des ressources

Avant de compiler, copiez les fichiers nécessaires dans `resources/`:

```powershell
# Copier le splash screen
copy ..\SeforimApp\src\jvmMain\assets\common\splash.png resources\splash.png

# Copier le MSI (après avoir buildé l'app avec Gradle)
copy ..\SeforimApp\build\compose\binaries\main-release\msi\Zayit-1.0.0_x64.msi resources\Zayit.msi
```

## Compilation

```powershell
cargo build --release
```

L'exécutable sera généré dans `target/release/zayit-installer.exe`

## Fonctionnement

1. Affiche le splash screen centré à l'écran
2. Exécute `msiexec /i /qn` en arrière-plan pour installer silencieusement
3. Une fois l'installation terminée, ferme le splash et lance Zayit

## Structure des fichiers

```
installer/
├── Cargo.toml
├── build.rs
├── src/
│   └── main.rs
└── resources/
    ├── app.rc
    ├── app.manifest
    ├── splash.png      # À copier depuis SeforimApp
    └── Zayit.msi       # À copier depuis le build Gradle
```
