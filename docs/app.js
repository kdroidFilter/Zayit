const GITHUB_OWNER = "kdroidFilter";
const GITHUB_REPO = "SeforimApp";
const DB_OWNER = "kdroidFilter";
const DB_REPO = "SeforimLibrary";
const BRAND_ICON =
  "https://raw.githubusercontent.com/kdroidFilter/SeforimApp/master/SeforimApp/desktopAppIcons/LinuxIcon.png";

function detectPlatform() {
  if (typeof navigator === "undefined") {
    return { os: "deb", arch: "amd64" };
  }

  const ua = navigator.userAgent || "";
  const uaData = navigator.userAgentData || {};
  const platform = (uaData.platform || navigator.platform || "").toLowerCase();
  const archHint = (uaData.architecture || ua).toLowerCase();
  const isArm = /arm64|aarch64|arm/.test(archHint);

  if (/win/i.test(platform) || /windows/i.test(ua)) {
    return { os: "windows", arch: isArm ? "arm64" : "x64" };
  }

  if (/mac/i.test(platform) || /macintosh|mac os x/i.test(ua)) {
    return { os: "mac", arch: isArm ? "arm64" : "x64" };
  }

  if (/linux/i.test(platform) || /linux/i.test(ua)) {
    return {
      os: /fedora|centos|redhat|rhel|opensuse|suse/i.test(ua) ? "rpm" : "deb",
      arch: isArm ? "arm64" : "amd64",
    };
  }

  return { os: "deb", arch: "amd64" };
}

function osLabelHebrew(osCode) {
  const labels = {
    windows: "ווינדוס",
    mac: "מק (macOS)",
    deb: "לינוקס (deb)",
    rpm: "לינוקס (rpm)",
  };
  return labels[osCode] || osCode;
}

function archLabelHebrew(arch) {
  const labels = {
    x64: "x64",
    amd64: "x64",
    arm64: "ARM64",
    arm: "ARM",
  };
  return labels[arch] || arch;
}

function inferArchFromName(name, fallback) {
  const lname = (name || "").toLowerCase();
  if (/arm64|aarch64/.test(lname)) return "arm64";
  if (/x86_64|amd64|x64/.test(lname)) return "x64";
  if (/arm/.test(lname)) return "arm";
  return fallback;
}

function formatFileSize(bytes) {
  if (!bytes) return "?";
  const mb = Math.round(bytes / 1024 / 1024);
  return mb > 0 ? mb + " מ״ב" : "< 1 מ״ב";
}

function pickBestAsset(assets) {
  if (!assets || assets.length === 0) return null;
  const { os, arch } = detectPlatform();
  const list = assets.map((a) => ({
    ...a,
    lname: (a.name || "").toLowerCase(),
  }));

  if (os === "windows") {
    const tries = [/x64.*\.msi$/, /x64.*\.exe$/, /_x64\.msi$/, /_x64\.exe$/];
    for (const r of tries) {
      const f = list.find((a) => r.test(a.lname));
      if (f) return f;
    }
  }

  if (os === "mac") {
    const f = list.find((a) => /\.(pkg|dmg)$/.test(a.lname));
    if (f) return f;
  }

  if (os === "rpm") {
    const archMarker = arch === "arm64" ? "aarch64|arm64" : "x86_64|amd64|x64";
    const re = new RegExp(archMarker + ".*\\.rpm|\\." + arch + "\\.rpm");
    const f = list.find((a) => re.test(a.lname));
    if (f) return f;
  }

  if (os === "deb") {
    const archMarker = arch === "arm64" ? "arm64|aarch64" : "amd64|x86_64|x64";
    const re = new RegExp(archMarker + ".*\\.deb|\\." + arch + "\\.deb");
    const f = list.find((a) => re.test(a.lname));
    if (f) return f;
  }

  const preferred = [".msi", ".exe", ".pkg", ".dmg", ".deb", ".rpm"];
  for (const ext of preferred) {
    const f = list.find((a) => a.lname.endsWith(ext));
    if (f) return f;
  }
  return list[0] || null;
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
};

function setState(patch) {
  appState = Object.assign({}, appState, patch);
  renderApp();
}

function renderApp() {
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

  const detected = detectPlatform();
  const release = appState.release;
  const assets = release ? release.assets || [] : [];
  const best = release ? pickBestAsset(assets) : null;
  const displayArch =
    best && best.name ? inferArchFromName(best.name, detected.arch) : detected.arch;

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
  let noAssetsBlock = "";
  if (!appState.error && best) {
    mainDownloadBlock = `
      <div class="section section-box">
        <h2 class="section-title">
          <span class="material-symbols-outlined">download</span>
          <span>הורדת התוכנה</span>
        </h2>
        <p style="color:var(--gold-soft);margin:0 0 1rem 0;font-size:0.9rem;">
          קובץ מומלץ בשבילך:
          <strong>${best.name}</strong>
          (${best.size})
        </p>
        <div class="btn-row">
          <a href="${best.url}" target="_blank" class="btn btn-primary">
            <span class="material-symbols-outlined">download</span>
            <span>הורד עכשיו</span>
          </a>
        </div>
      </div>
    `;
  } else if (!appState.error && !best) {
    noAssetsBlock = `
      <div class="section section-box" style="text-align:center;">
        <p style="margin:0;color:var(--gold-soft);font-size:0.9rem;">
          לא נמצאו קבצים זמינים להורדה לגרסה זו.
        </p>
      </div>
    `;
  }

  const otherAssets = assets.filter((a) => a !== best);
  const showAll = appState.showAllAssets;
  let assetsList = "";
  if (otherAssets.length > 0 || (!best && assets.length > 0)) {
    const listSource = best && !showAll ? [] : otherAssets;
    const listToRender = !best ? assets : listSource;
    const shouldRenderList = listToRender.length > 0;

    const toggleButton =
      best && otherAssets.length > 0
        ? `
            <button class="toggle-button inline" id="zayit-toggle-assets">
              <span class="material-symbols-outlined">
                ${showAll ? "expand_less" : "expand_more"}
              </span>
              <span>${showAll ? "הסתר" : "הצג ארכיטקטורות נוספות"}</span>
            </button>
          `
        : "";

    const listHtml = shouldRenderList
      ? `
          <div class="assets-list compact">
            ${listToRender
              .map(
                (a) => `
                  <div class="asset-item compact">
                    <div class="asset-line">
                      <div class="asset-meta">
                        <p class="asset-name" style="margin:0;">${a.name}</p>
                        <p class="asset-size" style="margin:0.1rem 0 0 0;">גודל: ${a.size}</p>
                      </div>
                      <a href="${a.url}" target="_blank" class="btn btn-secondary">
                        <span class="material-symbols-outlined">download</span>
                        <span>הורדה</span>
                      </a>
                    </div>
                  </div>
                `
              )
              .join("")}
          </div>
        `
      : "";

    assetsList = `
      <div class="section section-box">
        <div class="section-header">
          <div class="section-title">
            <span class="material-symbols-outlined">folder_open</span>
            <span>ארכיטקטורות אחרות</span>
          </div>
          ${toggleButton}
        </div>
        ${listHtml}
      </div>
    `;
  }

  const assetsToggleBlock = assetsList;

  let noAssetsAllBlock = "";
  if (!appState.error && assets.length === 0) {
    noAssetsAllBlock = `
      <div class="section section-box" style="text-align:center;">
        <p style="margin:0;color:var(--gold-soft);font-size:0.9rem;">
          לא נמצאו קבצים זמינים עבור הגרסה העדכנית.
        </p>
      </div>
    `;
  }

  const resolvedNoAssetsBlock = noAssetsBlock || noAssetsAllBlock;

  const showDbLinks = appState.includeDb;
  const dbToggleButton =
    appState.dbAssets.length > 0
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

  const dbSection = `
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

  root.innerHTML = `
    <div class="card">
      <div class="card-inner">
        <div class="center" style="margin-bottom:1.8rem;">
          <img src="${BRAND_ICON}" alt="Zayit logo" class="header-logo" />
          <h1 class="title">זית — הורדה מהירה</h1>
          <p class="subtitle">
            <span class="material-symbols-outlined">desktop_windows</span>
            זוהה:
            <strong>${osLabelHebrew(detected.os)}</strong>
            •
            <strong>${archLabelHebrew(displayArch)}</strong>
          </p>
          ${
            release
              ? `<p class="version-text">גרסה ${release.tag_name}</p>`
              : ""
          }
        </div>

        ${errorBlock}
        ${mainDownloadBlock}
        ${resolvedNoAssetsBlock}
        ${assetsToggleBlock}
        ${dbSection}

        <div class="footer">
          נוצר על ידי Elyahou Gambache
        </div>
      </div>
    </div>
  `;

  attachEventHandlers();
}

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

  const includeDbCheckbox = document.getElementById("zayit-include-db");
  if (includeDbCheckbox) {
    includeDbCheckbox.addEventListener("change", function (e) {
      setState({ includeDb: e.target.checked });
    });
  }
}

async function fetchLatestRelease() {
  try {
    const headers = {};
    const resp = await fetch(
      "https://api.github.com/repos/" +
        GITHUB_OWNER +
        "/" +
        GITHUB_REPO +
        "/releases/latest",
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
      "https://api.github.com/repos/" +
        DB_OWNER +
        "/" +
        DB_REPO +
        "/releases/latest",
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

window.addEventListener("DOMContentLoaded", function () {
  renderApp(); // Render loading state
  fetchLatestRelease();
  fetchDbAssets();
});
