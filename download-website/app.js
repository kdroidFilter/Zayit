const GITHUB_OWNER = "kdroidFilter";
const GITHUB_REPO = "Zayit";
const DB_OWNER = "kdroidFilter";
const DB_REPO = "SeforimLibrary";
const BRAND_ICON = "icon.png";

// ==================== i18n System ====================
const SUPPORTED_LANGUAGES = ['en', 'he'];
const DEFAULT_LANGUAGE = 'en';
const STORAGE_KEY = 'language'; // Synced with /website

let translations = {};
let currentLanguage = DEFAULT_LANGUAGE;

// ==================== Theme System ====================
const THEME_STORAGE_KEY = 'theme'; // Synced with /website
let currentTheme = 'system'; // 'light', 'dark', or 'system'

function initTheme() {
  const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
  if (savedTheme && ['light', 'dark', 'system'].includes(savedTheme)) {
    currentTheme = savedTheme;
  }
  applyTheme();

  // Listen for system preference changes
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (currentTheme === 'system') {
      applyTheme();
    }
  });
}

function applyTheme() {
  const root = document.documentElement;
  root.classList.remove('light', 'dark');

  if (currentTheme === 'light') {
    root.classList.add('light');
  } else if (currentTheme === 'dark') {
    root.classList.add('dark');
  }
  // If 'system', no class is added, CSS media query handles it
}

function isDarkMode() {
  if (currentTheme === 'dark') return true;
  if (currentTheme === 'light') return false;
  // System preference
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function toggleTheme() {
  // Cycle: system -> light -> dark -> system
  // Or simpler: just toggle between light and dark based on current resolved state
  if (isDarkMode()) {
    currentTheme = 'light';
  } else {
    currentTheme = 'dark';
  }
  localStorage.setItem(THEME_STORAGE_KEY, currentTheme);
  applyTheme();
  renderApp();
}

window.toggleTheme = toggleTheme;

async function loadTranslations(lang) {
  try {
    const response = await fetch(`i18n/${lang}.json`);
    if (!response.ok) throw new Error(`Failed to load ${lang} translations`);
    return await response.json();
  } catch (e) {
    console.error(`Error loading ${lang} translations:`, e);
    return null;
  }
}

async function initI18n() {
  // Try to get language from localStorage first
  const savedLang = localStorage.getItem(STORAGE_KEY);

  if (savedLang && SUPPORTED_LANGUAGES.includes(savedLang)) {
    currentLanguage = savedLang;
  } else {
    // Detect browser language
    const browserLang = navigator.language?.split('-')[0] ||
                        navigator.languages?.[0]?.split('-')[0] ||
                        DEFAULT_LANGUAGE;
    currentLanguage = SUPPORTED_LANGUAGES.includes(browserLang) ? browserLang : DEFAULT_LANGUAGE;
  }

  // Load translations for all supported languages
  const loadPromises = SUPPORTED_LANGUAGES.map(async (lang) => {
    const data = await loadTranslations(lang);
    if (data) translations[lang] = data;
  });

  await Promise.all(loadPromises);

  // Fallback to default if current language failed to load
  if (!translations[currentLanguage] && translations[DEFAULT_LANGUAGE]) {
    currentLanguage = DEFAULT_LANGUAGE;
  }

  applyLanguageDirection();
}

function setLanguage(lang) {
  if (!SUPPORTED_LANGUAGES.includes(lang) || !translations[lang]) return;

  currentLanguage = lang;
  localStorage.setItem(STORAGE_KEY, lang);
  applyLanguageDirection();
  renderApp();
}

function applyLanguageDirection() {
  const dir = translations[currentLanguage]?.meta?.dir || 'rtl';
  document.documentElement.setAttribute('dir', dir);
  document.documentElement.setAttribute('lang', currentLanguage);

  // Update page title
  const title = t('header.downloadTitle');
  if (title) document.title = title;

  // Update footer
  const footer = document.querySelector('.page-footer');
  if (footer) footer.textContent = t('common.createdBy');
}

function t(key, params = {}) {
  const keys = key.split('.');
  let value = translations[currentLanguage];

  for (const k of keys) {
    if (value === undefined || value === null) return key;
    value = value[k];
  }

  if (typeof value !== 'string') return key;

  // Replace parameters like {status} with actual values
  return value.replace(/\{(\w+)\}/g, (match, param) => {
    return params[param] !== undefined ? params[param] : match;
  });
}

function getCurrentLanguage() {
  return currentLanguage;
}

function getLanguageInfo(lang) {
  return translations[lang]?.meta || { code: lang, name: lang, dir: 'ltr' };
}

// ==================== Platform Detection ====================
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
    if (arch === "unknown") {
      if (/Apple/.test(navigator.vendor) && navigator.maxTouchPoints > 0) {
        arch = "arm64";
      } else {
        arch = null;
      }
    }
    return { os: "mac", arch, isMobile: false };
  }

  if (/linux/i.test(platform) || /linux/i.test(ua)) {
    const distro = /ubuntu|debian/i.test(ua) ? "deb" :
                   /fedora|centos|redhat|rhel|opensuse|suse/i.test(ua) ? "rpm" :
                   "both";
    return {
      os: "linux",
      distro,
      arch: arch === "unknown" ? null : arch,
      isMobile: false
    };
  }

  return { os: "unknown", arch: "unknown", isMobile: false };
}

function osLabel(platform) {
  const os = platform.os;
  return t(`platform.${os}`) || os;
}

function archLabel(arch, osContext) {
  if (!arch) return t('arch.unknown');

  const os = (osContext || "").toLowerCase();

  if (arch === "arm64") {
    if (os === "mac" || os === "darwin" || os === "macos") {
      return t('arch.appleSilicon');
    }
    return t('arch.arm64');
  }

  return t(`arch.${arch}`) || arch;
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

function getLaunchScriptUrl(kind) {
  const file = kind === "mac" ? "launch.mac" : "launch.linux";

  if (typeof window === "undefined" || !window.location) {
    return file;
  }

  const loc = window.location;
  const origin = loc.origin || "";
  const pathname = loc.pathname || "";

  // Get directory path (remove filename like index.html if present)
  let basePath = pathname;
  if (basePath.endsWith('.html')) {
    basePath = basePath.substring(0, basePath.lastIndexOf('/'));
  }
  // Ensure no trailing slash for consistency
  basePath = basePath.replace(/\/+$/, '');

  return origin + basePath + "/" + file;
}

function getLaunchCommand(kind) {
  return "curl -L " + getLaunchScriptUrl(kind) + " | bash";
}

function formatFileSize(bytes) {
  if (!bytes) return "?";
  const mb = Math.round(bytes / 1024 / 1024);
  if (mb > 0) {
    return mb + " " + t('fileSize.mb');
  }
  return t('fileSize.lessThan1mb');
}

function filterAssetsByPlatform(assets, platform) {
  if (!assets || assets.length === 0) return [];

  const list = assets.map((a) => ({
    ...a,
    lname: (a.name || "").toLowerCase(),
  }));

  if (platform.os === "windows") {
    return list.filter(a => /\.(msi|exe)$/i.test(a.name))
      .sort((a, b) => {
        if (a.lname.endsWith('.exe') && !b.lname.endsWith('.exe')) return -1;
        if (!a.lname.endsWith('.exe') && b.lname.endsWith('.exe')) return 1;
        return 0;
      });
  }

  if (platform.os === "linux") {
    if (platform.distro === "deb") {
      return list.filter(a => /\.deb$/i.test(a.name));
    } else if (platform.distro === "rpm") {
      return list.filter(a => /\.rpm$/i.test(a.name));
    } else {
      return list.filter(a => /\.(deb|rpm)$/i.test(a.name))
        .sort((a, b) => {
          if (a.lname.endsWith('.deb') && !b.lname.endsWith('.deb')) return -1;
          if (!a.lname.endsWith('.deb') && b.lname.endsWith('.deb')) return 1;
          return 0;
        });
    }
  }

  if (platform.os === "mac") {
    return list.filter(a => /\.dmg$/i.test(a.name));
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

// ==================== State Management ====================
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

// ==================== Render Functions ====================
function renderBackButton() {
  const label = t('nav.backToSite');
  return `
    <a href="/Zayit/" class="back-button" title="${label}" aria-label="${label}">
      <span class="material-symbols-outlined">arrow_back</span>
    </a>
  `;
}

function renderHeaderControls() {
  const currentLangInfo = getLanguageInfo(currentLanguage);
  const isDark = isDarkMode();
  const themeIcon = isDark ? 'light_mode' : 'dark_mode';
  const themeLabel = isDark ? t('theme.light') : t('theme.dark');

  return `
    <div class="header-controls">
      <button class="theme-toggle" onclick="toggleTheme()" title="${themeLabel}" aria-label="${themeLabel}">
        <span class="material-symbols-outlined">${themeIcon}</span>
      </button>
      <div class="language-selector">
        <button class="lang-btn" onclick="toggleLanguageMenu(event)">
          <span class="material-symbols-outlined">translate</span>
          <span class="lang-name">${currentLangInfo.name}</span>
          <span class="material-symbols-outlined lang-arrow">expand_more</span>
        </button>
        <div class="lang-menu" id="lang-menu">
          ${SUPPORTED_LANGUAGES.map(lang => {
            const info = getLanguageInfo(lang);
            const isActive = lang === currentLanguage ? 'active' : '';
            return `
              <button class="lang-option ${isActive}" onclick="setLanguage('${lang}')">
                <span>${info.name}</span>
                ${lang === currentLanguage ? '<span class="material-symbols-outlined">check</span>' : ''}
              </button>
            `;
          }).join('')}
        </div>
      </div>
    </div>
  `;
}

window.toggleLanguageMenu = function(event) {
  event.stopPropagation();
  const menu = document.getElementById('lang-menu');
  if (menu) {
    menu.classList.toggle('open');
  }
};

// Close language menu when clicking elsewhere
document.addEventListener('click', function(e) {
  const menu = document.getElementById('lang-menu');
  if (menu && !e.target.closest('.language-selector')) {
    menu.classList.remove('open');
  }
});

async function renderApp() {
  const root = document.getElementById("zayit-root");
  if (!root) return;

  if (appState.loading) {
    root.innerHTML = `
      <div class="card">
        <div class="card-inner">
          ${renderBackButton()}
          ${renderHeaderControls()}
          <div class="loading-screen">
            <div class="spinner"></div>
            <p style="color:var(--gold-soft);font-size:1.05rem;margin:0;">
              ${t('common.loading')}
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
          ${renderBackButton()}
          ${renderHeaderControls()}
          <div class="center" style="margin-bottom:1.8rem;">
            <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
            <h1 class="title">${t('header.title')}</h1>
            <p class="subtitle">
              <span class="material-symbols-outlined">${getOSIcon(platform.os)}</span>
              ${osLabel(platform)}
            </p>
          </div>

          <div class="section section-box" style="text-align:center;">
            <span class="material-symbols-outlined" style="font-size:3rem;color:var(--gold-muted);margin-bottom:1rem;display:block;">
              mobile_off
            </span>
            <h2 style="color:var(--text-main);margin:0 0 0.5rem 0;">
              ${t('mobile.notSupported')}
            </h2>
            <p style="color:var(--gold-soft);margin:0;font-size:0.95rem;">
              ${t('mobile.desktopOnly')}<br/>
              ${t('mobile.useDesktop')}
            </p>
          </div>

          ${renderFooter()}
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
          ${renderBackButton()}
          ${renderHeaderControls()}
          <div class="center" style="margin-bottom:1.8rem;">
            <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
            <h1 class="title">${t('header.title')} — ${t('common.download')}</h1>
          </div>

          <div class="section section-box" style="text-align:center;">
            <span class="material-symbols-outlined" style="font-size:3rem;color:var(--gold-muted);margin-bottom:1rem;display:block;">
              help_outline
            </span>
            <h2 style="color:var(--text-main);margin:0 0 0.5rem 0;">
              ${t('unknownOS.title')}
            </h2>
            <p style="color:var(--gold-soft);margin:0 0 1.5rem 0;font-size:0.95rem;">
              ${t('unknownOS.description')}<br/>
              ${t('unknownOS.selectManually')}
            </p>
            ${renderManualDownloadLinks(assets)}
          </div>

          ${renderFooter()}
        </div>
      </div>
    `;
    attachEventHandlers();
    return;
  }

  const errorBlock = appState.error
    ? `
      <div class="error-box">
        <p class="error-text"><strong>${t('common.error')}:</strong> ${appState.error}</p>
        <p class="error-help">
          ${t('errors.connectionIssue')}
        </p>
      </div>
    `
    : "";

  let mainDownloadBlock = "";

  // Special handling for macOS - show curl command
  if (platform.os === "mac") {
    const macAssets = filterAssetsByPlatform(assets, platform);

    mainDownloadBlock = `
      <div class="section section-box">
        <h2 class="section-title">
          <span class="material-symbols-outlined">terminal</span>
          <span>${t('install.autoInstallMac')}</span>
        </h2>
        <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.95rem;">
          ${t('install.copyAndRun')}
        </p>
        <div class="command-box">
          <code id="mac-command">${getLaunchCommand('mac')}</code>
          <button class="copy-btn" onclick="copyMacCommand(this)">
            <span class="material-symbols-outlined">content_copy</span>
          </button>
        </div>
        <p style="color:var(--gold-muted);margin:1rem 0 0 0;font-size:0.85rem;">
          <span class="material-symbols-outlined" style="font-size:0.95rem;vertical-align:middle;">info</span>
          ${t('install.scriptInfo')}
        </p>
      </div>

      ${macAssets.length > 0 ? `
        <div class="section section-box">
          <h2 class="section-title">
            <span class="material-symbols-outlined">download</span>
            <span>${t('install.manualDownload')}</span>
          </h2>

          <button class="toggle-button" onclick="setState({showAllAssets: !appState.showAllAssets})" style="margin-bottom:1rem;">
            <span class="material-symbols-outlined">
              ${appState.showAllAssets ? 'expand_less' : 'expand_more'}
            </span>
            <span>${appState.showAllAssets ? t('common.hide') : t('common.show')} ${t('install.showManualOptions')}</span>
          </button>

          ${appState.showAllAssets ? renderPlatformAssets(macAssets, 'mac') : ''}
        </div>
      ` : ''}
    `;
  } else if (platform.os === "windows") {
    const windowsAssets = filterAssetsByPlatform(assets, platform);
    const archGroups = groupAssetsByArch(windowsAssets);

    if (platform.arch && archGroups[platform.arch]?.length > 0) {
      const archAssets = archGroups[platform.arch];
      const exeAsset = archAssets.find(a => a.name.toLowerCase().endsWith('.exe'));
      const msiAsset = archAssets.find(a => a.name.toLowerCase().endsWith('.msi'));
      const recommended = exeAsset || archAssets[0];
      const otherArch = platform.arch === 'x64' ? 'arm64' : 'x64';
      const hasOtherArch = archGroups[otherArch]?.length > 0;
      const hasSupplementary = msiAsset || hasOtherArch;

      mainDownloadBlock = `
        <div class="section section-box">
          <h2 class="section-title">
            <span class="material-symbols-outlined">download</span>
            <span>${t('windows.downloadSoftware')}</span>
          </h2>
          <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.95rem;">
            ${t('windows.recommendedFile')}:
            <strong>${recommended.name}</strong>
            (${recommended.size})
          </p>
          <div class="btn-row">
            <a href="${recommended.url}" target="_blank" class="btn btn-primary">
              <span class="material-symbols-outlined">download</span>
              <span>${t('common.downloadNow')}</span>
            </a>
          </div>
          ${hasSupplementary ? `
            <div style="margin-top:1rem;text-align:center;">
              <button class="toggle-button" onclick="setState({showAllAssets: !appState.showAllAssets})">
                <span class="material-symbols-outlined">
                  ${appState.showAllAssets ? 'expand_less' : 'expand_more'}
                </span>
                <span>${appState.showAllAssets ? t('common.hide') : t('common.show')} ${t('windows.showMoreOptions')}</span>
              </button>
            </div>
            ${appState.showAllAssets ? `
              <div style="margin-top:1rem;padding-top:1rem;border-top:1px solid rgba(255,215,0,0.1);">
                ${msiAsset ? `
                  <p style="color:var(--gold-soft);font-size:0.9rem;margin:0 0 0.75rem 0;">
                    ${t('windows.alternativeFormat')}
                  </p>
                  <a href="${msiAsset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-bottom:0.5rem;">
                    <span class="material-symbols-outlined">download</span>
                    <span>${msiAsset.name} (${msiAsset.size})</span>
                  </a>
                ` : ''}
                ${hasOtherArch ? `
                  <p style="color:var(--gold-soft);font-size:0.9rem;margin:${msiAsset ? '1rem' : '0'} 0 0.75rem 0;">
                    ${t('windows.otherArchitectures')}
                  </p>
                  ${archGroups[otherArch].map(asset => `
                    <a href="${asset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-bottom:0.5rem;">
                      <span class="material-symbols-outlined">${getArchIcon(otherArch)}</span>
                      <span>${asset.name} (${asset.size})</span>
                    </a>
                  `).join('')}
                ` : ''}
              </div>
            ` : ''}
          ` : ''}
        </div>
      `;
    } else {
      mainDownloadBlock = renderWindowsArchOptions(archGroups);
    }
  } else if (platform.os === "linux") {
    const linuxAssets = filterAssetsByPlatform(assets, platform);
    const debAssets = linuxAssets.filter(a => a.name.toLowerCase().endsWith('.deb'));
    const rpmAssets = linuxAssets.filter(a => a.name.toLowerCase().endsWith('.rpm'));

    mainDownloadBlock = `
      <div class="section section-box">
        <h2 class="section-title">
          <span class="material-symbols-outlined">terminal</span>
          <span>${t('install.autoInstallLinux')}</span>
        </h2>
        <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.95rem;">
          ${t('install.copyAndRun')}
        </p>
        <div class="command-box">
          <code id="linux-command">${getLaunchCommand('linux')}</code>
          <button class="copy-btn" onclick="copyLinuxCommand(this)">
            <span class="material-symbols-outlined">content_copy</span>
          </button>
        </div>
        <p style="color:var(--gold-muted);margin:1rem 0 0 0;font-size:0.85rem;">
          <span class="material-symbols-outlined" style="font-size:0.95rem;vertical-align:middle;">info</span>
          ${t('install.scriptInfoLinux')}
        </p>
      </div>

      ${(debAssets.length > 0 || rpmAssets.length > 0) ? `
        <div class="section section-box">
          <h2 class="section-title">
            <span class="material-symbols-outlined">download</span>
            <span>${t('install.manualDownload')}</span>
          </h2>

          <button class="toggle-button" onclick="setState({showAllAssets: !appState.showAllAssets})" style="margin-bottom:1rem;">
            <span class="material-symbols-outlined">
              ${appState.showAllAssets ? 'expand_less' : 'expand_more'}
            </span>
            <span>${appState.showAllAssets ? t('common.hide') : t('common.show')} ${t('install.showManualOptions')}</span>
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

  const dbSection = renderDbSection();
  const crossPlatformSection = renderCrossPlatformSection(assets);

  root.innerHTML = `
    <div class="card">
      <div class="card-inner">
        ${renderBackButton()}
          ${renderHeaderControls()}
        <div class="center" style="margin-bottom:1.8rem;">
          <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
          <h1 class="title">${t('header.downloadTitle')}</h1>
          <p class="subtitle">
            <span class="material-symbols-outlined">${getOSIcon(platform.os)}</span>
            ${t('header.detected')}: <strong>${osLabel(platform)}</strong>
            ${platform.arch && platform.arch !== 'unknown' ? `
              &bull;
              <span class="material-symbols-outlined" style="font-size:1rem;">${getArchIcon(platform.arch)}</span>
              <strong>${archLabel(platform.arch, platform.os)}</strong>
            ` : ''}
          </p>
          ${release ? `<p class="version-text">${t('common.version')} ${release.tag_name}</p>` : ""}
        </div>

        ${errorBlock}
        ${mainDownloadBlock}
        ${crossPlatformSection}
        ${dbSection}

        ${renderFooter()}
      </div>
    </div>
  `;

  attachEventHandlers();
}

function renderFooter() {
  return `
    <div class="footer">
      <div class="footer-links">
        <a href="https://github.com/kdroidFilter/Zayit" target="_blank" class="footer-link" data-tooltip="${t('common.sourceCode')}">
          <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
          </svg>
        </a>
        <a href="https://ko-fi.com/lomityaesh" target="_blank" class="footer-link footer-link-donate" data-tooltip="${t('common.support')}">
          <svg class="footer-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
          </svg>
        </a>
      </div>
    </div>
  `;
}

function renderWindowsArchOptions(archGroups) {
  const renderArchGroup = (assets, archKey, archDesc) => {
    const exeAsset = assets.find(a => a.name.toLowerCase().endsWith('.exe'));
    const msiAsset = assets.find(a => a.name.toLowerCase().endsWith('.msi'));
    const primary = exeAsset || assets[0];

    return `
      <div class="arch-option">
        <div class="arch-header">
          <span class="material-symbols-outlined">${getArchIcon(archKey)}</span>
          <h3>${archLabel(archKey, 'windows')}</h3>
        </div>
        <p class="arch-desc">${archDesc}</p>
        <a href="${primary.url}" target="_blank" class="btn btn-primary" style="width:100%;margin-top:0.5rem;">
          <span class="material-symbols-outlined">download</span>
          <span>${primary.name} (${primary.size})</span>
        </a>
        ${msiAsset && exeAsset ? `
          <a href="${msiAsset.url}" target="_blank" class="btn btn-secondary" style="width:100%;margin-top:0.5rem;">
            <span class="material-symbols-outlined">download</span>
            <span>${msiAsset.name} (${msiAsset.size})</span>
          </a>
        ` : ''}
      </div>
    `;
  };

  return `
    <div class="section section-box">
      <h2 class="section-title">
        <span class="material-symbols-outlined">download</span>
        <span>${t('windows.selectArchitecture')}</span>
      </h2>
      <p style="color:var(--gold-soft);margin:0 0 1.5rem 0;font-size:0.9rem;">
        ${t('windows.archNotDetected')}
      </p>

      <div class="arch-options">
        ${archGroups.x64?.length > 0 ? renderArchGroup(archGroups.x64, 'x64', t('windows.mostModern')) : ''}
        ${archGroups.arm64?.length > 0 ? renderArchGroup(archGroups.arm64, 'arm64', t('windows.surfaceAndSimilar')) : ''}
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
            <span style="color:var(--gold-soft);font-size:0.9rem;">${archLabel('x64', 'linux')}</span>
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
            <span style="color:var(--gold-soft);font-size:0.9rem;">${archLabel('arm64', 'linux')}</span>
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

function renderManualDownloadLinks(assets) {
  if (!assets || assets.length === 0) {
    return `<p style="color:var(--gold-soft);">${t('unknownOS.noFilesFound')}</p>`;
  }

  return `
    <div class="assets-list compact">
      ${assets.map(asset => `
        <div class="asset-item compact">
          <div class="asset-line">
            <div class="asset-meta">
              <p class="asset-name">${asset.name}</p>
              <p class="asset-size">${t('common.size')}: ${asset.size}</p>
            </div>
            <a href="${asset.url}" target="_blank" class="btn btn-secondary">
              <span class="material-symbols-outlined">download</span>
              <span>${t('common.download')}</span>
            </a>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

function renderCrossPlatformSection(allAssets) {
  if (!allAssets || allAssets.length === 0) return '';

  const windowsAssets = allAssets.filter(a => /\.(msi|exe)$/i.test(a.name));
  const macAssets = allAssets.filter(a => /\.dmg$/i.test(a.name));
  const debAssets = allAssets.filter(a => /\.deb$/i.test(a.name));
  const rpmAssets = allAssets.filter(a => /\.rpm$/i.test(a.name));

  if (windowsAssets.length === 0 && macAssets.length === 0 && debAssets.length === 0 && rpmAssets.length === 0) {
    return '';
  }

  return `
    <div class="section section-box section-cross-platform">
      <div class="section-header">
        <h2 class="section-title">
          <span class="material-symbols-outlined">devices</span>
          <span>${t('crossPlatform.title')}</span>
        </h2>
        <button class="toggle-button inline" onclick="setState({showCrossPlatform: !appState.showCrossPlatform})">
          <span class="material-symbols-outlined">
            ${appState.showCrossPlatform ? 'expand_less' : 'expand_more'}
          </span>
          <span>${appState.showCrossPlatform ? t('common.hide') : t('common.show')} ${t('crossPlatform.showOther')}</span>
        </button>
      </div>

      ${appState.showCrossPlatform ? `
        <div class="os-tabs">
          <div class="tab-buttons">
            ${windowsAssets.length > 0 ? `
              <button class="tab-button ${appState.selectedOS === 'windows' ? 'active' : ''}"
                      onclick="setState({selectedOS: 'windows'})" data-tooltip="${t('platform.windows')}">
                <span class="material-symbols-outlined">desktop_windows</span>
                <span>${t('platform.windows')}</span>
              </button>
            ` : ''}
            ${macAssets.length > 0 ? `
              <button class="tab-button ${appState.selectedOS === 'mac' ? 'active' : ''}"
                      onclick="setState({selectedOS: 'mac'})" data-tooltip="${t('platform.mac')}">
                <span class="material-symbols-outlined">laptop_mac</span>
                <span>${t('platform.mac')}</span>
              </button>
            ` : ''}
            ${(debAssets.length > 0 || rpmAssets.length > 0) ? `
              <button class="tab-button ${appState.selectedOS === 'linux' ? 'active' : ''}"
                      onclick="setState({selectedOS: 'linux'})" data-tooltip="${t('platform.linux')}">
                <span class="material-symbols-outlined">computer</span>
                <span>${t('platform.linux')}</span>
              </button>
            ` : ''}
          </div>

          <div class="tab-content">
            ${appState.selectedOS === 'windows' && windowsAssets.length > 0 ? `
              <div class="platform-downloads">
                <h3 class="platform-title">
                  <span class="material-symbols-outlined">desktop_windows</span>
                  ${t('platform.windows')}
                </h3>
                ${renderPlatformAssets(windowsAssets, 'windows')}
              </div>
            ` : ''}

            ${appState.selectedOS === 'mac' && macAssets.length > 0 ? `
              <div class="platform-downloads">
                <h3 class="platform-title">
                  <span class="material-symbols-outlined">laptop_mac</span>
                  ${t('platform.mac')}
                </h3>
                <div class="command-box" style="margin-bottom:1rem;">
                  <code>${getLaunchCommand('mac')}</code>
                  <button class="copy-btn" onclick="copyCommand('${getLaunchCommand('mac')}', this)">
                    <span class="material-symbols-outlined">content_copy</span>
                  </button>
                </div>
                <p style="color:var(--gold-muted);font-size:0.85rem;margin-bottom:1rem;">${t('crossPlatform.orDownloadManually')}</p>
                ${renderPlatformAssets(macAssets, 'mac')}
              </div>
            ` : ''}

            ${appState.selectedOS === 'linux' && (debAssets.length > 0 || rpmAssets.length > 0) ? `
              <div class="platform-downloads">
                <h3 class="platform-title">
                  <span class="material-symbols-outlined">computer</span>
                  ${t('platform.linux')}
                </h3>
                <div class="command-box" style="margin-bottom:1rem;">
                  <code>${getLaunchCommand('linux')}</code>
                  <button class="copy-btn" onclick="copyCommand('${getLaunchCommand('linux')}', this)">
                    <span class="material-symbols-outlined">content_copy</span>
                  </button>
                </div>
                <p style="color:var(--gold-muted);font-size:0.85rem;margin-bottom:1rem;">${t('crossPlatform.orDownloadManually')}</p>
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
  const osContext =
    type === 'mac'
      ? 'mac'
      : type === 'windows'
      ? 'windows'
      : type === 'deb' || type === 'rpm'
      ? 'linux'
      : undefined;

  return `
    <div class="platform-assets">
      ${archGroups.x64?.length > 0 ? `
        <div class="arch-group">
          <div class="arch-label">
            <span class="material-symbols-outlined">${getArchIcon('x64')}</span>
            <span>${archLabel('x64', osContext)}</span>
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
            <span>${archLabel('arm64', osContext)}</span>
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
          <span>${showDbLinks ? t('database.hideFiles') : t('database.showFiles')}</span>
        </button>
      `
    : "";

  let dbInner = "";
  if (appState.dbLoading) {
    dbInner = `
      <div class="small-text" style="color:var(--gold-soft);">
        ${t('database.loading')}
      </div>
    `;
  } else if (appState.dbError) {
    dbInner = `
      <div class="error-box" style="margin:0;">
        <p class="error-text" style="margin:0;">
          ${appState.dbError}
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
                      <p class="asset-size" style="margin:0.1rem 0 0 0;">${t('common.size')}: ${a.size}</p>
                      ${
                        a.sha256
                          ? `<p class="small-text" style="margin-top:0.2rem;">SHA-256: ${a.sha256}</p>`
                          : ""
                      }
                    </div>
                    <a href="${a.url}" target="_blank" class="btn btn-secondary">
                      <span class="material-symbols-outlined">download</span>
                      <span>${t('common.download')}</span>
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
          <p class="small-text" style="margin:0;color:var(--text-main);font-size:0.9rem;">
            ${t('database.offlineInfo')}
          </p>
        </div>
        ${dbToggleButton ? `<div class="toggle-row">${dbToggleButton}</div>` : ""}
        ${
          showDbLinks
            ? `
                <p style="color:var(--gold-muted);font-size:0.83rem;margin:0 0 0.5rem 0;">
                  ${t('database.totalSize')}: ${formatFileSize(totalDbSize)}
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
          ${t('database.noFilesAvailable')}
        </p>
      `;
    }
  }

  return `
    <div class="section section-db">
      <div class="section-header">
        <div class="section-title">
          <span class="material-symbols-outlined">database</span>
          <span>${t('database.title')}</span>
        </div>
      </div>
      <div id="zayit-db-inner">
        ${dbInner}
      </div>
    </div>
  `;
}

// ==================== Event Handlers ====================
window.copyCommand = function(command, btn) {
  const button = btn || (typeof event !== 'undefined' ? event.target.closest('.copy-btn') : null);
  if (!button) return;

  const originalContent = button.innerHTML;
  const showCheck = () => {
    button.innerHTML = '<span class="material-symbols-outlined">check</span>';
    setTimeout(() => {
      button.innerHTML = originalContent;
    }, 2000);
  };

  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(command).then(showCheck, showCheck);
  } else {
    showCheck();
  }
};

window.copyMacCommand = function(button) {
  const command = document.getElementById('mac-command').textContent;
  copyCommand(command, button);
};

window.copyLinuxCommand = function(button) {
  const command = document.getElementById('linux-command').textContent;
  copyCommand(command, button);
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

// ==================== API Calls ====================
async function fetchLatestRelease() {
  try {
    const headers = {};
    const resp = await fetch(
      `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest`,
      { headers }
    );
    if (!resp.ok) {
      throw new Error(t('errors.githubError', { status: resp.status }));
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
      throw new Error(t('errors.githubError', { status: resp.status }));
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

// ==================== Initialization ====================
window.addEventListener("DOMContentLoaded", async function () {
  // Register service worker for cache control (optional)
  if ('serviceWorker' in navigator) {
    try {
      const registration = await navigator.serviceWorker.register('/service-worker.js');
      console.log('Service Worker registered:', registration);
      registration.update();
    } catch (error) {
      console.log('Service Worker registration failed:', error);
    }
  }

  // Initialize i18n system first
  await initI18n();

  // Initialize theme system
  initTheme();

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
