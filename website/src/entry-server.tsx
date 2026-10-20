/* eslint-disable react-refresh/only-export-components -- the prerender's entry, never hot-reloaded */
import { StrictMode } from 'react';
import { renderToString } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import i18n from './i18n';
import { Root } from './Root';
import { headTags, pageMeta, pagePath, type Lang, type Page } from './seo';

export { LANGS, PAGES, SITE_URL, pagePath } from './seo';

/** Renders a page for the prerender: its markup and its head. */
export async function render(lang: Lang, page: Page) {
  await i18n.changeLanguage(lang);
  const t = i18n.getFixedT(lang);
  const html = renderToString(
    <StrictMode>
      <StaticRouter location={pagePath(lang, page)}>
        <Root />
      </StaticRouter>
    </StrictMode>,
  );
  const meta = pageMeta(lang, page, t);
  return { html, head: headTags(meta, t('seo.imageAlt')), lang: meta.lang, dir: meta.dir };
}
