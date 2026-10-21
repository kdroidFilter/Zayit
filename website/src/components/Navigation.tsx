import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';
import { press, settle } from '../motion';
import { motion, AnimatePresence } from 'framer-motion';
import { Globe, Menu, X, Download, Github, Coffee } from 'lucide-react';
import { LANGUAGE_STORAGE_KEY } from '../i18n';
import { pagePath, parseRoute, type Lang } from '../seo';

const MotionLink = motion.create(Link);

export function Navigation() {
  const { t, i18n } = useTranslation();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isLangOpen, setIsLangOpen] = useState(false);
  const pendingSection = useRef<string | null>(null);

  const { page } = parseRoute(useLocation().pathname);
  const currentLang = i18n.language as Lang;
  const isRTL = currentLang === 'he';

  // Links rather than buttons: each language has its own URL, which crawlers follow; the path sets the language
  const chooseLanguage = (lang: Lang) => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    setIsLangOpen(false);
    setIsMenuOpen(false);
  };

  // The sections are on the landing page: from another page, the links go back to it
  const sectionPrefix = page === 'home' ? '' : pagePath(currentLang, 'home');
  const navItems = ['search', 'widgets', 'companions', 'features'].map((key) => ({ key, href: `${sectionPrefix}#${key}` }));

  return (
    <motion.nav
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={settle}
      className="nav-material fixed top-0 left-0 right-0 z-50"
      style={{
        // A translucent material the page scrolls under
        background: 'rgba(5, 5, 9, 0.62)',
        backdropFilter: 'blur(20px) saturate(180%)',
        WebkitBackdropFilter: 'blur(20px) saturate(180%)',
        borderBottom: '1px solid rgba(230, 210, 140, 0.1)',
      }}
    >
      <div className="max-w-7xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo and Links */}
          <div className="flex items-center gap-4">
            <MotionLink
              to={pagePath(currentLang, 'home')}
              className="flex items-center gap-3 text-xl font-bold"
              style={{ color: 'var(--text-main)' }}
              whileHover={{ scale: 1.02 }}
            >
              <img
                src="/icon.png"
                alt="Zayit"
                className="w-10 h-10 rounded-xl shadow-lg"
                style={{ border: '1px solid rgba(230, 210, 140, 0.2)' }}
              />
              <span>{isRTL ? 'זית' : 'Zayit'}</span>
            </MotionLink>

            {/* Source Code & Support - Desktop only */}
            <div className="hidden md:flex items-center gap-2">
              <motion.a
                href="https://github.com/kdroidFilter/SeforimApp"
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                style={{
                  color: 'var(--gold-soft)',
                  border: '1px solid rgba(230, 210, 140, 0.2)',
                }}
                whileHover={{
                  scale: 1.05,
                  background: 'rgba(230, 210, 140, 0.1)',
                }}
              >
                <Github size={14} />
                {t('footer.sourceCode')}
              </motion.a>
              <motion.a
                href="https://ko-fi.com/lomityaesh"
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                style={{
                  color: '#dc2626',
                  border: '1px solid rgba(220, 38, 38, 0.2)',
                  background: 'rgba(220, 38, 38, 0.05)',
                }}
                whileHover={{
                  scale: 1.05,
                  background: 'rgba(220, 38, 38, 0.1)',
                }}
              >
                <Coffee size={14} />
                {t('footer.support')}
              </motion.a>
            </div>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-6">
            {navItems.map((item) => (
              <motion.a
                key={item.key}
                href={item.href}
                className="text-sm font-medium transition-colors"
                style={{ color: 'var(--text-muted)' }}
                whileHover={{ color: 'var(--gold)' }}
              >
                {t(`nav.${item.key}`)}
              </motion.a>
            ))}

            {/* Language Selector */}
            <div className="relative">
              <motion.button
                onClick={() => setIsLangOpen(!isLangOpen)}
                className="flex items-center gap-2 px-3 py-2 rounded-full text-sm font-medium transition-all"
                style={{
                  color: 'var(--gold-soft)',
                  background: 'rgba(230, 210, 140, 0.1)',
                  border: '1px solid rgba(230, 210, 140, 0.2)',
                }}
                whileHover={{
                  background: 'rgba(230, 210, 140, 0.15)',
                  borderColor: 'rgba(230, 210, 140, 0.3)',
                }}
              >
                <Globe size={16} />
                <span>{currentLang === 'he' ? 'עברית' : 'English'}</span>
              </motion.button>

              <AnimatePresence>
                {isLangOpen && (
                  <motion.div
                    // Grows from its button and goes back into it, the same way
                    initial={{ opacity: 0, scale: 0.96, y: -4 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.96, y: -4 }}
                    transition={press}
                    className="absolute top-full mt-2 rounded-xl overflow-hidden shadow-xl"
                    style={{
                      transformOrigin: isRTL ? 'top left' : 'top right',
                      background: 'rgba(16, 16, 24, 0.98)',
                      border: '1px solid rgba(230, 210, 140, 0.2)',
                      [isRTL ? 'left' : 'right']: 0,
                      minWidth: '140px',
                    }}
                  >
                    <Link
                      to={pagePath('en', page)}
                      hrefLang="en"
                      lang="en"
                      onClick={() => chooseLanguage('en')}
                      className="w-full px-4 py-3 text-sm text-left transition-colors flex items-center justify-between"
                      style={{
                        color: currentLang === 'en' ? 'var(--gold)' : 'var(--text-muted)',
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(230, 210, 140, 0.1)'}
                      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                      <span>English</span>
                      {currentLang === 'en' && <span>&#10003;</span>}
                    </Link>
                    <Link
                      to={pagePath('he', page)}
                      hrefLang="he"
                      lang="he"
                      onClick={() => chooseLanguage('he')}
                      className="w-full px-4 py-3 text-sm text-left transition-colors flex items-center justify-between"
                      style={{
                        color: currentLang === 'he' ? 'var(--gold)' : 'var(--text-muted)',
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(230, 210, 140, 0.1)'}
                      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                      <span>עברית</span>
                      {currentLang === 'he' && <span>&#10003;</span>}
                    </Link>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* CTA Button */}
            <Link to={pagePath(currentLang, 'download')} aria-current={page === 'download' ? 'page' : undefined}>
              <motion.span
                className="flex items-center gap-2 px-5 py-2.5 rounded-full text-sm font-semibold text-white"
                style={{
                  background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                  boxShadow: '0 4px 15px rgba(230, 210, 140, 0.2)',
                }}
                whileHover={{ scale: 1.05, boxShadow: '0 6px 20px rgba(230, 210, 140, 0.3)' }}
                whileTap={{ scale: 0.98 }}
              >
                <Download size={16} />
                {t('nav.download')}
              </motion.span>
            </Link>
          </div>

          {/* Mobile buttons */}
          <div className="flex items-center gap-2 md:hidden">
            {/* Menu Button */}
            <button
              onClick={() => setIsMenuOpen(!isMenuOpen)}
              className="p-2 rounded-lg"
              style={{ color: 'var(--text-main)' }}
            >
              {isMenuOpen ? <X size={24} /> : <Menu size={24} />}
            </button>
          </div>
        </div>

        {/* Mobile Menu */}
        <AnimatePresence
          onExitComplete={() => {
            const id = pendingSection.current;
            pendingSection.current = null;
            if (!id) return;
            history.pushState(null, '', `#${id}`);
            document.getElementById(id)?.scrollIntoView();
          }}
        >
          {isMenuOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={settle}
              className="md:hidden overflow-hidden"
            >
              <div className="py-4 space-y-4">
                {navItems.map((item) => (
                  <a
                    key={item.key}
                    href={item.href}
                    className="block text-base font-medium py-2"
                    style={{ color: 'var(--text-muted)' }}
                    onClick={(e) => {
                      // The menu's collapse cancels the anchor's smooth scroll: scroll once it has closed
                      if (!sectionPrefix) {
                        e.preventDefault();
                        pendingSection.current = item.key;
                      }
                      setIsMenuOpen(false);
                    }}
                  >
                    {t(`nav.${item.key}`)}
                  </a>
                ))}

                <div className="flex gap-3 pt-2">
                  <Link
                    to={pagePath('en', page)}
                    hrefLang="en"
                    lang="en"
                    onClick={() => chooseLanguage('en')}
                    className="px-4 py-2 rounded-full text-sm font-medium"
                    style={{
                      color: 'var(--gold-soft)',
                      border: '1px solid rgba(230, 210, 140, 0.2)',
                      background: currentLang === 'en' ? 'rgba(230, 210, 140, 0.2)' : 'transparent',
                    }}
                  >
                    English
                  </Link>
                  <Link
                    to={pagePath('he', page)}
                    hrefLang="he"
                    lang="he"
                    onClick={() => chooseLanguage('he')}
                    className="px-4 py-2 rounded-full text-sm font-medium"
                    style={{
                      color: 'var(--gold-soft)',
                      border: '1px solid rgba(230, 210, 140, 0.2)',
                      background: currentLang === 'he' ? 'rgba(230, 210, 140, 0.2)' : 'transparent',
                    }}
                  >
                    עברית
                  </Link>
                </div>

                <Link
                  to={pagePath(currentLang, 'download')}
                  className="flex items-center justify-center gap-2 px-5 py-3 rounded-full text-sm font-semibold text-white"
                  style={{
                    background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                  }}
                  onClick={() => setIsMenuOpen(false)}
                >
                  <Download size={16} />
                  {t('nav.download')}
                </Link>

                {/* Source Code & Support - Mobile */}
                <div className="flex gap-3 pt-2">
                  <a
                    href="https://github.com/kdroidFilter/SeforimApp"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 px-4 py-2 rounded-full text-sm"
                    style={{
                      color: 'var(--gold-soft)',
                      border: '1px solid rgba(230, 210, 140, 0.2)',
                    }}
                  >
                    <Github size={16} />
                    {t('footer.sourceCode')}
                  </a>
                  <a
                    href="https://ko-fi.com/lomityaesh"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 px-4 py-2 rounded-full text-sm"
                    style={{
                      color: '#dc2626',
                      border: '1px solid rgba(220, 38, 38, 0.2)',
                      background: 'rgba(220, 38, 38, 0.05)',
                    }}
                  >
                    <Coffee size={16} />
                    {t('footer.support')}
                  </a>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.nav>
  );
}
