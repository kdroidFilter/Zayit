import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { LANGUAGE_STORAGE_KEY } from '../i18n';
import { pagePath, type Page } from '../seo';

const OPEN_CORE_EN = 'https://github.com/kdroidFilter/Zayit#open-core';
const OPEN_CORE_HE = 'https://github.com/kdroidFilter/Zayit/blob/master/README_HE.md#ליבה-פתוחה-open-core';

/** The footer of every page; it links the page in the other language, so crawlers find both. */
export function SiteFooter({ page }: { page: Page }) {
  const { t, i18n } = useTranslation();

  return (
    <footer className="py-12 px-6" style={{ borderTop: '1px solid var(--card-border)' }}>
      <div className="max-w-6xl mx-auto text-center">
        <p className="text-sm mb-2" style={{ color: 'var(--text-muted)' }}>
          {t('footer.createdByPrefix')}{' '}
          <a
            href="https://eliegambache.kdroidfilter.com/"
            target="_blank"
            rel="noopener noreferrer"
            className="hover:underline"
            style={{ color: 'var(--gold)', textDecoration: 'none' }}
          >
            {t('footer.createdByName')}
          </a>{' '}
          &#10084;
        </p>
        <p className="text-xs max-w-xl mx-auto leading-relaxed" style={{ color: 'var(--gold-muted)' }}>
          {t('footer.license')}{' '}
          <a href={i18n.language === 'he' ? OPEN_CORE_HE : OPEN_CORE_EN} target="_blank" rel="noopener noreferrer" className="underline hover:no-underline" style={{ color: 'var(--gold-soft)' }}>
            {t('footer.openCore')}
          </a>
        </p>
        <nav className="mt-4 flex justify-center gap-4 text-xs" aria-label="Language">
          <Link to={pagePath('en', page)} hrefLang="en" lang="en" onClick={() => localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en')} style={{ color: 'var(--gold-muted)' }}>
            English
          </Link>
          <Link to={pagePath('he', page)} hrefLang="he" lang="he" onClick={() => localStorage.setItem(LANGUAGE_STORAGE_KEY, 'he')} style={{ color: 'var(--gold-muted)' }}>
            עברית
          </Link>
        </nav>
      </div>
    </footer>
  );
}
