#![windows_subsystem = "windows"]

use image::GenericImageView;
use std::ffi::c_void;
use std::io::Write;
use std::process::Command;
use winreg::enums::*;
use winreg::RegKey;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};
use windows::core::w;
use windows::Win32::Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, POINT, SIZE, WPARAM};
use windows::Win32::Graphics::Gdi::{
    CreateCompatibleDC, CreateDIBSection, DeleteDC, DeleteObject, GetDC, ReleaseDC, SelectObject,
    BITMAPINFO, BITMAPINFOHEADER, BI_RGB, DIB_RGB_COLORS,
};
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::HiDpi::{
    SetProcessDpiAwarenessContext, DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
};
use windows::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DispatchMessageW, GetSystemMetrics,
    LoadCursorW, PeekMessageW, PostQuitMessage, RegisterClassW, ShowWindow, TranslateMessage, UpdateLayeredWindow,
    CS_HREDRAW, CS_VREDRAW, IDC_ARROW, MSG, PM_REMOVE, SM_CXSCREEN, SM_CYSCREEN, SW_SHOW, ULW_ALPHA,
    WM_CLOSE, WM_DESTROY, WNDCLASSW, WS_EX_LAYERED, WS_EX_TOOLWINDOW, WS_POPUP,
};

// Embed splash image at compile time
const SPLASH_PNG: &[u8] = include_bytes!("../resources/splash.png");
// Embed NSIS installer at compile time
const NSIS_DATA: &[u8] = include_bytes!("../resources/zayit-nsis.exe");

// Progress bar configuration
const PROGRESS_BAR_HEIGHT: i32 = 4;
const PROGRESS_BAR_COLOR: (u8, u8, u8) = (212, 175, 55); // Gold color matching the logo

fn main() {
    // Set DPI awareness before any window creation (like JetBrains Runtime does)
    unsafe {
        let _ = SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    }

    // Decode splash image
    let img = image::load_from_memory(SPLASH_PNG).expect("Failed to decode splash image");
    let (width, height) = img.dimensions();

    // Convert to BGRA format (premultiplied alpha for layered window)
    // Using ARGB format: 0x00ff0000 (R), 0x0000ff00 (G), 0x000000ff (B), 0xff000000 (A)
    // with premultiplied alpha (like JBR splash screen)
    let rgba = img.to_rgba8();
    let mut base_bgra_pixels = Vec::with_capacity((width * height * 4) as usize);
    for pixel in rgba.pixels() {
        let a = pixel[3] as f32 / 255.0;
        base_bgra_pixels.push((pixel[2] as f32 * a) as u8); // B premultiplied
        base_bgra_pixels.push((pixel[1] as f32 * a) as u8); // G premultiplied
        base_bgra_pixels.push((pixel[0] as f32 * a) as u8); // R premultiplied
        base_bgra_pixels.push(pixel[3]); // A
    }

    // No vertical flip needed - we use negative biHeight for top-down DIB (like JBR)

    // Progress tracking (0-100)
    let progress = Arc::new(AtomicU32::new(0));
    let progress_thread = Arc::clone(&progress);

    // Flag to signal installation complete
    let install_complete = Arc::new(AtomicBool::new(false));
    let install_complete_thread = Arc::clone(&install_complete);

    // Start installation in background thread
    let mut install_thread = Some(thread::spawn(move || {
        install_with_progress(progress_thread);
        install_complete_thread.store(true, Ordering::SeqCst);
    }));

    // Create and show splash window
    let mut hwnd = create_splash_window(width as i32, height as i32, &base_bgra_pixels);

    // Non-blocking message loop with progress bar animation
    let mut last_displayed_progress: u32 = 0;
    let mut smooth_progress: f32 = 0.0;
    let frame_duration = Duration::from_millis(33); // ~30 FPS
    let mut last_frame = Instant::now();
    let start_time = Instant::now();
    let mut last_visibility_check = Instant::now();

    unsafe {
        use windows::Win32::UI::WindowsAndMessaging::IsWindow;

        let mut msg = MSG::default();
        loop {
            // Process all pending messages without blocking
            while PeekMessageW(&mut msg, HWND::default(), 0, 0, PM_REMOVE).as_bool() {
                if msg.message == 0x0012 { // WM_QUIT
                    // Ignore WM_QUIT during installation - we control when to exit
                    continue;
                }
                let _ = TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }

            // Periodically check window validity and recreate if needed
            let now = Instant::now();
            if now.duration_since(last_visibility_check) >= Duration::from_millis(500) {
                last_visibility_check = now;
                if !IsWindow(hwnd).as_bool() {
                    // Window was destroyed - recreate it
                    hwnd = create_splash_window(width as i32, height as i32, &base_bgra_pixels);
                    // Force redraw with current progress
                    let _ = update_splash_with_progress(hwnd, width as i32, height as i32, &base_bgra_pixels, last_displayed_progress);
                } else {
                    // Ensure window is visible
                    let _ = ShowWindow(hwnd, SW_SHOW);
                }
            }

            // Check if installation is complete
            if install_complete.load(Ordering::SeqCst) {
                // Animate to 100% smoothly
                while smooth_progress < 100.0 {
                    smooth_progress = (smooth_progress + 5.0).min(100.0);
                    // Check window validity and recreate if needed
                    if !IsWindow(hwnd).as_bool() {
                        hwnd = create_splash_window(width as i32, height as i32, &base_bgra_pixels);
                    }
                    let _ = update_splash_with_progress(hwnd, width as i32, height as i32, &base_bgra_pixels, smooth_progress as u32);
                    thread::sleep(Duration::from_millis(20));
                }

                // Wait for installation thread to finish
                if let Some(handle) = install_thread.take() {
                    let _ = handle.join();
                }

                // Launch the application after NSIS silent install
                launch_application();

                PostQuitMessage(0);
                break;
            }

            // Update progress bar at ~30 FPS with smooth animation
            if now.duration_since(last_frame) >= frame_duration {
                let target_progress = progress.load(Ordering::SeqCst) as f32;

                // Smooth interpolation toward target
                // Also add time-based minimum progress so it never looks stuck
                let elapsed_secs = start_time.elapsed().as_secs_f32();
                let time_based_min = (elapsed_secs * 2.0).min(85.0); // Slow increase up to 85%

                let effective_target = target_progress.max(time_based_min);

                // Smooth easing toward target
                if smooth_progress < effective_target {
                    smooth_progress += ((effective_target - smooth_progress) * 0.1).max(0.5);
                    smooth_progress = smooth_progress.min(effective_target);
                }

                let display_progress = smooth_progress as u32;
                if display_progress != last_displayed_progress {
                    if update_splash_with_progress(hwnd, width as i32, height as i32, &base_bgra_pixels, display_progress) {
                        last_displayed_progress = display_progress;
                    }
                }
                last_frame = now;
            }

            // Small sleep to prevent CPU spinning
            thread::sleep(Duration::from_millis(10));
        }
    }
}

fn install_with_progress(progress: Arc<AtomicU32>) {
    // Step 1: Check for and uninstall old MSI installation
    uninstall_old_msi(&progress);

    // Step 2: Extract NSIS installer to temp directory
    let temp_dir = std::env::temp_dir();
    let nsis_path = temp_dir.join("Zayit-installer-temp.exe");

    {
        let mut file = std::fs::File::create(&nsis_path).expect("Failed to create temp NSIS file");
        file.write_all(NSIS_DATA).expect("Failed to write NSIS data");
    }

    progress.store(40, Ordering::SeqCst);

    // Step 3: Run NSIS installer silently
    let status = Command::new(&nsis_path)
        .arg("/S")
        .status();

    progress.store(100, Ordering::SeqCst);

    if let Err(e) = status {
        eprintln!("Failed to run NSIS installer: {}", e);
    }

    // Clean up temp file
    let _ = std::fs::remove_file(&nsis_path);
}

/// Detects and silently uninstalls any old MSI-based Zayit installation.
fn uninstall_old_msi(progress: &Arc<AtomicU32>) {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);

    let search_paths: &[(&RegKey, &str)] = &[
        (&hkcu, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
        (&hklm, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
        (&hklm, r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"),
    ];

    for (root, path) in search_paths {
        if let Ok(uninstall_key) = root.open_subkey(path) {
            for key_name in uninstall_key.enum_keys().filter_map(|k| k.ok()) {
                if let Ok(subkey) = uninstall_key.open_subkey(&key_name) {
                    let display_name: Result<String, _> = subkey.get_value("DisplayName");
                    if let Ok(name) = display_name {
                        if name != "Zayit" {
                            continue;
                        }
                        // Verify this is an MSI installation (not NSIS) by checking UninstallString
                        let is_msi = subkey
                            .get_value::<String, _>("UninstallString")
                            .map(|s| s.to_lowercase().contains("msiexec"))
                            .unwrap_or(false);

                        if !is_msi {
                            continue;
                        }

                        progress.store(10, Ordering::SeqCst);
                        // Silently uninstall the old MSI
                        let _ = Command::new("msiexec")
                            .args(["/x", &key_name, "/qn", "/norestart"])
                            .status();
                        progress.store(30, Ordering::SeqCst);
                        return;
                    }
                }
            }
        }
    }
}

fn update_splash_with_progress(hwnd: HWND, width: i32, height: i32, base_pixels: &[u8], progress: u32) -> bool {
    use windows::Win32::UI::WindowsAndMessaging::IsWindow;

    // Check if window is still valid before attempting to update
    unsafe {
        if !IsWindow(hwnd).as_bool() {
            return false;
        }
    }

    // Create a copy of the base pixels and overlay the progress bar
    let mut pixels = base_pixels.to_vec();

    // Calculate progress bar dimensions
    // 30px from bottom, 75% width centered (12.5% margin each side)
    let side_margin = width * 125 / 1000;  // 12.5%
    let left_margin = side_margin;
    let right_margin = side_margin;
    let bottom_margin = 75;

    let bar_y_start = (height - bottom_margin - PROGRESS_BAR_HEIGHT).max(0);
    let bar_y_end = (height - bottom_margin).min(height - 1);
    let bar_x_start = left_margin;
    let bar_x_end = width - right_margin;

    // Calculate bar width AFTER bounds checking
    let bar_width = (bar_x_end - bar_x_start).max(1);

    // Calculate filled portion (RIGHT TO LEFT - RTL style)
    // Progress goes from right to left: at 0% nothing is filled, at 100% full bar from right
    let filled_width = (bar_width as f32 * (progress as f32 / 100.0)) as i32;
    let fill_start_x = bar_x_end - filled_width;

    // Draw the progress bar
    let (r, g, b) = PROGRESS_BAR_COLOR;

    for y in bar_y_start..bar_y_end {
        for x in bar_x_start..bar_x_end {
            let pixel_idx = ((y * width + x) * 4) as usize;
            if pixel_idx + 3 < pixels.len() {
                if x >= fill_start_x {
                    // Filled portion (gold color, fully opaque)
                    pixels[pixel_idx] = b;     // B
                    pixels[pixel_idx + 1] = g; // G
                    pixels[pixel_idx + 2] = r; // R
                    pixels[pixel_idx + 3] = 255; // A - fully opaque
                } else {
                    // Background portion (dark, fully opaque to avoid transparency issues
                    // when other windows overlap and are minimized)
                    pixels[pixel_idx] = 30;     // B
                    pixels[pixel_idx + 1] = 30; // G
                    pixels[pixel_idx + 2] = 30; // R
                    pixels[pixel_idx + 3] = 255; // A - fully opaque
                }
            }
        }
    }

    // Update the layered window with new pixels
    unsafe {
        let screen_dc = GetDC(None);
        let mem_dc = CreateCompatibleDC(screen_dc);

        let bmi = BITMAPINFO {
            bmiHeader: BITMAPINFOHEADER {
                biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
                biWidth: width,
                biHeight: -height, // Negative height = top-down DIB
                biPlanes: 1,
                biBitCount: 32,
                biCompression: BI_RGB.0,
                ..Default::default()
            },
            ..Default::default()
        };

        let mut bits: *mut c_void = ptr::null_mut();
        let hbitmap = match CreateDIBSection(mem_dc, &bmi, DIB_RGB_COLORS, &mut bits, None, 0) {
            Ok(bmp) => bmp,
            Err(_) => {
                let _ = DeleteDC(mem_dc);
                let _ = ReleaseDC(None, screen_dc);
                return false;
            }
        };

        if !bits.is_null() {
            ptr::copy_nonoverlapping(pixels.as_ptr(), bits as *mut u8, pixels.len());
        }

        let old_bitmap = SelectObject(mem_dc, hbitmap);

        let size = SIZE { cx: width, cy: height };
        let pt_src = POINT { x: 0, y: 0 };
        let blend = windows::Win32::Graphics::Gdi::BLENDFUNCTION {
            BlendOp: 0,
            BlendFlags: 0,
            SourceConstantAlpha: 255,
            AlphaFormat: 1,
        };

        let result = UpdateLayeredWindow(
            hwnd,
            screen_dc,
            None,
            Some(&size),
            mem_dc,
            Some(&pt_src),
            None,
            Some(&blend),
            ULW_ALPHA,
        );

        SelectObject(mem_dc, old_bitmap);
        let _ = DeleteObject(hbitmap);
        let _ = DeleteDC(mem_dc);
        let _ = ReleaseDC(None, screen_dc);

        result.is_ok()
    }
}

fn get_install_path() -> std::path::PathBuf {
    let local_app_data = std::env::var("LOCALAPPDATA")
        .unwrap_or_else(|_| {
            let user = std::env::var("USERNAME").unwrap_or_else(|_| "User".to_string());
            format!(r"C:\Users\{}\AppData\Local", user)
        });
    std::path::PathBuf::from(local_app_data).join("Programs").join("zayit").join("zayit.exe")
}

fn launch_application() {
    let exe_path = get_install_path();

    // Wait a bit for NSIS to fully complete file operations
    thread::sleep(Duration::from_millis(500));

    for attempt in 0..5 {
        if exe_path.exists() {
            let _ = Command::new(&exe_path).spawn();
            return;
        }
        if attempt < 4 {
            thread::sleep(Duration::from_millis(500));
        }
    }
}

fn create_splash_window(img_width: i32, img_height: i32, pixels: &[u8]) -> HWND {
    unsafe {
        let h_module = GetModuleHandleW(None).unwrap();
        let instance: HINSTANCE = std::mem::transmute(h_module);

        let wnd_class = WNDCLASSW {
            style: CS_HREDRAW | CS_VREDRAW,
            lpfnWndProc: Some(wnd_proc),
            hInstance: instance,
            lpszClassName: w!("ZayitSplash"),
            hCursor: LoadCursorW(None, IDC_ARROW).unwrap(),
            ..Default::default()
        };

        RegisterClassW(&wnd_class);

        // Center window on screen
        let screen_width = GetSystemMetrics(SM_CXSCREEN);
        let screen_height = GetSystemMetrics(SM_CYSCREEN);
        let x = (screen_width - img_width) / 2;
        let y = (screen_height - img_height) / 2;

        // Create layered window (no border, transparent background)
        let hwnd = CreateWindowExW(
            WS_EX_LAYERED | WS_EX_TOOLWINDOW,
            w!("ZayitSplash"),
            w!("Zayit Installer"),
            WS_POPUP,
            x,
            y,
            img_width,
            img_height,
            None,
            None,
            Some(&instance),
            None,
        )
        .unwrap();

        // Create bitmap and update layered window
        let screen_dc = GetDC(None);
        let mem_dc = CreateCompatibleDC(screen_dc);

        // Use negative height for top-down DIB format (like JetBrains Runtime)
        // This avoids the need to manually flip the image vertically
        let bmi = BITMAPINFO {
            bmiHeader: BITMAPINFOHEADER {
                biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
                biWidth: img_width,
                biHeight: -img_height, // Negative height = top-down DIB (like JBR: bmi.biHeight = -splash->height)
                biPlanes: 1,
                biBitCount: 32,
                biCompression: BI_RGB.0,
                ..Default::default()
            },
            ..Default::default()
        };

        let mut bits: *mut c_void = ptr::null_mut();
        let hbitmap = CreateDIBSection(mem_dc, &bmi, DIB_RGB_COLORS, &mut bits, None, 0).unwrap();

        // Copy pixel data
        if !bits.is_null() {
            ptr::copy_nonoverlapping(pixels.as_ptr(), bits as *mut u8, pixels.len());
        }

        let old_bitmap = SelectObject(mem_dc, hbitmap);

        // Update layered window with the bitmap
        let size = SIZE {
            cx: img_width,
            cy: img_height,
        };
        let pt_src = POINT { x: 0, y: 0 };
        let blend = windows::Win32::Graphics::Gdi::BLENDFUNCTION {
            BlendOp: 0,        // AC_SRC_OVER
            BlendFlags: 0,
            SourceConstantAlpha: 255,
            AlphaFormat: 1,    // AC_SRC_ALPHA
        };

        let _ = UpdateLayeredWindow(
            hwnd,
            screen_dc,
            None,
            Some(&size),
            mem_dc,
            Some(&pt_src),
            None,
            Some(&blend),
            ULW_ALPHA,
        );

        // Cleanup
        SelectObject(mem_dc, old_bitmap);
        let _ = DeleteObject(hbitmap);
        let _ = DeleteDC(mem_dc);
        let _ = ReleaseDC(None, screen_dc);

        let _ = ShowWindow(hwnd, SW_SHOW);

        hwnd
    }
}

unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_CLOSE => {
            // Ignore WM_CLOSE during installation - prevent external closure
            LRESULT(0)
        }
        WM_DESTROY => {
            // Don't call PostQuitMessage - the main loop will recreate the window if needed
            // and will control when to actually quit
            LRESULT(0)
        }
        _ => DefWindowProcW(hwnd, msg, wparam, lparam),
    }
}
