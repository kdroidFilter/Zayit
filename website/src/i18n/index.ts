import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './en.json';
import he from './he.json';

const LANGUAGE_STORAGE_KEY = 'language'; // Synced with /download-website

// Detect browser language
const getBrowserLanguage = (): string => {
  const browserLang = navigator.language || navigator.languages?.[0] || 'en';
  // Check if Hebrew is preferred
  if (browserLang.startsWith('he')) {
    return 'he';
  }
  return 'en';
};

// Get saved language from localStorage or detect from browser
const getInitialLanguage = (): string => {
  const savedLang = localStorage.getItem(LANGUAGE_STORAGE_KEY);
  if (savedLang && ['en', 'he'].includes(savedLang)) {
    return savedLang;
  }
  return getBrowserLanguage();
};

const detectedLang = getInitialLanguage();

// Set document direction based on language
if (detectedLang === 'he') {
  document.documentElement.dir = 'rtl';
  document.documentElement.lang = 'he';
}

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    he: { translation: he },
  },
  lng: detectedLang,
  fallbackLng: 'en',
  interpolation: {
    escapeValue: false,
  },
});

export { LANGUAGE_STORAGE_KEY };
export default i18n;
