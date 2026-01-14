@echo off
setlocal enabledelayedexpansion

echo === Zayit Installer Build Script ===
echo.

cd /d "%~dp0"

:: Create resources directory if it doesn't exist
if not exist "resources" mkdir resources

:: Copy splash image
echo Copying splash.png...
copy /Y "..\SeforimApp\src\jvmMain\assets\common\splash.png" "resources\splash.png" >nul
if errorlevel 1 (
    echo ERROR: Failed to copy splash.png
    exit /b 1
)
echo OK

:: Copy MSI and get its name
echo Copying MSI...
set "MSI_PATH=..\SeforimApp\build\compose\binaries\main-release\msi"
set "MSI_NAME="

:: Find the MSI file (handle version in filename)
for %%f in ("%MSI_PATH%\Zayit-*_x64.msi") do (
    set "MSI_NAME=%%~nf"
    copy /Y "%%f" "resources\Zayit.msi" >nul
    goto :msi_copied
)

echo ERROR: MSI not found in %MSI_PATH%
echo Run: gradlew :SeforimApp:packageReleaseMsi
exit /b 1

:msi_copied
echo OK - Found: %MSI_NAME%.msi

:: Build Rust project
echo.
echo Building Rust installer...
cargo build --release
if errorlevel 1 (
    echo ERROR: Cargo build failed
    exit /b 1
)

:: Rename exe to match MSI name
set "EXE_NAME=%MSI_NAME%.exe"
echo.
echo Renaming to %EXE_NAME%...
move /Y "target\release\zayit-installer.exe" "target\release\%EXE_NAME%" >nul
if errorlevel 1 (
    echo ERROR: Failed to rename exe
    exit /b 1
)
echo OK

echo.
echo === Build Complete ===
echo Output: target\release\%EXE_NAME%
echo.

endlocal
