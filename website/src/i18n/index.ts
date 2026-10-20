import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './en.json';
import he from './he.json';
import { parseRoute, type Lang } from '../seo';

const LANGUAGE_STORAGE_KEY = 'language'; // Synced with /download-website

const isBrowser = typeof window !== 'undefined';

/** The language the reader chose before, else the one of their browser. */
export function preferredLanguage(): Lang {
  if (!isBrowser) return 'en';
  const saved = localStorage.getItem(LANGUAGE_STORAGE_KEY);
  if (saved === 'en' || saved === 'he') return saved;
  const browserLang = navigator.language || navigator.languages?.[0] || 'en';
  return browserLang.startsWith('he') ? 'he' : 'en';
}

// The URL carries the language (/he/…), so every page renders the same for crawlers and readers
const initialLang: Lang = isBrowser ? parseRoute(window.location.pathname).lang : 'en';

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    he: { translation: he },
  },
  lng: initialLang,
  fallbackLng: 'en',
  interpolation: {
    escapeValue: false,
  },
});

export { LANGUAGE_STORAGE_KEY };
export default i18n;
