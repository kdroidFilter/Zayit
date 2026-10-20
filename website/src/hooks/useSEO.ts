import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { pageMeta, type Lang, type Page } from '../seo';

/**
 * Keeps the head in step with in-app navigation (language switch, download dialog). The first load already has the
 * right head, written by the prerender.
 */
export function useSEO(lang: Lang, page: Page) {
  const { i18n } = useTranslation();

  useEffect(() => {
    const meta = pageMeta(lang, page, i18n.getFixedT(lang));
    const root = document.documentElement;
    root.lang = meta.lang;
    root.dir = meta.dir;
    document.title = meta.title;

    setAttribute('meta[name="description"]', 'content', meta.description);
    setAttribute('link[rel="canonical"]', 'href', meta.url);
    setAttribute('meta[property="og:url"]', 'content', meta.url);
    setAttribute('meta[property="og:title"]', 'content', meta.title);
    setAttribute('meta[property="og:description"]', 'content', meta.description);
    setAttribute('meta[name="twitter:title"]', 'content', meta.title);
    setAttribute('meta[name="twitter:description"]', 'content', meta.description);
    for (const alternate of meta.alternates) {
      setAttribute(`link[rel="alternate"][hreflang="${alternate.hreflang}"]`, 'href', alternate.href);
    }
  }, [lang, page, i18n]);
}

function setAttribute(selector: string, attribute: string, value: string) {
  document.querySelector(selector)?.setAttribute(attribute, value);
}
