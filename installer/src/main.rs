#![windows_subsystem = "windows"]

use image::GenericImageView;
use std::ffi::c_void;
use std::io::Write;
use std::path::PathBuf;
use std::process::Command;
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
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
    CreateWindowExW, DefWindowProcW, DispatchMessageW, GetMessageW, GetSystemMetrics,
    LoadCursorW, PostQuitMessage, RegisterClassW, ShowWindow, TranslateMessage, UpdateLayeredWindow,
    CS_HREDRAW, CS_VREDRAW, IDC_ARROW, MSG, SM_CXSCREEN, SM_CYSCREEN, SW_SHOW, ULW_ALPHA,
    WM_DESTROY, WNDCLASSW, WS_EX_LAYERED, WS_EX_TOOLWINDOW, WS_EX_TOPMOST, WS_POPUP,
};

// Embed splash image at compile time
const SPLASH_PNG: &[u8] = include_bytes!("../resources/splash.png");
// Embed MSI at compile time
const MSI_DATA: &[u8] = include_bytes!("../resources/Zayit.msi");

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
    let mut bgra_pixels = Vec::with_capacity((width * height * 4) as usize);
    for pixel in rgba.pixels() {
        let a = pixel[3] as f32 / 255.0;
        bgra_pixels.push((pixel[2] as f32 * a) as u8); // B premultiplied
        bgra_pixels.push((pixel[1] as f32 * a) as u8); // G premultiplied
        bgra_pixels.push((pixel[0] as f32 * a) as u8); // R premultiplied
        bgra_pixels.push(pixel[3]); // A
    }

    // No vertical flip needed - we use negative biHeight for top-down DIB (like JBR)

    // Flag to signal installation complete
    let install_complete = Arc::new(AtomicBool::new(false));
    let install_complete_thread = Arc::clone(&install_complete);

    // Start MSI installation in background thread
    let mut install_thread = Some(thread::spawn(move || {
        install_msi_silently();
        install_complete_thread.store(true, Ordering::SeqCst);
    }));

    // Create and show splash window
    let _hwnd = create_splash_window(width as i32, height as i32, &bgra_pixels);

    // Message loop with periodic check for installation completion
    unsafe {
        let mut msg = MSG::default();
        loop {
            let result = GetMessageW(&mut msg, HWND::default(), 0, 0);
            if result.0 == 0 || result.0 == -1 {
                break;
            }

            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);

            // Check if installation is complete
            if install_complete.load(Ordering::SeqCst) {
                // Wait for installation thread to finish
                if let Some(handle) = install_thread.take() {
                    let _ = handle.join();
                }

                // Launch the application
                launch_application();

                // Keep splash visible for 3 more seconds after app launch
                thread::sleep(std::time::Duration::from_secs(3));

                PostQuitMessage(0);
                break;
            }
        }
    }
}

fn install_msi_silently() {
    // Write MSI to temp file
    let temp_dir = std::env::temp_dir();
    let msi_path = temp_dir.join("Zayit-installer-temp.msi");

    {
        let mut file = std::fs::File::create(&msi_path).expect("Failed to create temp MSI file");
        file.write_all(MSI_DATA).expect("Failed to write MSI data");
    }

    // Run msiexec silently
    let status = Command::new("msiexec")
        .args(["/i", msi_path.to_str().unwrap(), "/qn", "/norestart"])
        .status();

    match status {
        Ok(exit_status) => {
            if !exit_status.success() {
                eprintln!(
                    "MSI installation failed with exit code: {:?}",
                    exit_status.code()
                );
            }
        }
        Err(e) => {
            eprintln!("Failed to run msiexec: {}", e);
        }
    }

    // Clean up temp MSI
    let _ = std::fs::remove_file(&msi_path);
}

fn get_install_path() -> PathBuf {
    if let Some(local_app_data) = dirs::data_local_dir() {
        local_app_data.join("Zayit").join("Zayit.exe")
    } else {
        PathBuf::from(r"C:\Users")
            .join(std::env::var("USERNAME").unwrap_or_else(|_| "User".to_string()))
            .join("AppData")
            .join("Local")
            .join("Zayit")
            .join("Zayit.exe")
    }
}

fn launch_application() {
    let exe_path = get_install_path();

    // Wait a bit for MSI to fully complete
    thread::sleep(std::time::Duration::from_millis(500));

    // Try a few times in case the file system needs to catch up
    for attempt in 0..5 {
        if exe_path.exists() {
            let _ = Command::new(&exe_path).spawn();
            return;
        }
        if attempt < 4 {
            thread::sleep(std::time::Duration::from_millis(500));
        }
    }

    // If still not found, show error message
    use windows::Win32::UI::WindowsAndMessaging::{MessageBoxW, MB_ICONERROR, MB_OK};
    unsafe {
        MessageBoxW(
            None,
            w!("L'application n'a pas pu être trouvée après l'installation."),
            w!("Erreur"),
            MB_OK | MB_ICONERROR,
        );
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
            WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_TOPMOST,
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
        WM_DESTROY => {
            PostQuitMessage(0);
            LRESULT(0)
        }
        _ => DefWindowProcW(hwnd, msg, wparam, lparam),
    }
}
