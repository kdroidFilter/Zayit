import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  X,
  Download,
  Terminal,
  Monitor,
  Laptop,
  Cpu,
  Database,
  Smartphone,
  HelpCircle,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
  Github,
  Heart,
  Globe,
  Package,
  Info,
} from 'lucide-react';

const GITHUB_OWNER = 'kdroidFilter';
const GITHUB_REPO = 'Zayit';
const DB_OWNER = 'kdroidFilter';
const DB_REPO = 'SeforimLibrary';

interface Asset {
  id: number;
  name: string;
  url: string;
  size: string;
  rawSize: number;
  sha256?: string;
}

interface Release {
  tag_name: string;
  name: string;
  body: string;
  assets: Asset[];
  created_at: string;
}

interface Platform {
  os: 'windows' | 'mac' | 'linux' | 'ios' | 'android' | 'mobile' | 'unknown';
  arch: 'x64' | 'arm64' | 'x86' | 'arm' | 'mobile' | 'unknown' | null;
  distro?: 'deb' | 'rpm' | 'both';
  isMobile: boolean;
}

interface ArchGroups {
  x64: Asset[];
  arm64: Asset[];
  unknown: Asset[];
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '?';
  const mb = Math.round(bytes / 1024 / 1024);
  if (mb > 0) return `${mb} MB`;
  return '< 1 MB';
}

async function detectPlatform(): Promise<Platform> {
  if (typeof navigator === 'undefined') {
    return { os: 'unknown', arch: 'unknown', isMobile: false };
  }

  const ua = navigator.userAgent || '';
  const uaData = (navigator as Navigator & { userAgentData?: { platform?: string; getHighEntropyValues?: (hints: string[]) => Promise<{ architecture?: string; bitness?: string }> } }).userAgentData || {};
  const platform = (uaData.platform || navigator.platform || '').toLowerCase();

  const isMobile = /iPhone|iPad|iPod|Android/i.test(ua);
  if (isMobile) {
    const isIOS = /iPhone|iPad|iPod/i.test(ua);
    const isAndroid = /Android/i.test(ua);
    return {
      os: isIOS ? 'ios' : isAndroid ? 'android' : 'mobile',
      arch: 'mobile',
      isMobile: true,
    };
  }

  let arch: Platform['arch'] = 'unknown';
  if (uaData.getHighEntropyValues) {
    try {
      const highEntropy = await uaData.getHighEntropyValues(['architecture', 'bitness']);
      if (highEntropy.architecture) {
        const archMap: Record<string, Platform['arch']> = {
          arm: 'arm64',
          x86: highEntropy.bitness === '64' ? 'x64' : 'x86',
        };
        arch = archMap[highEntropy.architecture] || (highEntropy.architecture as Platform['arch']);
      }
    } catch {
      // Ignore
    }
  }

  if (arch === 'unknown') {
    const archHint = (ua).toLowerCase();
    if (/arm64|aarch64/.test(archHint)) {
      arch = 'arm64';
    } else if (/arm/.test(archHint)) {
      arch = 'arm';
    } else if (/x64|x86_64|amd64|win64/.test(archHint) || /WOW64|Win64/.test(ua)) {
      arch = 'x64';
    } else if (/i[3-6]86|x86/.test(archHint)) {
      arch = 'x86';
    }
  }

  if (/win/i.test(platform) || /windows/i.test(ua)) {
    return { os: 'windows', arch: arch === 'unknown' ? null : arch, isMobile: false };
  }

  if (/mac/i.test(platform) || /macintosh|mac os x/i.test(ua)) {
    if (arch === 'unknown') {
      if (/Apple/.test(navigator.vendor) && navigator.maxTouchPoints > 0) {
        arch = 'arm64';
      } else {
        arch = null;
      }
    }
    return { os: 'mac', arch, isMobile: false };
  }

  if (/linux/i.test(platform) || /linux/i.test(ua)) {
    const distro: Platform['distro'] = /ubuntu|debian/i.test(ua)
      ? 'deb'
      : /fedora|centos|redhat|rhel|opensuse|suse/i.test(ua)
        ? 'rpm'
        : 'both';
    return {
      os: 'linux',
      distro,
      arch: arch === 'unknown' ? null : arch,
      isMobile: false,
    };
  }

  return { os: 'unknown', arch: 'unknown', isMobile: false };
}

function filterAssetsByPlatform(assets: Asset[], platform: Platform): Asset[] {
  if (!assets || assets.length === 0) return [];

  if (platform.os === 'windows') {
    return assets
      .filter((a) => /\.(msi|exe)$/i.test(a.name))
      .sort((a, b) => {
        if (a.name.toLowerCase().endsWith('.exe') && !b.name.toLowerCase().endsWith('.exe')) return -1;
        if (!a.name.toLowerCase().endsWith('.exe') && b.name.toLowerCase().endsWith('.exe')) return 1;
        return 0;
      });
  }

  if (platform.os === 'linux') {
    if (platform.distro === 'deb') {
      return assets.filter((a) => /\.deb$/i.test(a.name));
    } else if (platform.distro === 'rpm') {
      return assets.filter((a) => /\.rpm$/i.test(a.name));
    } else {
      return assets
        .filter((a) => /\.(deb|rpm)$/i.test(a.name))
        .sort((a, b) => {
          if (a.name.toLowerCase().endsWith('.deb') && !b.name.toLowerCase().endsWith('.deb')) return -1;
          if (!a.name.toLowerCase().endsWith('.deb') && b.name.toLowerCase().endsWith('.deb')) return 1;
          return 0;
        });
    }
  }

  if (platform.os === 'mac') {
    return assets.filter((a) => /\.dmg$/i.test(a.name));
  }

  return [];
}

function groupAssetsByArch(assets: Asset[]): ArchGroups {
  const groups: ArchGroups = { x64: [], arm64: [], unknown: [] };

  assets.forEach((asset) => {
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

function getLaunchCommand(kind: 'mac' | 'linux'): string {
  const file = kind === 'mac' ? 'launch.mac' : 'launch.linux';
  const base = window.location.origin + '/Zayit/download/';
  return `curl -L ${base}${file} | bash`;
}

export function DownloadModal() {
  const { t, i18n } = useTranslation();
  const isRTL = i18n.language === 'he';
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [release, setRelease] = useState<Release | null>(null);
  const [dbLoading, setDbLoading] = useState(true);
  const [dbError, setDbError] = useState<string | null>(null);
  const [dbAssets, setDbAssets] = useState<Asset[]>([]);
  const [showAllAssets, setShowAllAssets] = useState(false);
  const [includeDb, setIncludeDb] = useState(false);
  const [platform, setPlatform] = useState<Platform | null>(null);
  const [showCrossPlatform, setShowCrossPlatform] = useState(false);
  const [selectedOS, setSelectedOS] = useState<'windows' | 'mac' | 'linux'>('windows');
  const [copiedCommand, setCopiedCommand] = useState<string | null>(null);
  const [langMenuOpen, setLangMenuOpen] = useState(false);
  const [isClosing, setIsClosing] = useState(false);

  // Disable body scroll when modal is open (only on mount)
  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = '';
    };
  }, []);

  const handleCloseWithAnimation = () => {
    if (isClosing) return;
    // First restore the scrollbar
    document.body.style.overflow = '';
    // Then trigger closing animation
    setIsClosing(true);
    // Wait for animation to complete then navigate
    setTimeout(() => {
      handleClose();
    }, 350);
  };

  useEffect(() => {
    async function init() {
      const detectedPlatform = await detectPlatform();
      setPlatform(detectedPlatform);

      try {
        const resp = await fetch(`https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest`);
        if (!resp.ok) throw new Error(t('dl.errors.githubError', { status: resp.status }));
        const data = await resp.json();
        const assets: Asset[] = (data.assets || []).map((a: { id: number; name: string; browser_download_url: string; size: number }) => ({
          id: a.id,
          name: a.name,
          url: a.browser_download_url,
          size: formatFileSize(a.size),
          rawSize: a.size,
        }));
        setRelease({
          tag_name: data.tag_name,
          name: data.name || data.tag_name,
          body: data.body || '',
          assets,
          created_at: data.created_at,
        });
        setLoading(false);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
        setLoading(false);
      }

      try {
        const dbResp = await fetch(`https://api.github.com/repos/${DB_OWNER}/${DB_REPO}/releases/latest`);
        if (!dbResp.ok) throw new Error(t('dl.errors.githubError', { status: dbResp.status }));
        const dbData = await dbResp.json();
        const parts = (dbData.assets || [])
          .filter((a: { name: string }) => /seforim_bundle|part0?1|part0?2|\.part/i.test(a.name))
          .map((a: { id: number; name: string; browser_download_url: string; size: number; label?: string }) => ({
            id: a.id,
            name: a.name,
            url: a.browser_download_url,
            size: formatFileSize(a.size),
            rawSize: a.size,
            sha256: a.label || '',
          }))
          .sort((x: Asset, y: Asset) => x.name.localeCompare(y.name, undefined, { numeric: true }));
        setDbAssets(parts);
        setDbLoading(false);
      } catch (e) {
        setDbError(e instanceof Error ? e.message : String(e));
        setDbLoading(false);
      }
    }

    init();
  }, [t]);

  const copyCommand = useCallback((command: string) => {
    navigator.clipboard.writeText(command).then(() => {
      setCopiedCommand(command);
      setTimeout(() => setCopiedCommand(null), 2000);
    });
  }, []);

  const handleClose = () => {
    navigate('/');
  };

  const osLabel = (p: Platform) => t(`dl.platform.${p.os}`);

  const archLabel = (arch: string | null, osContext?: string): string => {
    if (!arch) return t('dl.arch.unknown');
    if (arch === 'arm64') {
      if (osContext === 'mac') return t('dl.arch.appleSilicon');
      return t('dl.arch.arm64');
    }
    return t(`dl.arch.${arch}`) || arch;
  };

  const getOSIcon = (os: string) => {
    const icons: Record<string, React.ReactNode> = {
      windows: <Monitor size={20} />,
      mac: <Laptop size={20} />,
      linux: <Monitor size={20} />,
      ios: <Smartphone size={20} />,
      android: <Smartphone size={20} />,
      mobile: <Smartphone size={20} />,
      unknown: <HelpCircle size={20} />,
    };
    return icons[os] || <Monitor size={20} />;
  };

  const getArchIcon = (arch: string) => {
    if (arch === 'arm64' || arch === 'arm') return <Cpu size={16} />;
    return <Cpu size={16} />;
  };

  const assets = release?.assets || [];

  // Cinematic animation config
  const cinematicEase: [number, number, number, number] = [0.16, 1, 0.3, 1];
  const dramaticEase: [number, number, number, number] = [0.34, 1.56, 0.64, 1];

  const windowsAssets = assets.filter((a) => /\.(msi|exe)$/i.test(a.name));
  const macAssets = assets.filter((a) => /\.dmg$/i.test(a.name));
  const debAssets = assets.filter((a) => /\.deb$/i.test(a.name));
  const rpmAssets = assets.filter((a) => /\.rpm$/i.test(a.name));

  const renderContent = () => {
    if (loading) {
      return (
        <div className="text-center py-12">
          <div className="download-spinner mx-auto mb-4" />
          <p style={{ color: 'var(--gold-soft)' }}>{t('dl.common.loading')}</p>
        </div>
      );
    }

    if (!platform) return null;

    // Mobile device
    if (platform.isMobile) {
      return (
        <>
          <div className="text-center mb-7">
            <img src="/Zayit/icon.png" alt="Zayit" className="download-logo mx-auto" />
            <h1 className="download-title">{t('dl.header.title')}</h1>
            <p className="download-subtitle flex items-center justify-center gap-2">
              {getOSIcon(platform.os)}
              {osLabel(platform)}
            </p>
          </div>

          <div className="download-section text-center">
            <Smartphone size={48} className="mx-auto mb-4" style={{ color: 'var(--gold-muted)' }} />
            <h2 style={{ color: 'var(--text-main)', margin: '0 0 0.5rem' }}>{t('dl.mobile.notSupported')}</h2>
            <p style={{ color: 'var(--gold-soft)', margin: 0, fontSize: '0.95rem' }}>
              {t('dl.mobile.desktopOnly')}<br />
              {t('dl.mobile.useDesktop')}
            </p>
          </div>
        </>
      );
    }

    // Unknown OS
    if (platform.os === 'unknown') {
      return (
        <>
          <div className="text-center mb-7">
            <img src="/Zayit/icon.png" alt="Zayit" className="download-logo mx-auto" />
            <h1 className="download-title">{t('dl.header.title')} — {t('dl.common.download')}</h1>
          </div>

          <div className="download-section text-center">
            <HelpCircle size={48} className="mx-auto mb-4" style={{ color: 'var(--gold-muted)' }} />
            <h2 style={{ color: 'var(--text-main)', margin: '0 0 0.5rem' }}>{t('dl.unknownOS.title')}</h2>
            <p style={{ color: 'var(--gold-soft)', margin: '0 0 1.5rem', fontSize: '0.95rem' }}>
              {t('dl.unknownOS.description')}<br />
              {t('dl.unknownOS.selectManually')}
            </p>
            <ManualDownloadLinks assets={assets} t={t} />
          </div>
        </>
      );
    }

    // Normal content
    return (
      <>
        {/* Header */}
        <div className="text-center mb-7">
          <img src="/Zayit/icon.png" alt="Zayit" className="download-logo mx-auto" />
          <h1 className="download-title">{t('dl.header.downloadTitle')}</h1>
          <p className="download-subtitle flex items-center justify-center gap-2 flex-wrap">
            {getOSIcon(platform.os)}
            {t('dl.header.detected')}: <strong>{osLabel(platform)}</strong>
            {platform.arch && platform.arch !== 'unknown' && (
              <>
                <span>&bull;</span>
                {getArchIcon(platform.arch)}
                <strong>{archLabel(platform.arch, platform.os)}</strong>
              </>
            )}
          </p>
          {release && <p className="download-version">{t('dl.common.version')} {release.tag_name}</p>}
        </div>

        {/* Error */}
        {error && (
          <div className="download-error">
            <p className="download-error-text"><strong>{t('dl.common.error')}:</strong> {error}</p>
            <p className="download-error-help">{t('dl.errors.connectionIssue')}</p>
          </div>
        )}

        {/* Main Download Section */}
        {platform.os === 'mac' && (
          <MacDownloadSection
            assets={filterAssetsByPlatform(assets, platform)}
            showAllAssets={showAllAssets}
            setShowAllAssets={setShowAllAssets}
            copiedCommand={copiedCommand}
            copyCommand={copyCommand}
            archLabel={archLabel}
            t={t}
          />
        )}

        {platform.os === 'windows' && (
          <WindowsDownloadSection
            assets={filterAssetsByPlatform(assets, platform)}
            platform={platform}
            showAllAssets={showAllAssets}
            setShowAllAssets={setShowAllAssets}
            archLabel={archLabel}
            getArchIcon={getArchIcon}
            t={t}
          />
        )}

        {platform.os === 'linux' && (
          <LinuxDownloadSection
            assets={filterAssetsByPlatform(assets, platform)}
            showAllAssets={showAllAssets}
            setShowAllAssets={setShowAllAssets}
            copiedCommand={copiedCommand}
            copyCommand={copyCommand}
            archLabel={archLabel}
            getArchIcon={getArchIcon}
            t={t}
          />
        )}

        {/* Cross-Platform Section */}
        {(windowsAssets.length > 0 || macAssets.length > 0 || debAssets.length > 0 || rpmAssets.length > 0) && (
          <CrossPlatformSection
            windowsAssets={windowsAssets}
            macAssets={macAssets}
            debAssets={debAssets}
            rpmAssets={rpmAssets}
            showCrossPlatform={showCrossPlatform}
            setShowCrossPlatform={setShowCrossPlatform}
            selectedOS={selectedOS}
            setSelectedOS={setSelectedOS}
            copiedCommand={copiedCommand}
            copyCommand={copyCommand}
            archLabel={archLabel}
            t={t}
          />
        )}

        {/* Database Section */}
        <DatabaseSection
          dbLoading={dbLoading}
          dbError={dbError}
          dbAssets={dbAssets}
          includeDb={includeDb}
          setIncludeDb={setIncludeDb}
          t={t}
        />
      </>
    );
  };

  return (
    <motion.div
      className="fixed inset-0 z-50"
      initial={{ opacity: 0 }}
      animate={{ opacity: isClosing ? 0 : 1 }}
      transition={{ duration: 0.3 }}
    >
      {/* Background */}
      <motion.div
        className="absolute inset-0"
        style={{ background: '#000' }}
        initial={{ opacity: 0 }}
        animate={{ opacity: isClosing ? 0 : 1 }}
        transition={{ duration: 0.3 }}
        onClick={handleCloseWithAnimation}
      />

      {/* Radial gradient glow */}
      <motion.div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: 'radial-gradient(ellipse 80% 50% at 50% 50%, rgba(230, 210, 140, 0.15) 0%, transparent 60%)',
        }}
        initial={{ opacity: 0, scale: 0.5 }}
        animate={isClosing ? { opacity: 0 } : { opacity: 1, scale: 1 }}
        transition={{ duration: isClosing ? 0.3 : 1.2, ease: cinematicEase }}
      />

      {/* Animated light rays */}
      <motion.div
        className="absolute inset-0 pointer-events-none overflow-hidden"
        initial={{ opacity: 0 }}
        animate={{ opacity: isClosing ? 0 : 1 }}
        transition={{ duration: isClosing ? 0.3 : 0.8 }}
      >
        {[...Array(8)].map((_, i) => (
          <motion.div
            key={i}
            className="absolute top-1/2 left-1/2"
            style={{
              width: '2px',
              height: '200vh',
              background: 'linear-gradient(to bottom, transparent, rgba(230, 210, 140, 0.15), transparent)',
              transformOrigin: 'center center',
            }}
            initial={{ opacity: 0, scale: 0, rotate: i * 22.5 }}
            animate={isClosing ? { opacity: 0 } : { opacity: 1, scale: 1, rotate: i * 22.5 }}
            transition={{
              duration: isClosing ? 0.3 : 1,
              ease: cinematicEase,
              delay: isClosing ? 0 : 0.3 + i * 0.05
            }}
          />
        ))}
      </motion.div>

      {/* Particle dots */}
      <motion.div
        className="absolute inset-0 pointer-events-none overflow-hidden"
        initial={{ opacity: 0 }}
        animate={{ opacity: isClosing ? 0 : 1 }}
        transition={{ duration: 0.3 }}
      >
        {[...Array(30)].map((_, i) => (
          <motion.div
            key={i}
            className="absolute rounded-full"
            style={{
              width: Math.random() * 4 + 2 + 'px',
              height: Math.random() * 4 + 2 + 'px',
              background: 'rgba(230, 210, 140, 0.8)',
              left: '50%',
              top: '50%',
              boxShadow: '0 0 15px rgba(230, 210, 140, 0.6)',
            }}
            initial={{ opacity: 0, scale: 0, x: 0, y: 0 }}
            animate={isClosing
              ? { opacity: 0 }
              : {
                  opacity: [0, 1, 0.6],
                  scale: [0, 1.5, 1],
                  x: (Math.random() - 0.5) * 400,
                  y: (Math.random() - 0.5) * 400,
                }
            }
            transition={{
              duration: isClosing ? 0.3 : 2,
              ease: 'easeInOut',
              delay: isClosing ? 0 : 0.4 + i * 0.05,
              repeat: isClosing ? 0 : Infinity,
              repeatDelay: isClosing ? 0 : Math.random() * 3,
            }}
          />
        ))}
      </motion.div>

      {/* Vignette effect */}
      <motion.div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: 'radial-gradient(ellipse at center, transparent 40%, rgba(0,0,0,0.8) 100%)',
        }}
        initial={{ opacity: 0 }}
        animate={{ opacity: isClosing ? 0 : 1 }}
        transition={{ duration: isClosing ? 0.3 : 0.8 }}
      />

      {/* Modal container - scrollable */}
      <div className="relative w-full h-full overflow-y-auto py-8 px-4 flex items-start justify-center">
        <motion.div
          className="download-card relative mx-auto"
          initial={{ opacity: 0, scale: 0.8, y: 100 }}
          animate={isClosing ? { opacity: 0 } : { opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: isClosing ? 0.3 : 1, ease: dramaticEase, delay: isClosing ? 0 : 0.3 }}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Card glow effect */}
          <motion.div
            className="absolute -inset-1 rounded-3xl pointer-events-none"
            style={{
              background: 'linear-gradient(135deg, rgba(230, 210, 140, 0.3), transparent, rgba(230, 210, 140, 0.1))',
              filter: 'blur(20px)',
            }}
            initial={{ opacity: 0 }}
            animate={{ opacity: isClosing ? 0 : 1 }}
            transition={{ duration: isClosing ? 0.3 : 1.2, delay: isClosing ? 0 : 0.5 }}
          />

          <div className="download-card-inner relative">
            {/* Close button */}
            <motion.button
              onClick={handleCloseWithAnimation}
              className="download-close-button"
              style={{ transform: isRTL ? 'scaleX(-1)' : undefined }}
              initial={{ opacity: 0, scale: 0 }}
              animate={isClosing ? { opacity: 0 } : { opacity: 1, scale: 1 }}
              transition={{ duration: isClosing ? 0.2 : 0.5, delay: isClosing ? 0 : 0.8, ease: dramaticEase }}
              whileHover={{ scale: 1.1, rotate: 90 }}
              whileTap={{ scale: 0.9 }}
            >
              <X size={20} />
            </motion.button>

            {/* Language selector */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={isClosing ? { opacity: 0 } : { opacity: 1, x: 0 }}
              transition={{ duration: isClosing ? 0.2 : 0.5, delay: isClosing ? 0 : 0.9 }}
            >
              <LanguageSelector
                currentLang={i18n.language}
                isOpen={langMenuOpen}
                onToggle={() => setLangMenuOpen(!langMenuOpen)}
                onSelect={(lang) => { i18n.changeLanguage(lang); setLangMenuOpen(false); }}
              />
            </motion.div>

            {/* Content */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={isClosing ? { opacity: 0 } : { opacity: 1, y: 0 }}
              transition={{ duration: isClosing ? 0.2 : 0.8, delay: isClosing ? 0 : 0.6, ease: cinematicEase }}
            >
              {renderContent()}
            </motion.div>

            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: isClosing ? 0 : 1 }}
              transition={{ duration: isClosing ? 0.2 : 0.5, delay: isClosing ? 0 : 1.2 }}
            >
              <Footer />
            </motion.div>
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
}

// Sub-components

function LanguageSelector({
  currentLang,
  isOpen,
  onToggle,
  onSelect,
}: {
  currentLang: string;
  isOpen: boolean;
  onToggle: () => void;
  onSelect: (lang: string) => void;
}) {
  const languages = [
    { code: 'en', name: 'English' },
    { code: 'he', name: 'עברית' },
  ];

  return (
    <div className="download-header-controls">
      <div className="download-language-selector">
        <button className="download-lang-btn" onClick={onToggle}>
          <Globe size={18} />
          <span className="download-lang-name">{languages.find((l) => l.code === currentLang)?.name}</span>
          <ChevronDown size={16} className={`transition-transform ${isOpen ? 'rotate-180' : ''}`} />
        </button>
        {isOpen && (
          <div className="download-lang-menu">
            {languages.map((lang) => (
              <button
                key={lang.code}
                className={`download-lang-option ${lang.code === currentLang ? 'active' : ''}`}
                onClick={() => onSelect(lang.code)}
              >
                <span>{lang.name}</span>
                {lang.code === currentLang && <Check size={16} />}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function Footer() {
  return (
    <div className="download-footer">
      <div className="download-footer-links">
        <a
          href="https://github.com/kdroidFilter/Zayit"
          target="_blank"
          rel="noopener noreferrer"
          className="download-footer-link"
        >
          <Github size={20} />
        </a>
        <a
          href="https://ko-fi.com/lomityaesh"
          target="_blank"
          rel="noopener noreferrer"
          className="download-footer-link download-footer-link-donate"
        >
          <Heart size={20} />
        </a>
      </div>
    </div>
  );
}

function ManualDownloadLinks({ assets, t }: { assets: Asset[]; t: (key: string) => string }) {
  if (!assets || assets.length === 0) {
    return <p style={{ color: 'var(--gold-soft)' }}>{t('dl.unknownOS.noFilesFound')}</p>;
  }

  return (
    <div className="download-assets-list compact">
      {assets.map((asset) => (
        <div key={asset.id} className="download-asset-item compact">
          <div className="download-asset-line">
            <div className="download-asset-meta">
              <p className="download-asset-name">{asset.name}</p>
              <p className="download-asset-size">{t('dl.common.size')}: {asset.size}</p>
            </div>
            <a href={asset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary">
              <Download size={18} />
              <span>{t('dl.common.download')}</span>
            </a>
          </div>
        </div>
      ))}
    </div>
  );
}

function MacDownloadSection({
  assets,
  showAllAssets,
  setShowAllAssets,
  copiedCommand,
  copyCommand,
  archLabel,
  t,
}: {
  assets: Asset[];
  showAllAssets: boolean;
  setShowAllAssets: (v: boolean) => void;
  copiedCommand: string | null;
  copyCommand: (cmd: string) => void;
  archLabel: (arch: string | null, os?: string) => string;
  t: (key: string) => string;
}) {
  const command = getLaunchCommand('mac');

  return (
    <>
      <div className="download-section">
        <h2 className="download-section-title">
          <Terminal size={20} />
          <span>{t('dl.install.autoInstallMac')}</span>
        </h2>
        <p style={{ color: 'var(--gold-soft)', margin: '0 0 1rem', fontSize: '0.95rem' }}>
          {t('dl.install.copyAndRun')}
        </p>
        <div className="download-command-box">
          <code>{command}</code>
          <button className="download-copy-btn" onClick={() => copyCommand(command)}>
            {copiedCommand === command ? <Check size={18} /> : <Copy size={18} />}
          </button>
        </div>
        <p style={{ color: 'var(--gold-muted)', margin: '1rem 0 0', fontSize: '0.85rem' }} className="flex items-center gap-1">
          <Info size={14} />
          {t('dl.install.scriptInfo')}
        </p>
      </div>

      {assets.length > 0 && (
        <div className="download-section">
          <h2 className="download-section-title">
            <Download size={20} />
            <span>{t('dl.install.manualDownload')}</span>
          </h2>
          <button className="download-toggle-button" onClick={() => setShowAllAssets(!showAllAssets)}>
            {showAllAssets ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            <span>{showAllAssets ? t('dl.common.hide') : t('dl.common.show')} {t('dl.install.showManualOptions')}</span>
          </button>
          {showAllAssets && <PlatformAssets assets={assets} osContext="mac" archLabel={archLabel} />}
        </div>
      )}
    </>
  );
}

function WindowsDownloadSection({
  assets,
  platform,
  showAllAssets,
  setShowAllAssets,
  archLabel,
  getArchIcon,
  t,
}: {
  assets: Asset[];
  platform: Platform;
  showAllAssets: boolean;
  setShowAllAssets: (v: boolean) => void;
  archLabel: (arch: string | null, os?: string) => string;
  getArchIcon: (arch: string) => React.ReactNode;
  t: (key: string) => string;
}) {
  const archGroups = groupAssetsByArch(assets);

  if (platform.arch && archGroups[platform.arch as keyof ArchGroups]?.length > 0) {
    const archAssets = archGroups[platform.arch as keyof ArchGroups];
    const exeAsset = archAssets.find((a) => a.name.toLowerCase().endsWith('.exe'));
    const msiAsset = archAssets.find((a) => a.name.toLowerCase().endsWith('.msi'));
    const recommended = exeAsset || archAssets[0];
    const otherArch = platform.arch === 'x64' ? 'arm64' : 'x64';
    const hasOtherArch = archGroups[otherArch]?.length > 0;
    const hasSupplementary = msiAsset || hasOtherArch;

    return (
      <div className="download-section">
        <h2 className="download-section-title">
          <Download size={20} />
          <span>{t('dl.windows.downloadSoftware')}</span>
        </h2>
        <p style={{ color: 'var(--gold-soft)', margin: '0 0 1rem', fontSize: '0.95rem' }}>
          {t('dl.windows.recommendedFile')}: <strong>{recommended.name}</strong> ({recommended.size})
        </p>
        <div className="download-btn-row">
          <a href={recommended.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-primary">
            <Download size={20} />
            <span>{t('dl.common.downloadNow')}</span>
          </a>
        </div>
        {hasSupplementary && (
          <div style={{ marginTop: '1rem', textAlign: 'center' }}>
            <button className="download-toggle-button" onClick={() => setShowAllAssets(!showAllAssets)}>
              {showAllAssets ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
              <span>{showAllAssets ? t('dl.common.hide') : t('dl.common.show')} {t('dl.windows.showMoreOptions')}</span>
            </button>
          </div>
        )}
        {showAllAssets && hasSupplementary && (
          <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid rgba(255,215,0,0.1)' }}>
            {msiAsset && (
              <>
                <p style={{ color: 'var(--gold-soft)', fontSize: '0.9rem', margin: '0 0 0.75rem' }}>
                  {t('dl.windows.alternativeFormat')}
                </p>
                <a href={msiAsset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary w-full mb-2">
                  <Download size={18} />
                  <span>{msiAsset.name} ({msiAsset.size})</span>
                </a>
              </>
            )}
            {hasOtherArch && (
              <>
                <p style={{ color: 'var(--gold-soft)', fontSize: '0.9rem', margin: `${msiAsset ? '1rem' : '0'} 0 0.75rem` }}>
                  {t('dl.windows.otherArchitectures')}
                </p>
                {archGroups[otherArch].map((asset) => (
                  <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary w-full mb-2">
                    {getArchIcon(otherArch)}
                    <span>{asset.name} ({asset.size})</span>
                  </a>
                ))}
              </>
            )}
          </div>
        )}
      </div>
    );
  }

  // Architecture not detected
  return (
    <div className="download-section">
      <h2 className="download-section-title">
        <Download size={20} />
        <span>{t('dl.windows.selectArchitecture')}</span>
      </h2>
      <p style={{ color: 'var(--gold-soft)', margin: '0 0 1.5rem', fontSize: '0.9rem' }}>
        {t('dl.windows.archNotDetected')}
      </p>
      <div className="download-arch-options">
        {archGroups.x64.length > 0 && (
          <ArchOption
            assets={archGroups.x64}
            archKey="x64"
            archDesc={t('dl.windows.mostModern')}
            archLabel={archLabel}
            getArchIcon={getArchIcon}
          />
        )}
        {archGroups.arm64.length > 0 && (
          <ArchOption
            assets={archGroups.arm64}
            archKey="arm64"
            archDesc={t('dl.windows.surfaceAndSimilar')}
            archLabel={archLabel}
            getArchIcon={getArchIcon}
          />
        )}
      </div>
    </div>
  );
}

function ArchOption({
  assets,
  archKey,
  archDesc,
  archLabel,
  getArchIcon,
}: {
  assets: Asset[];
  archKey: string;
  archDesc: string;
  archLabel: (arch: string | null, os?: string) => string;
  getArchIcon: (arch: string) => React.ReactNode;
}) {
  const exeAsset = assets.find((a) => a.name.toLowerCase().endsWith('.exe'));
  const msiAsset = assets.find((a) => a.name.toLowerCase().endsWith('.msi'));
  const primary = exeAsset || assets[0];

  return (
    <div className="download-arch-option">
      <div className="download-arch-header">
        <span style={{ color: 'var(--gold)', fontSize: '1.5rem' }}>{getArchIcon(archKey)}</span>
        <h3>{archLabel(archKey, 'windows')}</h3>
      </div>
      <p className="download-arch-desc">{archDesc}</p>
      <a href={primary.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-primary w-full mt-2">
        <Download size={18} />
        <span>{primary.name} ({primary.size})</span>
      </a>
      {msiAsset && exeAsset && (
        <a href={msiAsset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary w-full mt-2">
          <Download size={18} />
          <span>{msiAsset.name} ({msiAsset.size})</span>
        </a>
      )}
    </div>
  );
}

function LinuxDownloadSection({
  assets,
  showAllAssets,
  setShowAllAssets,
  copiedCommand,
  copyCommand,
  archLabel,
  getArchIcon,
  t,
}: {
  assets: Asset[];
  showAllAssets: boolean;
  setShowAllAssets: (v: boolean) => void;
  copiedCommand: string | null;
  copyCommand: (cmd: string) => void;
  archLabel: (arch: string | null, os?: string) => string;
  getArchIcon: (arch: string) => React.ReactNode;
  t: (key: string) => string;
}) {
  const command = getLaunchCommand('linux');
  const debAssets = assets.filter((a) => a.name.toLowerCase().endsWith('.deb'));
  const rpmAssets = assets.filter((a) => a.name.toLowerCase().endsWith('.rpm'));

  return (
    <>
      <div className="download-section">
        <h2 className="download-section-title">
          <Terminal size={20} />
          <span>{t('dl.install.autoInstallLinux')}</span>
        </h2>
        <p style={{ color: 'var(--gold-soft)', margin: '0 0 1rem', fontSize: '0.95rem' }}>
          {t('dl.install.copyAndRun')}
        </p>
        <div className="download-command-box">
          <code>{command}</code>
          <button className="download-copy-btn" onClick={() => copyCommand(command)}>
            {copiedCommand === command ? <Check size={18} /> : <Copy size={18} />}
          </button>
        </div>
        <p style={{ color: 'var(--gold-muted)', margin: '1rem 0 0', fontSize: '0.85rem' }} className="flex items-center gap-1">
          <Info size={14} />
          {t('dl.install.scriptInfoLinux')}
        </p>
      </div>

      {(debAssets.length > 0 || rpmAssets.length > 0) && (
        <div className="download-section">
          <h2 className="download-section-title">
            <Download size={20} />
            <span>{t('dl.install.manualDownload')}</span>
          </h2>
          <button className="download-toggle-button" onClick={() => setShowAllAssets(!showAllAssets)}>
            {showAllAssets ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            <span>{showAllAssets ? t('dl.common.hide') : t('dl.common.show')} {t('dl.install.showManualOptions')}</span>
          </button>
          {showAllAssets && (
            <>
              {debAssets.length > 0 && (
                <div className="download-linux-distro-section">
                  <h3 className="flex items-center gap-2" style={{ color: 'var(--text-main)', fontSize: '1rem', margin: '0 0 0.75rem' }}>
                    <Package size={18} />
                    Debian/Ubuntu (.deb)
                  </h3>
                  <LinuxArchOptions assets={debAssets} archLabel={archLabel} getArchIcon={getArchIcon} />
                </div>
              )}
              {rpmAssets.length > 0 && (
                <div className="download-linux-distro-section" style={{ marginTop: '1.5rem' }}>
                  <h3 className="flex items-center gap-2" style={{ color: 'var(--text-main)', fontSize: '1rem', margin: '0 0 0.75rem' }}>
                    <Package size={18} />
                    Fedora/RHEL/openSUSE (.rpm)
                  </h3>
                  <LinuxArchOptions assets={rpmAssets} archLabel={archLabel} getArchIcon={getArchIcon} />
                </div>
              )}
            </>
          )}
        </div>
      )}
    </>
  );
}

function LinuxArchOptions({
  assets,
  archLabel,
  getArchIcon,
}: {
  assets: Asset[];
  archLabel: (arch: string | null, os?: string) => string;
  getArchIcon: (arch: string) => React.ReactNode;
}) {
  const archGroups = groupAssetsByArch(assets);

  return (
    <div className="download-arch-options compact">
      {archGroups.x64.length > 0 && (
        <div className="download-item">
          <div className="flex items-center gap-2 mb-2">
            <span style={{ color: 'var(--gold-soft)' }}>{getArchIcon('x64')}</span>
            <span style={{ color: 'var(--gold-soft)', fontSize: '0.9rem' }}>{archLabel('x64', 'linux')}</span>
          </div>
          {archGroups.x64.map((asset) => (
            <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary w-full mb-2">
              <Download size={18} />
              <span>{asset.name} ({asset.size})</span>
            </a>
          ))}
        </div>
      )}
      {archGroups.arm64.length > 0 && (
        <div className="download-item">
          <div className="flex items-center gap-2 mb-2">
            <span style={{ color: 'var(--gold-soft)' }}>{getArchIcon('arm64')}</span>
            <span style={{ color: 'var(--gold-soft)', fontSize: '0.9rem' }}>{archLabel('arm64', 'linux')}</span>
          </div>
          {archGroups.arm64.map((asset) => (
            <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary w-full mb-2">
              <Download size={18} />
              <span>{asset.name} ({asset.size})</span>
            </a>
          ))}
        </div>
      )}
      {archGroups.unknown.length > 0 && (
        <div className="download-item">
          {archGroups.unknown.map((asset) => (
            <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary w-full mb-2">
              <Download size={18} />
              <span>{asset.name} ({asset.size})</span>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

function CrossPlatformSection({
  windowsAssets,
  macAssets,
  debAssets,
  rpmAssets,
  showCrossPlatform,
  setShowCrossPlatform,
  selectedOS,
  setSelectedOS,
  copiedCommand,
  copyCommand,
  archLabel,
  t,
}: {
  windowsAssets: Asset[];
  macAssets: Asset[];
  debAssets: Asset[];
  rpmAssets: Asset[];
  showCrossPlatform: boolean;
  setShowCrossPlatform: (v: boolean) => void;
  selectedOS: 'windows' | 'mac' | 'linux';
  setSelectedOS: (v: 'windows' | 'mac' | 'linux') => void;
  copiedCommand: string | null;
  copyCommand: (cmd: string) => void;
  archLabel: (arch: string | null, os?: string) => string;
  t: (key: string) => string;
}) {
  return (
    <div className="download-section download-section-cross-platform">
      <div className="download-section-header">
        <h2 className="download-section-title">
          <Monitor size={20} />
          <span>{t('dl.crossPlatform.title')}</span>
        </h2>
        <button className="download-toggle-button inline" onClick={() => setShowCrossPlatform(!showCrossPlatform)}>
          {showCrossPlatform ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
          <span>{showCrossPlatform ? t('dl.common.hide') : t('dl.common.show')} {t('dl.crossPlatform.showOther')}</span>
        </button>
      </div>

      {showCrossPlatform && (
        <div className="download-os-tabs">
          <div className="download-tab-buttons">
            {windowsAssets.length > 0 && (
              <button
                className={`download-tab-button ${selectedOS === 'windows' ? 'active' : ''}`}
                onClick={() => setSelectedOS('windows')}
              >
                <Monitor size={20} />
                <span>{t('dl.platform.windows')}</span>
              </button>
            )}
            {macAssets.length > 0 && (
              <button
                className={`download-tab-button ${selectedOS === 'mac' ? 'active' : ''}`}
                onClick={() => setSelectedOS('mac')}
              >
                <Laptop size={20} />
                <span>{t('dl.platform.mac')}</span>
              </button>
            )}
            {(debAssets.length > 0 || rpmAssets.length > 0) && (
              <button
                className={`download-tab-button ${selectedOS === 'linux' ? 'active' : ''}`}
                onClick={() => setSelectedOS('linux')}
              >
                <Monitor size={20} />
                <span>{t('dl.platform.linux')}</span>
              </button>
            )}
          </div>

          <div className="download-tab-content">
            {selectedOS === 'windows' && windowsAssets.length > 0 && (
              <div className="download-platform-downloads">
                <h3 className="download-platform-title">
                  <Monitor size={20} />
                  {t('dl.platform.windows')}
                </h3>
                <PlatformAssets assets={windowsAssets} osContext="windows" archLabel={archLabel} />
              </div>
            )}

            {selectedOS === 'mac' && macAssets.length > 0 && (
              <div className="download-platform-downloads">
                <h3 className="download-platform-title">
                  <Laptop size={20} />
                  {t('dl.platform.mac')}
                </h3>
                <div className="download-command-box mb-4">
                  <code>{getLaunchCommand('mac')}</code>
                  <button className="download-copy-btn" onClick={() => copyCommand(getLaunchCommand('mac'))}>
                    {copiedCommand === getLaunchCommand('mac') ? <Check size={18} /> : <Copy size={18} />}
                  </button>
                </div>
                <p style={{ color: 'var(--gold-muted)', fontSize: '0.85rem', marginBottom: '1rem' }}>
                  {t('dl.crossPlatform.orDownloadManually')}
                </p>
                <PlatformAssets assets={macAssets} osContext="mac" archLabel={archLabel} />
              </div>
            )}

            {selectedOS === 'linux' && (debAssets.length > 0 || rpmAssets.length > 0) && (
              <div className="download-platform-downloads">
                <h3 className="download-platform-title">
                  <Monitor size={20} />
                  {t('dl.platform.linux')}
                </h3>
                <div className="download-command-box mb-4">
                  <code>{getLaunchCommand('linux')}</code>
                  <button className="download-copy-btn" onClick={() => copyCommand(getLaunchCommand('linux'))}>
                    {copiedCommand === getLaunchCommand('linux') ? <Check size={18} /> : <Copy size={18} />}
                  </button>
                </div>
                <p style={{ color: 'var(--gold-muted)', fontSize: '0.85rem', marginBottom: '1rem' }}>
                  {t('dl.crossPlatform.orDownloadManually')}
                </p>
                {debAssets.length > 0 && (
                  <div className="download-distro-group">
                    <h4 className="download-distro-title">
                      <Package size={16} />
                      Debian/Ubuntu (.deb)
                    </h4>
                    <PlatformAssets assets={debAssets} osContext="linux" archLabel={archLabel} />
                  </div>
                )}
                {rpmAssets.length > 0 && (
                  <div className="download-distro-group">
                    <h4 className="download-distro-title">
                      <Package size={16} />
                      Fedora/RHEL (.rpm)
                    </h4>
                    <PlatformAssets assets={rpmAssets} osContext="linux" archLabel={archLabel} />
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function PlatformAssets({
  assets,
  osContext,
  archLabel,
}: {
  assets: Asset[];
  osContext: string;
  archLabel: (arch: string | null, os?: string) => string;
}) {
  const archGroups = groupAssetsByArch(assets);

  return (
    <div className="download-platform-assets">
      {archGroups.x64.length > 0 && (
        <div className="download-arch-group">
          <div className="download-arch-label">
            <Cpu size={16} />
            <span>{archLabel('x64', osContext)}</span>
          </div>
          <div className="download-arch-downloads">
            {archGroups.x64.map((asset) => (
              <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-chip" title={asset.size}>
                <Download size={16} />
                <span>{asset.name}</span>
              </a>
            ))}
          </div>
        </div>
      )}
      {archGroups.arm64.length > 0 && (
        <div className="download-arch-group">
          <div className="download-arch-label">
            <Cpu size={16} />
            <span>{archLabel('arm64', osContext)}</span>
          </div>
          <div className="download-arch-downloads">
            {archGroups.arm64.map((asset) => (
              <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-chip" title={asset.size}>
                <Download size={16} />
                <span>{asset.name}</span>
              </a>
            ))}
          </div>
        </div>
      )}
      {archGroups.unknown.length > 0 && (
        <div className="download-arch-group">
          <div className="download-arch-downloads">
            {archGroups.unknown.map((asset) => (
              <a key={asset.id} href={asset.url} target="_blank" rel="noopener noreferrer" className="download-chip" title={asset.size}>
                <Download size={16} />
                <span>{asset.name}</span>
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function DatabaseSection({
  dbLoading,
  dbError,
  dbAssets,
  includeDb,
  setIncludeDb,
  t,
}: {
  dbLoading: boolean;
  dbError: string | null;
  dbAssets: Asset[];
  includeDb: boolean;
  setIncludeDb: (v: boolean) => void;
  t: (key: string) => string;
}) {
  const totalDbSize = dbAssets.reduce((acc, a) => acc + (a.rawSize || 0), 0);

  return (
    <div className="download-section download-section-db">
      <div className="download-section-header">
        <h2 className="download-section-title">
          <Database size={20} />
          <span>{t('dl.database.title')}</span>
        </h2>
      </div>

      {dbLoading ? (
        <p style={{ color: 'var(--gold-soft)', fontSize: '0.9rem' }}>{t('dl.database.loading')}</p>
      ) : dbError ? (
        <div className="download-error" style={{ margin: 0 }}>
          <p className="download-error-text" style={{ margin: 0 }}>{dbError}</p>
        </div>
      ) : dbAssets.length > 0 ? (
        <>
          <div className="download-info-banner">
            <p style={{ margin: 0, color: 'var(--text-main)', fontSize: '0.9rem' }}>
              {t('dl.database.offlineInfo')}
            </p>
          </div>
          <div className="download-toggle-row">
            <button className="download-toggle-button inline" onClick={() => setIncludeDb(!includeDb)}>
              {includeDb ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
              <span>{includeDb ? t('dl.database.hideFiles') : t('dl.database.showFiles')}</span>
            </button>
          </div>
          {includeDb && (
            <>
              <p style={{ color: 'var(--gold-muted)', fontSize: '0.83rem', margin: '0 0 0.5rem' }}>
                {t('dl.database.totalSize')}: {formatFileSize(totalDbSize)}
              </p>
              <div className="download-assets-list compact">
                {dbAssets.map((asset) => (
                  <div key={asset.id} className="download-db-item">
                    <div className="download-asset-line">
                      <div className="download-asset-meta">
                        <p className="download-asset-name">{asset.name}</p>
                        <p className="download-asset-size">{t('dl.common.size')}: {asset.size}</p>
                        {asset.sha256 && (
                          <p className="download-small-text" style={{ marginTop: '0.2rem' }}>SHA-256: {asset.sha256}</p>
                        )}
                      </div>
                      <a href={asset.url} target="_blank" rel="noopener noreferrer" className="download-btn download-btn-secondary">
                        <Download size={18} />
                        <span>{t('dl.common.download')}</span>
                      </a>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      ) : (
        <p style={{ color: 'var(--gold-soft)', textAlign: 'center', margin: 0, fontSize: '0.85rem' }}>
          {t('dl.database.noFilesAvailable')}
        </p>
      )}
    </div>
  );
}

export default DownloadModal;
