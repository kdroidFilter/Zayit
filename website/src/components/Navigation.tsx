import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Globe, Menu, X, Download, Sun, Moon } from 'lucide-react';
import { useTheme } from '../contexts/ThemeContext';

export function Navigation() {
  const { t, i18n } = useTranslation();
  const { isDark, toggleTheme } = useTheme();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isLangOpen, setIsLangOpen] = useState(false);

  const currentLang = i18n.language;
  const isRTL = currentLang === 'he';

  const toggleLanguage = (lang: string) => {
    i18n.changeLanguage(lang);
    document.documentElement.dir = lang === 'he' ? 'rtl' : 'ltr';
    document.documentElement.lang = lang;
    setIsLangOpen(false);
  };

  const navItems = [
    { key: 'features', href: '#features' },
    { key: 'search', href: '#search' },
  ];

  return (
    <motion.nav
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6 }}
      className="fixed top-0 left-0 right-0 z-50 backdrop-blur-xl"
      style={{
        background: isDark
          ? 'rgba(5, 5, 9, 0.85)'
          : 'rgba(245, 243, 238, 0.85)',
        borderBottom: `1px solid ${isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)'}`,
      }}
    >
      <div className="max-w-7xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo */}
          <motion.a
            href="#"
            className="flex items-center gap-3 text-xl font-bold"
            style={{ color: 'var(--text-main)' }}
            whileHover={{ scale: 1.02 }}
          >
            <img
              src="/Zayit/icon.png"
              alt="Zayit"
              className="w-10 h-10 rounded-xl shadow-lg"
              style={{ border: '1px solid rgba(139, 115, 85, 0.2)' }}
            />
            <span>{isRTL ? 'זית' : 'Zayit'}</span>
          </motion.a>

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

            {/* Theme Toggle */}
            <motion.button
              onClick={toggleTheme}
              className="flex items-center justify-center w-10 h-10 rounded-full transition-all"
              style={{
                background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
                border: `1px solid ${isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)'}`,
                color: 'var(--gold)',
              }}
              whileHover={{
                scale: 1.1,
                background: isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.15)',
              }}
              whileTap={{ scale: 0.95 }}
              aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              <AnimatePresence mode="wait">
                {isDark ? (
                  <motion.div
                    key="sun"
                    initial={{ rotate: -90, opacity: 0 }}
                    animate={{ rotate: 0, opacity: 1 }}
                    exit={{ rotate: 90, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                  >
                    <Sun size={18} />
                  </motion.div>
                ) : (
                  <motion.div
                    key="moon"
                    initial={{ rotate: 90, opacity: 0 }}
                    animate={{ rotate: 0, opacity: 1 }}
                    exit={{ rotate: -90, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                  >
                    <Moon size={18} />
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.button>

            {/* Language Selector */}
            <div className="relative">
              <motion.button
                onClick={() => setIsLangOpen(!isLangOpen)}
                className="flex items-center gap-2 px-3 py-2 rounded-full text-sm font-medium transition-all"
                style={{
                  color: 'var(--gold-soft)',
                  background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
                  border: `1px solid ${isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)'}`,
                }}
                whileHover={{
                  background: isDark ? 'rgba(230, 210, 140, 0.15)' : 'rgba(139, 115, 85, 0.15)',
                  borderColor: isDark ? 'rgba(230, 210, 140, 0.3)' : 'rgba(139, 115, 85, 0.3)',
                }}
              >
                <Globe size={16} />
                <span>{currentLang === 'he' ? 'עברית' : 'English'}</span>
              </motion.button>

              <AnimatePresence>
                {isLangOpen && (
                  <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -10 }}
                    className="absolute top-full mt-2 rounded-xl overflow-hidden shadow-xl"
                    style={{
                      background: isDark ? 'rgba(16, 16, 24, 0.98)' : 'rgba(255, 255, 255, 0.98)',
                      border: `1px solid ${isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)'}`,
                      [isRTL ? 'left' : 'right']: 0,
                      minWidth: '140px',
                    }}
                  >
                    <button
                      onClick={() => toggleLanguage('en')}
                      className="w-full px-4 py-3 text-sm text-left transition-colors flex items-center justify-between"
                      style={{
                        color: currentLang === 'en' ? 'var(--gold)' : 'var(--text-muted)',
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.background = isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)'}
                      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                      <span>English</span>
                      {currentLang === 'en' && <span>&#10003;</span>}
                    </button>
                    <button
                      onClick={() => toggleLanguage('he')}
                      className="w-full px-4 py-3 text-sm text-left transition-colors flex items-center justify-between"
                      style={{
                        color: currentLang === 'he' ? 'var(--gold)' : 'var(--text-muted)',
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.background = isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)'}
                      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                      <span>עברית</span>
                      {currentLang === 'he' && <span>&#10003;</span>}
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* CTA Button */}
            <motion.a
              href="/Zayit/download"
              className="flex items-center gap-2 px-5 py-2.5 rounded-full text-sm font-semibold text-white"
              style={{
                background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                boxShadow: isDark ? '0 4px 15px rgba(230, 210, 140, 0.2)' : '0 4px 15px rgba(139, 115, 85, 0.3)',
              }}
              whileHover={{ scale: 1.05, boxShadow: isDark ? '0 6px 20px rgba(230, 210, 140, 0.3)' : '0 6px 20px rgba(139, 115, 85, 0.4)' }}
              whileTap={{ scale: 0.98 }}
            >
              <Download size={16} />
              {t('nav.download')}
            </motion.a>
          </div>

          {/* Mobile buttons */}
          <div className="flex items-center gap-2 md:hidden">
            {/* Theme Toggle Mobile */}
            <motion.button
              onClick={toggleTheme}
              className="flex items-center justify-center w-10 h-10 rounded-full"
              style={{
                background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
                color: 'var(--gold)',
              }}
              whileTap={{ scale: 0.95 }}
            >
              {isDark ? <Sun size={18} /> : <Moon size={18} />}
            </motion.button>

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
        <AnimatePresence>
          {isMenuOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="md:hidden overflow-hidden"
            >
              <div className="py-4 space-y-4">
                {navItems.map((item) => (
                  <a
                    key={item.key}
                    href={item.href}
                    className="block text-base font-medium py-2"
                    style={{ color: 'var(--text-muted)' }}
                    onClick={() => setIsMenuOpen(false)}
                  >
                    {t(`nav.${item.key}`)}
                  </a>
                ))}

                <div className="flex gap-3 pt-2">
                  <button
                    onClick={() => toggleLanguage('en')}
                    className="px-4 py-2 rounded-full text-sm font-medium"
                    style={{
                      color: 'var(--gold-soft)',
                      border: `1px solid ${isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)'}`,
                      background: currentLang === 'en' ? (isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)') : 'transparent',
                    }}
                  >
                    English
                  </button>
                  <button
                    onClick={() => toggleLanguage('he')}
                    className="px-4 py-2 rounded-full text-sm font-medium"
                    style={{
                      color: 'var(--gold-soft)',
                      border: `1px solid ${isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)'}`,
                      background: currentLang === 'he' ? (isDark ? 'rgba(230, 210, 140, 0.2)' : 'rgba(139, 115, 85, 0.2)') : 'transparent',
                    }}
                  >
                    עברית
                  </button>
                </div>

                <a
                  href="/Zayit/download"
                  className="flex items-center justify-center gap-2 px-5 py-3 rounded-full text-sm font-semibold text-white"
                  style={{
                    background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                  }}
                >
                  <Download size={16} />
                  {t('nav.download')}
                </a>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.nav>
  );
}
