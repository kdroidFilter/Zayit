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

:: Copy NSIS installer and get its name
echo Copying NSIS installer...
set "NSIS_PATH=..\SeforimApp\build\compose\binaries\main\graalvm-nsis"
set "NSIS_NAME="

:: Find the NSIS exe file (handle version in filename)
for %%f in ("%NSIS_PATH%\zayit-*-nsis.exe") do (
    set "NSIS_NAME=%%~nf"
    copy /Y "%%f" "resources\zayit-nsis.exe" >nul
    goto :nsis_copied
)

echo ERROR: NSIS installer not found in %NSIS_PATH%
echo Run: gradlew :SeforimApp:packageNsis
exit /b 1

:nsis_copied
echo OK - Found: %NSIS_NAME%.exe

:: Build Rust project
echo.
echo Building Rust installer...
cargo build --release
if errorlevel 1 (
    echo ERROR: Cargo build failed
    exit /b 1
)

:: Rename exe to match NSIS name
set "EXE_NAME=%NSIS_NAME%.exe"
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
