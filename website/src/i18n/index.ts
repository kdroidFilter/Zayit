import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './en.json';
import he from './he.json';

// Detect browser language
const getBrowserLanguage = (): string => {
  const browserLang = navigator.language || navigator.languages?.[0] || 'en';
  // Check if Hebrew is preferred
  if (browserLang.startsWith('he')) {
    return 'he';
  }
  return 'en';
};

const detectedLang = getBrowserLanguage();

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

export default i18n;
