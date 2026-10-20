import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AnimatePresence, MotionConfig } from 'framer-motion';
import App from './App';
import { DownloadModal } from './pages/DownloadPage';
import { ThemeProvider } from './contexts/ThemeContext';
import { useSEO } from './hooks/useSEO';
import { parseRoute } from './seo';

function Pages() {
  const { pathname } = useLocation();
  const { lang, page } = parseRoute(pathname);
  const { i18n } = useTranslation();

  // The path is the source of truth for the language
  useEffect(() => {
    if (i18n.language !== lang) i18n.changeLanguage(lang);
  }, [i18n, lang]);

  useSEO(lang, page);

  return (
    <>
      <App isHome={page === 'home'} />
      <AnimatePresence>{page === 'download' && <DownloadModal />}</AnimatePresence>
    </>
  );
}

/** The whole site, under the router its entry provides (browser or static). */
export function Root() {
  return (
    // Reduced motion: transforms are dropped, fades stay
    <MotionConfig reducedMotion="user">
      <ThemeProvider>
        <Pages />
      </ThemeProvider>
    </MotionConfig>
  );
}
