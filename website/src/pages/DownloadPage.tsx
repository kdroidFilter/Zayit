import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, Check, Copy, Download, ExternalLink, HardDrive, Smartphone, Sparkles } from 'lucide-react';
import { Navigation } from '../components/Navigation';
import { SiteFooter } from '../components/SiteFooter';
import { useDownloadCount } from '../hooks/useDownloadCount';
import { press, reveal, RISE, settle } from '../motion';

const APP_REPO = 'kdroidFilter/Zayit';
const DB_REPO = 'kdroidFilter/SeforimLibrary';
const RELEASES_URL = `https://github.com/${APP_REPO}/releases/latest`;

type Os = 'mac' | 'windows' | 'linux';
type Arch = 'x64' | 'arm64';
type Format = 'exe' | 'msi' | 'dmg' | 'deb' | 'rpm' | 'pacman';

const OSES: Os[] = ['mac', 'windows', 'linux'];

interface Asset {
  name: string;
  url: string;
  size: number;
}

interface Package extends Asset {
  os: Os;
  format: Format;
  arch: Arch;
}

interface Release {
  version: string;
  date: string;
  url: string;
  packages: Package[];
}

interface Detected {
  os: Os | null;
  arch: Arch | null;
  mobile: boolean;
}

const FORMAT_OS: Record<Format, Os> = { exe: 'windows', msi: 'windows', dmg: 'mac', deb: 'linux', rpm: 'linux', pacman: 'linux' };

/** What a release file is, or null for the files users don't install (updater feeds, signatures, the updater's NSIS, mac zips). */
function toPackage(asset: Asset): Package | null {
  const ext = asset.name.match(/\.(exe|msi|dmg|deb|rpm|pacman)$/i)?.[1].toLowerCase() as Format | undefined;
  if (!ext || /-nsis\.exe$/i.test(asset.name)) return null;
  const arch: Arch = /arm64|aarch64/i.test(asset.name) ? 'arm64' : 'x64';
  return { ...asset, os: FORMAT_OS[ext], format: ext, arch };
}

function formatSize(bytes: number): string {
  const mb = bytes / 1024 / 1024;
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.max(1, Math.round(mb))} MB`;
}

async function fetchLatest(repo: string) {
  const resp = await fetch(`https://api.github.com/repos/${repo}/releases/latest`);
  if (!resp.ok) throw new Error(`GitHub ${resp.status}`);
  return resp.json();
}

type GithubAsset = { name: string; browser_download_url: string; size: number };
const toAsset = (a: GithubAsset): Asset => ({ name: a.name, url: a.browser_download_url, size: a.size });

async function detect(): Promise<Detected> {
  const ua = navigator.userAgent;
  if (/iPhone|iPad|iPod|Android/i.test(ua)) return { os: null, arch: null, mobile: true };

  type UAData = { platform?: string; getHighEntropyValues?: (hints: string[]) => Promise<{ architecture?: string }> };
  const uaData = (navigator as Navigator & { userAgentData?: UAData }).userAgentData;
  const platform = (uaData?.platform || navigator.platform || '').toLowerCase();

  let arch: Arch | null = null;
  try {
    const hints = await uaData?.getHighEntropyValues?.(['architecture']);
    if (hints?.architecture) arch = hints.architecture === 'arm' ? 'arm64' : 'x64';
  } catch {
    // The hint is optional
  }
  if (!arch && /arm64|aarch64/i.test(ua)) arch = 'arm64';

  if (/win/.test(platform) || /windows/i.test(ua)) return { os: 'windows', arch: arch ?? 'x64', mobile: false };
  // Safari doesn't tell Apple Silicon from Intel; since 2020 most Macs are Apple Silicon
  if (/mac/.test(platform) || /mac os x/i.test(ua)) return { os: 'mac', arch: arch ?? 'arm64', mobile: false };
  if (/linux/.test(platform) || /linux/i.test(ua)) return { os: 'linux', arch: arch ?? 'x64', mobile: false };
  return { os: null, arch: null, mobile: false };
}

export function DownloadPage() {
  const { t, i18n } = useTranslation();
  const isRTL = i18n.language === 'he';

  const [detected, setDetected] = useState<Detected | null>(null);
  const [selected, setSelected] = useState<Os | null>(null);
  const [release, setRelease] = useState<Release | null>(null);
  const [releaseError, setReleaseError] = useState(false);
  const [libraryParts, setLibraryParts] = useState<Asset[] | null>(null);
  const downloadCount = useDownloadCount();

  useEffect(() => {
    detect().then((d) => {
      setDetected(d);
      setSelected(d.os ?? 'windows');
    });

    fetchLatest(APP_REPO)
      .then((data) =>
        setRelease({
          version: String(data.tag_name).replace(/^v/, ''),
          date: data.published_at,
          url: data.html_url,
          packages: (data.assets as GithubAsset[]).map(toAsset).map(toPackage).filter((p): p is Package => p !== null),
        }),
      )
      .catch(() => setReleaseError(true));

    // The library comes in parts the app assembles
    fetchLatest(DB_REPO)
      .then((data) =>
        setLibraryParts(
          (data.assets as GithubAsset[])
            .filter((a) => /\.part\d+$/i.test(a.name))
            .map(toAsset)
            .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true })),
        ),
      )
      .catch(() => setLibraryParts([]));
  }, []);

  // The panel slides the way the picker moved (mirrored in Hebrew)
  const [direction, setDirection] = useState(0);
  const select = (os: Os) => {
    if (selected) setDirection(Math.sign(OSES.indexOf(os) - OSES.indexOf(selected)) * (isRTL ? -1 : 1));
    setSelected(os);
  };

  const packages = release?.packages.filter((p) => p.os === selected) ?? [];
  const isDetected = !!detected?.os && selected === detected.os;
  const releaseDate = release ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'long' }).format(new Date(release.date)) : null;

  return (
    <div
      className="min-h-screen relative"
      style={{ background: 'radial-gradient(ellipse at top, var(--bg-gradient-top) 0%, var(--bg-main) 60%)', color: 'var(--text-main)' }}
    >
      <Navigation />

      <main className="px-4 md:px-6 pt-28 md:pt-36 pb-20">
        {/* Hero */}
        <motion.header
          className="max-w-2xl mx-auto text-center"
          initial={{ opacity: 0, y: RISE }}
          animate={{ opacity: 1, y: 0 }}
          transition={reveal()}
        >
          <img
            src="/icon.png"
            alt=""
            width={96}
            height={96}
            className="mx-auto mb-6 rounded-[22px]"
            style={{ boxShadow: '0 20px 50px rgba(230, 210, 140, 0.18)', border: '1px solid rgba(230, 210, 140, 0.25)' }}
          />
          <h1 className="dl-display">{t('dl.title')}</h1>
          <p className="mt-4 text-lg md:text-xl" style={{ color: 'var(--text-muted)' }}>
            {t('dl.subtitle')}
          </p>
          <p className="mt-3 text-sm min-h-5 flex items-center justify-center gap-x-2 flex-wrap" style={{ color: 'var(--gold-muted)' }}>
            {release && (
              <>
                <span>{t('dl.version', { version: release.version })}</span>
                <span aria-hidden>·</span>
                <span>{t('dl.released', { date: releaseDate })}</span>
              </>
            )}
            {release && downloadCount !== null && <span aria-hidden>·</span>}
            {downloadCount !== null && <span>{t('dl.downloads', { total: downloadCount.toLocaleString(i18n.language) })}</span>}
          </p>
        </motion.header>

        <motion.section
          className="max-w-3xl mx-auto mt-12"
          initial={{ opacity: 0, y: RISE }}
          animate={{ opacity: 1, y: 0 }}
          transition={reveal(0.08)}
          aria-label={t('dl.choose')}
        >
          {detected?.mobile && (
            <p className="dl-notice mb-6">
              <Smartphone size={18} className="shrink-0" />
              <span>{t('dl.mobile')}</span>
            </p>
          )}

          <OsPicker selected={selected} detectedOs={detected?.os ?? null} onSelect={select} />

          <div className="dl-card mt-6">
            {releaseError ? (
              <ReleaseError />
            ) : !selected || !release ? (
              <PanelSkeleton />
            ) : (
              <AnimatePresence mode="popLayout" initial={false} custom={direction}>
                <motion.div
                  key={selected}
                  custom={direction}
                  variants={{
                    enter: (d: number) => ({ opacity: 0, x: d * 24 }),
                    center: { opacity: 1, x: 0 },
                    exit: (d: number) => ({ opacity: 0, x: d * -24 }),
                  }}
                  initial="enter"
                  animate="center"
                  exit="exit"
                  transition={settle}
                >
                  {isDetected && (
                    <p className="dl-eyebrow">
                      <Sparkles size={14} />
                      {t('dl.detected', { os: t(`dl.os.${selected}`) })}
                      {detected?.arch && ` · ${archLabel(t, selected, detected.arch)}`}
                    </p>
                  )}
                  {selected === 'windows' ? (
                    <WindowsAction packages={packages} arch={detected?.os === 'windows' ? detected.arch : null} />
                  ) : (
                    <TerminalAction os={selected} />
                  )}
                  <FileList os={selected} packages={packages} />
                </motion.div>
              </AnimatePresence>
            )}
          </div>

          {release && (
            <p className="mt-4 text-center text-sm">
              <a href={release.url} target="_blank" rel="noopener noreferrer" className="dl-link">
                {t('dl.notes')}
                <ExternalLink size={13} />
              </a>
            </p>
          )}
        </motion.section>

        <OfflineSection parts={libraryParts} />
      </main>

      <SiteFooter page="download" />
    </div>
  );
}

type T = (key: string, options?: Record<string, unknown>) => string;

function archLabel(t: T, os: Os, arch: Arch): string {
  if (os === 'mac') return t(arch === 'arm64' ? 'dl.arch.appleSilicon' : 'dl.arch.intel');
  return t(`dl.arch.${arch}`);
}

function OsPicker({ selected, detectedOs, onSelect }: { selected: Os | null; detectedOs: Os | null; onSelect: (os: Os) => void }) {
  const { t } = useTranslation();

  return (
    <div role="tablist" className="dl-segmented">
      {OSES.map((os) => (
        <button
          key={os}
          role="tab"
          aria-selected={selected === os}
          onClick={() => onSelect(os)}
          className="dl-segment"
          data-selected={selected === os || undefined}
        >
          {selected === os && <motion.span layoutId="dl-segment-pill" className="dl-segment-pill" transition={settle} />}
          <span className="relative flex items-center gap-2">
            {t(`dl.os.${os}`)}
            {os === detectedOs && <span className="dl-dot" aria-hidden />}
          </span>
        </button>
      ))}
    </div>
  );
}

function WindowsAction({ packages, arch }: { packages: Package[]; arch: Arch | null }) {
  const { t } = useTranslation();
  const installers = packages.filter((p) => p.format === 'exe');
  const recommended = installers.find((p) => p.arch === (arch ?? 'x64')) ?? installers[0] ?? packages[0];
  if (!recommended) return null;

  return (
    <div className="text-center">
      <motion.a href={recommended.url} className="dl-primary" whileTap={{ scale: 0.97 }} transition={press}>
        <Download size={20} />
        {t('dl.windows.cta')}
      </motion.a>
      <p className="mt-3 text-sm" style={{ color: 'var(--gold-muted)' }}>
        {archLabel(t, 'windows', recommended.arch)} · {formatSize(recommended.size)}
      </p>
      <p className="mt-5 text-sm" style={{ color: 'var(--text-muted)' }}>
        {t('dl.windows.hint')}
      </p>
    </div>
  );
}

function TerminalAction({ os }: { os: 'mac' | 'linux' }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const command = `curl -L ${window.location.origin}/download/launch.${os} | bash`;

  useEffect(() => {
    if (!copied) return;
    const id = setTimeout(() => setCopied(false), 1800);
    return () => clearTimeout(id);
  }, [copied]);

  return (
    <div>
      <div className="dl-command" dir="ltr">
        <span className="dl-prompt" aria-hidden>$</span>
        <code className="flex-1 min-w-0 overflow-x-auto whitespace-nowrap">{command}</code>
        <button
          className="dl-copy"
          onClick={() => navigator.clipboard.writeText(command).then(() => setCopied(true))}
          aria-live="polite"
        >
          {copied ? <Check size={16} /> : <Copy size={16} />}
          <span>{copied ? t('dl.terminal.copied') : t('dl.terminal.copy')}</span>
        </button>
      </div>
      <p className="mt-4 text-sm leading-relaxed" style={{ color: 'var(--text-muted)' }}>
        {t(os === 'mac' ? 'dl.terminal.macHint' : 'dl.terminal.linuxHint')}
      </p>
    </div>
  );
}

const FORMAT_ORDER: Format[] = ['exe', 'msi', 'dmg', 'deb', 'rpm', 'pacman'];

function FileList({ os, packages }: { os: Os; packages: Package[] }) {
  const { t } = useTranslation();
  if (packages.length === 0) return null;
  const sorted = [...packages].sort(
    (a, b) => FORMAT_ORDER.indexOf(a.format) - FORMAT_ORDER.indexOf(b.format) || b.arch.localeCompare(a.arch),
  );

  return (
    <div className="mt-8 pt-6" style={{ borderTop: '1px solid var(--card-border)' }}>
      <h2 className="dl-label">{t('dl.files.title')}</h2>
      <ul className="mt-2">
        {sorted.map((p) => (
          <li key={p.name}>
            <a href={p.url} className="dl-row">
              <span className="min-w-0">
                <span className="block font-medium" style={{ color: 'var(--text-main)' }}>
                  {t(`dl.format.${p.format}`)}
                  <span style={{ color: 'var(--gold-muted)' }}> · {archLabel(t, os, p.arch)}</span>
                </span>
                <span className="block text-xs truncate" dir="ltr" style={{ color: 'var(--gold-muted)' }}>
                  {p.name}
                </span>
              </span>
              <span className="dl-row-size">{formatSize(p.size)}</span>
              <Download size={18} className="dl-row-icon" />
            </a>
          </li>
        ))}
      </ul>
      {os === 'mac' && (
        <p className="mt-3 text-xs leading-relaxed" style={{ color: 'var(--gold-muted)' }}>
          {t('dl.files.macNote')}
        </p>
      )}
    </div>
  );
}

function OfflineSection({ parts }: { parts: Asset[] | null }) {
  const { t } = useTranslation();
  if (parts?.length === 0) return null;
  const total = parts?.reduce((sum, p) => sum + p.size, 0) ?? 0;

  return (
    <motion.section
      className="max-w-3xl mx-auto mt-20"
      initial={{ opacity: 0, y: RISE }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.3 }}
      transition={reveal()}
    >
      <div className="grid md:grid-cols-[1fr_1.1fr] gap-8 md:gap-10 items-start">
        <div>
          <HardDrive size={28} style={{ color: 'var(--gold)' }} />
          <h2 className="mt-4 text-2xl md:text-3xl font-semibold">{t('dl.offline.title')}</h2>
          <p className="mt-3" style={{ color: 'var(--text-muted)' }}>
            {t('dl.offline.body')}
          </p>
          <ol className="mt-6 space-y-3">
            {['step1', 'step2', 'step3'].map((step, i) => (
              <li key={step} className="flex gap-3 text-sm" style={{ color: 'var(--text-main)' }}>
                <span className="dl-step">{i + 1}</span>
                <span className="pt-0.5">{t(`dl.offline.${step}`)}</span>
              </li>
            ))}
          </ol>
        </div>

        <div className="dl-card !p-3">
          {parts === null ? (
            <div className="p-3 space-y-3">
              {[0, 1, 2].map((i) => <div key={i} className="dl-skeleton h-10" />)}
            </div>
          ) : (
            <>
              <ul>
                {parts.map((p) => (
                  <li key={p.name}>
                    <a href={p.url} className="dl-row">
                      <span className="min-w-0 truncate text-sm font-medium" dir="ltr" style={{ color: 'var(--text-main)' }}>
                        {p.name}
                      </span>
                      <span className="dl-row-size">{formatSize(p.size)}</span>
                      <Download size={18} className="dl-row-icon" />
                    </a>
                  </li>
                ))}
              </ul>
              <p className="px-3 pt-3 pb-1 flex justify-between text-sm" style={{ borderTop: '1px solid var(--card-border)', color: 'var(--gold-muted)' }}>
                <span>{t('dl.offline.total')}</span>
                <span className="tabular-nums">{formatSize(total)}</span>
              </p>
            </>
          )}
        </div>
      </div>
    </motion.section>
  );
}

function PanelSkeleton() {
  return (
    <div aria-hidden>
      <div className="dl-skeleton h-14 max-w-sm mx-auto rounded-full" />
      <div className="dl-skeleton h-4 max-w-48 mx-auto mt-4" />
      <div className="mt-8 pt-6 space-y-3" style={{ borderTop: '1px solid var(--card-border)' }}>
        {[0, 1].map((i) => <div key={i} className="dl-skeleton h-12" />)}
      </div>
    </div>
  );
}

function ReleaseError() {
  const { t } = useTranslation();
  return (
    <div className="text-center py-4">
      <AlertCircle size={32} className="mx-auto" style={{ color: 'var(--gold)' }} />
      <h2 className="mt-3 text-lg font-semibold">{t('dl.error.title')}</h2>
      <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
        {t('dl.error.body')}
      </p>
      <a href={RELEASES_URL} target="_blank" rel="noopener noreferrer" className="dl-primary mt-6">
        <ExternalLink size={18} />
        {t('dl.error.cta')}
      </a>
    </div>
  );
}

export default DownloadPage;
