const GITHUB_OWNER = "kdroidFilter";
const GITHUB_REPO = "Zayit";
const DB_OWNER = "kdroidFilter";
const DB_REPO = "SeforimLibrary";
const BRAND_ICON =
  "icon.png";

// Enhanced platform and architecture detection
async function detectPlatform() {
  if (typeof navigator === "undefined") {
    return { os: "unknown", arch: "unknown", isMobile: false };
  }

  const ua = navigator.userAgent || "";
  const uaData = navigator.userAgentData || {};
  const platform = (uaData.platform || navigator.platform || "").toLowerCase();

  // Check for mobile devices first
  const isMobile = /iPhone|iPad|iPod|Android/i.test(ua);
  if (isMobile) {
    const isIOS = /iPhone|iPad|iPod/i.test(ua);
    const isAndroid = /Android/i.test(ua);
    return {
      os: isIOS ? "ios" : isAndroid ? "android" : "mobile",
      arch: "mobile",
      isMobile: true
    };
  }

  // Try to use Chrome's UserAgentData API for accurate architecture detection
  let arch = "unknown";
  if (uaData && uaData.getHighEntropyValues) {
    try {
      const highEntropy = await uaData.getHighEntropyValues(["architecture", "bitness"]);
      if (highEntropy.architecture) {
        const archMap = {
          "arm": "arm64",
          "x86": highEntropy.bitness === "64" ? "x64" : "x86"
        };
        arch = archMap[highEntropy.architecture] || highEntropy.architecture;
      }
    } catch (e) {
      // Fallback to UA string detection
      console.log("Could not get high entropy values:", e);
    }
  }

  // Fallback architecture detection from UA string
  if (arch === "unknown") {
    const archHint = (uaData.architecture || ua).toLowerCase();
    if (/arm64|aarch64/.test(archHint)) {
      arch = "arm64";
    } else if (/arm/.test(archHint)) {
      arch = "arm";
    } else if (/x64|x86_64|amd64|win64/.test(archHint) || /WOW64|Win64/.test(ua)) {
      arch = "x64";
    } else if (/i[3-6]86|x86/.test(archHint)) {
      arch = "x86";
    }
  }

  // OS Detection
  if (/win/i.test(platform) || /windows/i.test(ua)) {
    return { os: "windows", arch: arch === "unknown" ? null : arch, isMobile: false };
  }

  if (/mac/i.test(platform) || /macintosh|mac os x/i.test(ua)) {
    // For Mac, try to detect Apple Silicon vs Intel
    if (arch === "unknown") {
      // Check for Apple Silicon indicators
      if (/Apple/.test(navigator.vendor) && navigator.maxTouchPoints > 0) {
        arch = "arm64"; // Likely Apple Silicon
      } else {
        arch = null; // Will show both options
      }
    }
    return { os: "mac", arch, isMobile: false };
  }

  if (/linux/i.test(platform) || /linux/i.test(ua)) {
    // Detect Linux distribution type
    const distro = /ubuntu|debian/i.test(ua) ? "deb" :
                   /fedora|centos|redhat|rhel|opensuse|suse/i.test(ua) ? "rpm" :
                   "both"; // Show both if unknown
    return {
      os: "linux",
      distro,
      arch: arch === "unknown" ? null : arch,
      isMobile: false
    };
  }

  return { os: "unknown", arch: "unknown", isMobile: false };
}

function osLabelHebrew(platform) {
  const labels = {
    windows: "Windows",
    mac: "macOS",
    linux: "Linux",
    ios: "iOS",
    android: "Android",
    mobile: "מכשיר נייד",
    unknown: "לא מזוהה"
  };
  return labels[platform.os] || platform.os;
}

function archLabelHebrew(arch) {
  const labels = {
    x64: "Intel/AMD 64-bit",
    x86: "Intel/AMD 32-bit",
    arm64: "ARM64 / Apple Silicon",
    arm: "ARM",
    mobile: "נייד",
    unknown: "לא מזוהה"
  };
  return labels[arch] || arch;
}

function getArchIcon(arch) {
  const icons = {
    x64: "memory",
    x86: "memory_alt",
    arm64: "developer_board",
    arm: "developer_board",
    unknown: "help_outline"
  };
  return icons[arch] || "help_outline";
}

function getOSIcon(os) {
  const icons = {
    windows: "desktop_windows",
    mac: "laptop_mac",
    linux: "computer",
    deb: "computer",
    rpm: "computer",
    ios: "phone_iphone",
    android: "phone_android",
    mobile: "smartphone",
    unknown: "devices"
  };
  return icons[os] || "devices";
}

function formatFileSize(bytes) {
  if (!bytes) return "?";
  const mb = Math.round(bytes / 1024 / 1024);
  return mb > 0 ? mb + " מ״ב" : "< 1 מ״ב";
}

function filterAssetsByPlatform(assets, platform) {
  if (!assets || assets.length === 0) return [];

  const list = assets.map((a) => ({
    ...a,
    lname: (a.name || "").toLowerCase(),
  }));

  if (platform.os === "windows") {
    // Filter Windows installers
    return list.filter(a => /\.(msi|exe)$/i.test(a.name))
      .sort((a, b) => {
        // Prioritize MSI over EXE
        if (a.lname.endsWith('.msi') && !b.lname.endsWith('.msi')) return -1;
        if (!a.lname.endsWith('.msi') && b.lname.endsWith('.msi')) return 1;
        return 0;
      });
  }

  if (platform.os === "linux") {
    // Filter based on distribution
    if (platform.distro === "deb") {
      return list.filter(a => /\.deb$/i.test(a.name));
    } else if (platform.distro === "rpm") {
      return list.filter(a => /\.rpm$/i.test(a.name));
    } else {
      // Show both deb and rpm
      return list.filter(a => /\.(deb|rpm)$/i.test(a.name))
        .sort((a, b) => {
          // Group by type
          if (a.lname.endsWith('.deb') && !b.lname.endsWith('.deb')) return -1;
          if (!a.lname.endsWith('.deb') && b.lname.endsWith('.deb')) return 1;
          return 0;
        });
    }
  }

  if (platform.os === "mac") {
    // For macOS, prefer DMG for manual installation; fall back to PKG if no DMG exists
    const dmgAssets = list.filter(a => /\.dmg$/i.test(a.name));
    if (dmgAssets.length > 0) {
      return dmgAssets;
    }
    return list.filter(a => /\.pkg$/i.test(a.name));
  }

  return [];
}

function groupAssetsByArch(assets) {
  const groups = {
    x64: [],
    arm64: [],
    unknown: []
  };

  assets.forEach(asset => {
    const name = asset.name.toLowerCase();
    if (/arm64|aarch64/.test(name)) {
      groups.arm64.push(asset);
    } else if (/x64|x86_64|amd64/.test(name) || (name.includes('64') && !name.includes('arm'))) {
      groups.x64.push(asset);
    } else if (/arm/.test(name)) {
      groups.arm64.push(asset);
    } else {
      groups.unknown.push(asset);
    }
  });

  return groups;
}

let appState = {
  loading: true,
  error: null,
  release: null,
  dbLoading: true,
  dbError: null,
  dbAssets: [],
  showAllAssets: false,
  includeDb: false,
  platform: null,
  showCrossPlatform: false,
  selectedOS: 'windows'
};

function setState(patch) {
  appState = Object.assign({}, appState, patch);
  renderApp();
}

async function renderApp() {
  const root = document.getElementById("zayit-root");
  if (!root) return;

  if (appState.loading) {
    root.innerHTML = `
      <div class="card">
        <div class="card-inner">
          <div class="loading-screen">
            <div class="spinner"></div>
            <p style="color:var(--gold-soft);font-size:1.05rem;margin:0;">
              טוען את הנתונים…
            </p>
          </div>
        </div>
      </div>
    `;
    return;
  }

  const platform = appState.platform || await detectPlatform();
  if (!appState.platform) {
    appState.platform = platform;
  }

  const release = appState.release;
  const assets = release ? release.assets || [] : [];

  // Handle mobile devices
  if (platform.isMobile) {
    root.innerHTML = `
      <div class="card">
        <div class="card-inner">
          <div class="center" style="margin-bottom:1.8rem;">
            <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
            <h1 class="title">זית</h1>
            <p class="subtitle">
              <span class="material-symbols-outlined">${getOSIcon(platform.os)}</span>
              ${osLabelHebrew(platform)}
            </p>
          </div>

          <div class="section section-box" style="text-align:center;">
            <span class="material-symbols-outlined" style="font-size:3rem;color:var(--gold-muted);margin-bottom:1rem;display:block;">
              mobile_off
            </span>
            <h2 style="color:var(--text-main);margin:0 0 0.5rem 0;">
              לא נתמך במכשירים ניידים
            </h2>
            <p style="color:var(--gold-soft);margin:0;font-size:0.95rem;">
              זית הוא יישום שולחני בלבד.<br/>
              אנא גש מהמחשב שלך כדי להוריד את התוכנה.
            </p>
          </div>

          <div class="footer">
            <p style="margin:0 0 0.5rem 0;">נוצר על ידי אליהו גמבש</p>
            <div class="footer-links">
              <a href="https://github.com/kdroidFilter/Zayit" target="_blank" class="footer-link" data-tooltip="קוד מקור">
                <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                <span>GitHub</span>
              </a>
              <a href="https://ko-fi.com/lomityaesh" target="_blank" class="footer-link footer-link-donate" data-tooltip="תמיכה">
                <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
                <span>תמיכה</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    `;
    return;
  }

  // Handle unknown OS
  if (platform.os === "unknown") {
    root.innerHTML = `
      <div class="card">
        <div class="card-inner">
          <div class="center" style="margin-bottom:1.8rem;">
            <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
            <h1 class="title">זית — הורדה</h1>
          </div>

          <div class="section section-box" style="text-align:center;">
            <span class="material-symbols-outlined" style="font-size:3rem;color:var(--gold-muted);margin-bottom:1rem;display:block;">
              help_outline
            </span>
            <h2 style="color:var(--text-main);margin:0 0 0.5rem 0;">
              מערכת הפעלה לא מזוהה
            </h2>
            <p style="color:var(--gold-soft);margin:0 0 1.5rem 0;font-size:0.95rem;">
              לא הצלחנו לזהות את מערכת ההפעלה שלך.<br/>
              בחר ידנית את הקובץ המתאים:
            </p>
            ${renderManualDownloadLinks(assets)}
          </div>

          <div class="footer">
            <p style="margin:0 0 0.5rem 0;">נוצר על ידי אליהו גמבש</p>
            <div class="footer-links">
              <a href="https://github.com/kdroidFilter/Zayit" target="_blank" class="footer-link" data-tooltip="קוד מקור">
                <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                <span>GitHub</span>
              </a>
              <a href="https://ko-fi.com/lomityaesh" target="_blank" class="footer-link footer-link-donate" data-tooltip="תמיכה">
                <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
                <span>תמיכה</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    `;
    attachEventHandlers();
    return;
  }

  const errorBlock = appState.error
    ? `
      <div class="error-box">
        <p class="error-text">⚠️ <strong>שגיאה:</strong> ${appState.error}</p>
        <p class="error-help">
          אם הבעיה נמשכת, בדוק את חיבור האינטרנט או נסה מאוחר יותר.
        </p>
      </div>
    `
    : "";

  let mainDownloadBlock = "";

  // Special handling for macOS - show curl command
  if (platform.os === "mac") {
    mainDownloadBlock = `
      <div class="section section-box">
        <h2 class="section-title">
          <span class="material-symbols-outlined">terminal</span>
          <span>התקנה אוטומטית עבור macOS</span>
        </h2>
        <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.95rem;">
          העתק והרץ את הפקודה הבאה בטרמינל:
        </p>
        <div class="command-box">
          <code id="mac-command">curl -L https://raw.githubusercontent.com/kdroidFilter/Zayit/refs/heads/master/launch.mac | bash</code>
          <button class="copy-btn" onclick="copyMacCommand()">
            <span class="material-symbols-outlined">content_copy</span>
          </button>
        </div>
        <p style="color:var(--gold-muted);margin:1rem 0 0 0;font-size:0.85rem;">
          <span class="material-symbols-outlined" style="font-size:0.95rem;vertical-align:middle;">info</span>
          הסקריפט יוריד ויתקין את הגרסה המתאימה למחשב שלך אוטומטית
        </p>
      </div>
    `;
  } else if (platform.os === "windows") {
    // Windows - show architecture options if unknown
    const windowsAssets = filterAssetsByPlatform(assets, platform);
    const archGroups = groupAssetsByArch(windowsAssets);

    if (platform.arch && archGroups[platform.arch]?.length > 0) {
      // Known architecture with matching assets
      const recommended = archGroups[platform.arch][0];
      mainDownloadBlock = `
        <div class="section section-box">
          <h2 class="section-title">
            <span class="material-symbols-outlined">download</span>
            <span>הורדת התוכנה</span>
          </h2>
          <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.95rem;">
            קובץ מומלץ בשבילך:
            <strong>${recommended.name}</strong>
            (${recommended.size})
          </p>
          <div class="btn-row">
            <a href="${recommended.url}" target="_blank" class="btn btn-primary">
              <span class="material-symbols-outlined">download</span>
              <span>הורד עכשיו</span>
            </a>
          </div>
          ${archGroups[platform.arch === 'x64' ? 'arm64' : 'x64']?.length > 0 ? `
            <div style="margin-top:1rem;text-align:center;">
              <button class="toggle-button" onclick="setState({showAllAssets: !appState.showAllAssets})">
                <span class="material-symbols-outlined">
                  ${appState.showAllAssets ? 'expand_less' : 'expand_more'}
                </span>
                <span>${appState.showAllAssets ? 'הסתר' : 'הצג'} ארכיטקטורות אחרות</span>
              </button>
            </div>
            ${appState.showAllAssets ? renderArchitectureOptions(archGroups, platform.arch) : ''}
          ` : ''}
        </div>
      `;
    } else {
      // Unknown architecture or no matching assets - show both options
      mainDownloadBlock = renderWindowsArchOptions(archGroups);
    }
  } else if (platform.os === "linux") {
    // Linux - show curl command as primary, with download links as secondary
    const linuxAssets = filterAssetsByPlatform(assets, platform);
    const debAssets = linuxAssets.filter(a => a.name.toLowerCase().endsWith('.deb'));
    const rpmAssets = linuxAssets.filter(a => a.name.toLowerCase().endsWith('.rpm'));

    mainDownloadBlock = `
      <div class="section section-box">
        <h2 class="section-title">
          <span class="material-symbols-outlined">terminal</span>
          <span>התקנה אוטומטית עבור Linux</span>
        </h2>
        <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.95rem;">
          העתק והרץ את הפקודה הבאה בטרמינל:
        </p>
        <div class="command-box">
          <code id="linux-command">curl -L https://raw.githubusercontent.com/kdroidFilter/Zayit/refs/heads/master/launch.linux | bash</code>
          <button class="copy-btn" onclick="copyLinuxCommand()">
            <span class="material-symbols-outlined">content_copy</span>
          </button>
        </div>
        <p style="color:var(--gold-muted);margin:1rem 0 0 0;font-size:0.85rem;">
          <span class="material-symbols-outlined" style="font-size:0.95rem;vertical-align:middle;">info</span>
          הסקריפט יזהה אוטומטית את ההפצה (DEB/RPM) והארכיטקטורה שלך
        </p>
      </div>

      ${(debAssets.length > 0 || rpmAssets.length > 0) ? `
        <div class="section section-box">
          <h2 class="section-title">
            <span class="material-symbols-outlined">download</span>
            <span>הורדה ידנית</span>
          </h2>

          <button class="toggle-button" onclick="setState({showAllAssets: !appState.showAllAssets})" style="margin-bottom:1rem;">
            <span class="material-symbols-outlined">
              ${appState.showAllAssets ? 'expand_less' : 'expand_more'}
            </span>
            <span>${appState.showAllAssets ? 'הסתר' : 'הצג'} אפשרויות הורדה ידנית</span>
          </button>

          ${appState.showAllAssets ? `
            ${debAssets.length > 0 ? `
              <div class="linux-distro-section">
                <h3 style="color:var(--text-main);font-size:1rem;margin:0 0 0.75rem 0;display:flex;align-items:center;gap:0.4rem;">
                  <span class="material-symbols-outlined">package_2</span>
                  Debian/Ubuntu (.deb)
                </h3>
                ${renderLinuxArchOptions(debAssets, 'deb')}
              </div>
            ` : ''}

            ${rpmAssets.length > 0 ? `
              <div class="linux-distro-section" style="margin-top:1.5rem;">
                <h3 style="color:var(--text-main);font-size:1rem;margin:0 0 0.75rem 0;display:flex;align-items:center;gap:0.4rem;">
                  <span class="material-symbols-outlined">package_2</span>
                  Fedora/RHEL/openSUSE (.rpm)
                </h3>
                ${renderLinuxArchOptions(rpmAssets, 'rpm')}
              </div>
            ` : ''}
          ` : ''}
        </div>
      ` : ''}
    `;
  }

  // Database section
  const dbSection = renderDbSection();

  // Cross-platform downloads section
  const crossPlatformSection = renderCrossPlatformSection(assets);

  root.innerHTML = `
    <div class="card">
      <div class="card-inner">
        <div class="center" style="margin-bottom:1.8rem;">
          <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
          <h1 class="title">זית — הורדה מהירה</h1>
          <p class="subtitle">
            <span class="material-symbols-outlined">${getOSIcon(platform.os)}</span>
            זוהה: <strong>${osLabelHebrew(platform)}</strong>
            ${platform.arch && platform.arch !== 'unknown' ? `
              •
              <span class="material-symbols-outlined" style="font-size:1rem;">${getArchIcon(platform.arch)}</span>
              <strong>${archLabelHebrew(platform.arch)}</strong>
            ` : ''}
          </p>
          ${release ? `<p class="version-text">גרסה ${release.tag_name}</p>` : ""}
        </div>

        ${errorBlock}
        ${mainDownloadBlock}
        ${crossPlatformSection}
        ${dbSection}

        <div class="footer">
          <p style="margin:0 0 0.5rem 0;">נוצר על ידי אליהו גמבש</p>
          <div class="footer-links">
            <a href="https://github.com/kdroidFilter/Zayit" target="_blank" class="footer-link" data-tooltip="קוד מקור">
              <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
              <span>GitHub</span>
            </a>
            <a href="https://ko-fi.com/lomityaesh" target="_blank" class="footer-link footer-link-donate" data-tooltip="תמיכה">
              <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
              <span>תמיכה</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  `;

  attachEventHandlers();
}

function renderWindowsArchOptions(archGroups) {
  return `
    <div class="section section-box">
      <h2 class="section-title">
        <span class="material-symbols-outlined">download</span>
        <span>בחר את הארכיטקטורה שלך</span>
      </h2>
      <p style="color:var(--gold-soft);margin:0 0 1.5rem 0;font-size:0.9rem;">
        לא הצלחנו לזהות את הארכיטקטורה. בחר את האפשרות המתאימה:
      </p>

      <div class="arch-options">
        ${archGroups.x64?.length > 0 ? `
          <div class="arch-option">
            <div class="arch-header">
              <span class="material-symbols-outlined">${getArchIcon('x64')}</span>
              <h3>${archLabelHebrew('x64')}</h3>
            </div>
            <p class="arch-desc">רוב המחשבים המודרניים</p>
            ${archGroups.x64.map(asset => `
              <a href="${asset.url}" target="_blank" class="btn btn-primary" style="width:100%;margin-top:0.5rem;">
                <span class="material-symbols-outlined">download</span>
                <span>${asset.name} (${asset.size})</span>
              </a>
            `).join('')}
          </div>
        ` : ''}

        ${archGroups.arm64?.length > 0 ? `
          <div class="arch-option">
            <div class="arch-header">
              <span class="material-symbols-outlined">${getArchIcon('arm64')}</span>
              <h3>${archLabelHebrew('arm64')}</h3>
            </div>
            <p class="arch-desc">מחשבי Surface וכדומה</p>
            ${archGroups.arm64.map(asset => `
              <a href="${asset.url}" target="_blank" class="btn btn-primary" style="width:100%;margin-top:0.5rem;">
                <span class="material-symbols-outlined">download</span>
                <span>${asset.name} (${asset.size})</span>
              </a>
            `).join('')}
          </div>
        ` : ''}
      </div>
    </div>
  `;
}

function renderLinuxArchOptions(assets, type) {
  const archGroups = groupAssetsByArch(assets);

  return `
    <div class="arch-options compact">
      ${archGroups.x64?.length > 0 ? `
        <div class="download-item">
          <div style="display:flex;align-items:center;gap:0.5rem;margin-bottom:0.5rem;">
            <span class="material-symbols-outlined" style="font-size:1rem;color:var(--gold-soft);">${getArchIcon('x64')}</span>
            <span style="color:var(--gold-soft);font-size:0.9rem;">${archLabelHebrew('x64')}</span>
          </div>
          ${archGroups.x64.map(asset => `
            <a href="${asset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-bottom:0.5rem;">
              <span class="material-symbols-outlined">download</span>
              <span>${asset.name} (${asset.size})</span>
            </a>
          `).join('')}
        </div>
      ` : ''}

      ${archGroups.arm64?.length > 0 ? `
        <div class="download-item">
          <div style="display:flex;align-items:center;gap:0.5rem;margin-bottom:0.5rem;">
            <span class="material-symbols-outlined" style="font-size:1rem;color:var(--gold-soft);">${getArchIcon('arm64')}</span>
            <span style="color:var(--gold-soft);font-size:0.9rem;">${archLabelHebrew('arm64')}</span>
          </div>
          ${archGroups.arm64.map(asset => `
            <a href="${asset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-bottom:0.5rem;">
              <span class="material-symbols-outlined">download</span>
              <span>${asset.name} (${asset.size})</span>
            </a>
          `).join('')}
        </div>
      ` : ''}

      ${archGroups.unknown?.length > 0 ? `
        <div class="download-item">
          ${archGroups.unknown.map(asset => `
            <a href="${asset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-bottom:0.5rem;">
              <span class="material-symbols-outlined">download</span>
              <span>${asset.name} (${asset.size})</span>
            </a>
          `).join('')}
        </div>
      ` : ''}
    </div>
  `;
}

function renderArchitectureOptions(archGroups, currentArch) {
  const otherArch = currentArch === 'x64' ? 'arm64' : 'x64';
  if (!archGroups[otherArch]?.length) return '';

  return `
    <div style="margin-top:1rem;padding-top:1rem;border-top:1px solid rgba(255,215,0,0.1);">
      <p style="color:var(--gold-soft);font-size:0.9rem;margin:0 0 0.75rem 0;">
        ארכיטקטורות אחרות:
      </p>
      ${archGroups[otherArch].map(asset => `
        <a href="${asset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-bottom:0.5rem;">
          <span class="material-symbols-outlined">${getArchIcon(otherArch)}</span>
          <span>${asset.name} (${asset.size})</span>
        </a>
      `).join('')}
    </div>
  `;
}

function renderManualDownloadLinks(assets) {
  if (!assets || assets.length === 0) {
    return `<p style="color:var(--gold-soft);">לא נמצאו קבצים זמינים</p>`;
  }

  return `
    <div class="assets-list compact">
      ${assets.map(asset => `
        <div class="asset-item compact">
          <div class="asset-line">
            <div class="asset-meta">
              <p class="asset-name">${asset.name}</p>
              <p class="asset-size">גודל: ${asset.size}</p>
            </div>
            <a href="${asset.url}" target="_blank" class="btn btn-secondary">
              <span class="material-symbols-outlined">download</span>
              <span>הורדה</span>
            </a>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

function renderCrossPlatformSection(allAssets) {
  if (!allAssets || allAssets.length === 0) return '';

  // Group assets by platform
  const windowsAssets = allAssets.filter(a => /\.(msi|exe)$/i.test(a.name));
  // For macOS, prefer DMG for manual installation; fall back to PKG if no DMG exists
  const macCandidateAssets = allAssets.filter(a => /\.(dmg|pkg)$/i.test(a.name));
  const macDmgAssets = macCandidateAssets.filter(a => /\.dmg$/i.test(a.name));
  const macAssets = macDmgAssets.length > 0 ? macDmgAssets : macCandidateAssets.filter(a => /\.pkg$/i.test(a.name));
  const debAssets = allAssets.filter(a => /\.deb$/i.test(a.name));
  const rpmAssets = allAssets.filter(a => /\.rpm$/i.test(a.name));

  // Don't show section if no cross-platform assets
  if (windowsAssets.length === 0 && macAssets.length === 0 && debAssets.length === 0 && rpmAssets.length === 0) {
    return '';
  }

  return `
    <div class="section section-box section-cross-platform">
      <div class="section-header">
        <h2 class="section-title">
          <span class="material-symbols-outlined">devices</span>
          <span>הורדה למערכות הפעלה אחרות</span>
        </h2>
        <button class="toggle-button inline" onclick="setState({showCrossPlatform: !appState.showCrossPlatform})">
          <span class="material-symbols-outlined">
            ${appState.showCrossPlatform ? 'expand_less' : 'expand_more'}
          </span>
          <span>${appState.showCrossPlatform ? 'הסתר' : 'הצג'} מערכות אחרות</span>
        </button>
      </div>

      ${appState.showCrossPlatform ? `
        <div class="os-tabs">
          <div class="tab-buttons">
            ${windowsAssets.length > 0 ? `
              <button class="tab-button ${appState.selectedOS === 'windows' ? 'active' : ''}"
                      onclick="setState({selectedOS: 'windows'})" data-tooltip="Windows">
                <span class="material-symbols-outlined">desktop_windows</span>
                <span>Windows</span>
              </button>
            ` : ''}
            ${macAssets.length > 0 ? `
              <button class="tab-button ${appState.selectedOS === 'mac' ? 'active' : ''}"
                      onclick="setState({selectedOS: 'mac'})" data-tooltip="macOS">
                <span class="material-symbols-outlined">laptop_mac</span>
                <span>macOS</span>
              </button>
            ` : ''}
            ${(debAssets.length > 0 || rpmAssets.length > 0) ? `
              <button class="tab-button ${appState.selectedOS === 'linux' ? 'active' : ''}"
                      onclick="setState({selectedOS: 'linux'})" data-tooltip="Linux">
                <span class="material-symbols-outlined">computer</span>
                <span>Linux</span>
              </button>
            ` : ''}
          </div>

          <div class="tab-content">
            ${appState.selectedOS === 'windows' && windowsAssets.length > 0 ? `
              <div class="platform-downloads">
                <h3 class="platform-title">
                  <span class="material-symbols-outlined">desktop_windows</span>
                  Windows
                </h3>
                ${renderPlatformAssets(windowsAssets, 'windows')}
              </div>
            ` : ''}

            ${appState.selectedOS === 'mac' && macAssets.length > 0 ? `
              <div class="platform-downloads">
                <h3 class="platform-title">
                  <span class="material-symbols-outlined">laptop_mac</span>
                  macOS
                </h3>
                <div class="command-box" style="margin-bottom:1rem;">
                  <code>curl -L https://raw.githubusercontent.com/kdroidFilter/Zayit/refs/heads/master/launch.mac | bash</code>
                  <button class="copy-btn" onclick="copyCommand('curl -L https://raw.githubusercontent.com/kdroidFilter/Zayit/refs/heads/master/launch.mac | bash')">
                    <span class="material-symbols-outlined">content_copy</span>
                  </button>
                </div>
                <p style="color:var(--gold-muted);font-size:0.85rem;margin-bottom:1rem;">או הורד ידנית:</p>
                ${renderPlatformAssets(macAssets, 'mac')}
              </div>
            ` : ''}

            ${appState.selectedOS === 'linux' && (debAssets.length > 0 || rpmAssets.length > 0) ? `
              <div class="platform-downloads">
                <h3 class="platform-title">
                  <span class="material-symbols-outlined">computer</span>
                  Linux
                </h3>
                <div class="command-box" style="margin-bottom:1rem;">
                  <code>curl -L https://raw.githubusercontent.com/kdroidFilter/Zayit/refs/heads/master/launch.linux | bash</code>
                  <button class="copy-btn" onclick="copyCommand('curl -L https://raw.githubusercontent.com/kdroidFilter/Zayit/refs/heads/master/launch.linux | bash')">
                    <span class="material-symbols-outlined">content_copy</span>
                  </button>
                </div>
                <p style="color:var(--gold-muted);font-size:0.85rem;margin-bottom:1rem;">או הורד ידנית:</p>
                ${debAssets.length > 0 ? `
                  <div class="distro-group">
                    <h4 class="distro-title">
                      <span class="material-symbols-outlined">package_2</span>
                      Debian/Ubuntu (.deb)
                    </h4>
                    ${renderPlatformAssets(debAssets, 'deb')}
                  </div>
                ` : ''}
                ${rpmAssets.length > 0 ? `
                  <div class="distro-group">
                    <h4 class="distro-title">
                      <span class="material-symbols-outlined">package_2</span>
                      Fedora/RHEL (.rpm)
                    </h4>
                    ${renderPlatformAssets(rpmAssets, 'rpm')}
                  </div>
                ` : ''}
              </div>
            ` : ''}
          </div>
        </div>
      ` : ''}
    </div>
  `;
}

function renderPlatformAssets(assets, type) {
  const archGroups = groupAssetsByArch(assets);

  return `
    <div class="platform-assets">
      ${archGroups.x64?.length > 0 ? `
        <div class="arch-group">
          <div class="arch-label">
            <span class="material-symbols-outlined">${getArchIcon('x64')}</span>
            <span>${archLabelHebrew('x64')}</span>
          </div>
          <div class="arch-downloads">
            ${archGroups.x64.map(asset => `
              <a href="${asset.url}" target="_blank" class="download-chip" data-tooltip="${asset.size}">
                <span class="material-symbols-outlined">download</span>
                <span>${asset.name}</span>
              </a>
            `).join('')}
          </div>
        </div>
      ` : ''}

      ${archGroups.arm64?.length > 0 ? `
        <div class="arch-group">
          <div class="arch-label">
            <span class="material-symbols-outlined">${getArchIcon('arm64')}</span>
            <span>${archLabelHebrew('arm64')}</span>
          </div>
          <div class="arch-downloads">
            ${archGroups.arm64.map(asset => `
              <a href="${asset.url}" target="_blank" class="download-chip" data-tooltip="${asset.size}">
                <span class="material-symbols-outlined">download</span>
                <span>${asset.name}</span>
              </a>
            `).join('')}
          </div>
        </div>
      ` : ''}

      ${archGroups.unknown?.length > 0 ? `
        <div class="arch-group">
          <div class="arch-downloads">
            ${archGroups.unknown.map(asset => `
              <a href="${asset.url}" target="_blank" class="download-chip" data-tooltip="${asset.size}">
                <span class="material-symbols-outlined">download</span>
                <span>${asset.name}</span>
              </a>
            `).join('')}
          </div>
        </div>
      ` : ''}
    </div>
  `;
}

function renderDbSection() {
  const showDbLinks = appState.includeDb;
  const dbToggleButton = appState.dbAssets.length > 0
    ? `
        <button class="toggle-button inline" id="zayit-toggle-db">
          <span class="material-symbols-outlined">
            ${showDbLinks ? "expand_less" : "expand_more"}
          </span>
          <span>${showDbLinks ? "הסתר קבצי DB" : "הצג קבצי DB"}</span>
        </button>
      `
    : "";

  let dbInner = "";
  if (appState.dbLoading) {
    dbInner = `
      <div class="small-text" style="color:var(--gold-soft);">
        טוען קבצי DB…
      </div>
    `;
  } else if (appState.dbError) {
    dbInner = `
      <div class="error-box" style="margin:0;">
        <p class="error-text" style="margin:0;">
          ⚠️ ${appState.dbError}
        </p>
      </div>
    `;
  } else {
    const totalDbSize = appState.dbAssets.reduce((acc, a) => acc + (a.rawSize || 0), 0);
    const dbListHtml =
      appState.dbAssets.length > 0
        ? appState.dbAssets
            .map(
              (a) => `
                <div class="db-item">
                  <div class="asset-line">
                    <div class="asset-meta">
                      <p class="asset-name" style="margin:0;">${a.name}</p>
                      <p class="asset-size" style="margin:0.1rem 0 0 0;">גודל: ${a.size}</p>
                      ${
                        a.sha256
                          ? `<p class="small-text" style="margin-top:0.2rem;">SHA-256: ${a.sha256}</p>`
                          : ""
                      }
                    </div>
                    <a href="${a.url}" target="_blank" class="btn btn-secondary">
                      <span class="material-symbols-outlined">download</span>
                      <span>הורדה</span>
                    </a>
                  </div>
                </div>
              `
            )
            .join("")
        : "";

    if (dbListHtml) {
      dbInner = `
        <div class="info-banner">
          <p class="small-text" style="margin:0;color:var(--gold-soft);font-size:0.9rem;">
            אם אתה מתכנן להתקין את זית ללא חיבור לאינטרנט,
            הורד גם את קבצי מסד הנתונים (הורדה נפרדת).
          </p>
        </div>
        ${dbToggleButton ? `<div class="toggle-row">${dbToggleButton}</div>` : ""}
        ${
          showDbLinks
            ? `
                <p style="color:var(--gold-muted);font-size:0.83rem;margin:0 0 0.5rem 0;">
                  גודל כולל של קבצי מסד הנתונים: ${formatFileSize(totalDbSize)}
                </p>
                <div class="assets-list compact">
                  ${dbListHtml}
                </div>
              `
            : ""
        }
      `;
    } else {
      dbInner = `
        <p style="color:var(--gold-soft);text-align:center;margin:0;font-size:0.85rem;">
          לא נמצאו קבצי DB זמינים.
        </p>
      `;
    }
  }

  return `
    <div class="section section-db">
      <div class="section-header">
        <div class="section-title">
          <span class="material-symbols-outlined">database</span>
          <span>קבצי מסד נתונים</span>
        </div>
      </div>
      <div id="zayit-db-inner">
        ${dbInner}
      </div>
    </div>
  `;
}

// Global functions for copying commands
window.copyCommand = function(command) {
  navigator.clipboard.writeText(command).then(() => {
    const btn = event.target.closest('.copy-btn');
    const originalContent = btn.innerHTML;
    btn.innerHTML = '<span class="material-symbols-outlined">check</span>';
    setTimeout(() => {
      btn.innerHTML = originalContent;
    }, 2000);
  });
};

window.copyMacCommand = function() {
  const command = document.getElementById('mac-command').textContent;
  copyCommand(command);
};

window.copyLinuxCommand = function() {
  const command = document.getElementById('linux-command').textContent;
  copyCommand(command);
};

function attachEventHandlers() {
  const toggleBtn = document.getElementById("zayit-toggle-assets");
  if (toggleBtn) {
    toggleBtn.addEventListener("click", function () {
      setState({ showAllAssets: !appState.showAllAssets });
    });
  }

  const toggleDbBtn = document.getElementById("zayit-toggle-db");
  if (toggleDbBtn) {
    toggleDbBtn.addEventListener("click", function () {
      setState({ includeDb: !appState.includeDb });
    });
  }
}

async function fetchLatestRelease() {
  try {
    const headers = {};
    const resp = await fetch(
      `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest`,
      { headers }
    );
    if (!resp.ok) {
      throw new Error("GitHub API - שגיאה " + resp.status + ". נסה שוב בעוד כמה דקות.");
    }
    const data = await resp.json();
    const assets = (data.assets || []).map((a) => ({
      id: a.id,
      name: a.name,
      url: a.browser_download_url,
      size: formatFileSize(a.size),
      rawSize: a.size,
      uploaded_at: a.updated_at,
    }));
    setState({
      loading: false,
      error: null,
      release: {
        tag_name: data.tag_name,
        name: data.name || data.tag_name,
        body: data.body || "",
        assets: assets,
        created_at: data.created_at,
      },
    });
  } catch (e) {
    setState({
      loading: false,
      error: e.message || String(e),
      release: null,
    });
  }
}

async function fetchDbAssets() {
  try {
    const headers = {};
    const resp = await fetch(
      `https://api.github.com/repos/${DB_OWNER}/${DB_REPO}/releases/latest`,
      { headers }
    );
    if (!resp.ok) {
      throw new Error("GitHub API - שגיאה " + resp.status);
    }
    const data = await resp.json();
    const parts = (data.assets || []).filter(function (a) {
      return /seforim_bundle|part0?1|part0?2|\.part/i.test(a.name);
    });
    const mapped = parts
      .map((a) => ({
        id: a.id,
        name: a.name,
        url: a.browser_download_url,
        size: formatFileSize(a.size),
        rawSize: a.size,
        sha256: a.label || "",
        uploaded_at: a.updated_at,
      }))
      .sort(function (x, y) {
        return x.name.localeCompare(y.name, undefined, { numeric: true });
      });

    setState({
      dbLoading: false,
      dbError: null,
      dbAssets: mapped,
    });
  } catch (e) {
    setState({
      dbLoading: false,
      dbError: e.message || String(e),
      dbAssets: [],
    });
  }
}

window.addEventListener("DOMContentLoaded", async function () {
  // Register service worker for cache control (optional)
  if ('serviceWorker' in navigator) {
    try {
      const registration = await navigator.serviceWorker.register('/service-worker.js');
      console.log('Service Worker registered:', registration);

      // Check for updates every page load
      registration.update();
    } catch (error) {
      console.log('Service Worker registration failed:', error);
    }
  }

  renderApp(); // Render loading state

  // Start platform detection early
  const platform = await detectPlatform();
  appState.platform = platform;

  // Fetch data in parallel
  await Promise.all([
    fetchLatestRelease(),
    fetchDbAssets()
  ]);
});
