// The site's pages, their URLs and the head they carry, shared by the prerender and the client

export const SITE_URL = 'https://zayitapp.com';

export type Lang = 'en' | 'he';
export type Page = 'home' | 'download';

export const LANGS: Lang[] = ['en', 'he'];
export const PAGES: Page[] = ['home', 'download'];

const OG_IMAGE = `${SITE_URL}/art/og-image.jpg`;

/** The path of a page, with the trailing slash GitHub Pages redirects to: `/`, `/he/`, `/download/`, `/he/download/`. */
export function pagePath(lang: Lang, page: Page): string {
  const prefix = lang === 'he' ? '/he/' : '/';
  return page === 'download' ? `${prefix}download/` : prefix;
}

export function parseRoute(pathname: string): { lang: Lang; page: Page } {
  const segments = pathname.split('/').filter(Boolean);
  const lang: Lang = segments[0] === 'he' ? 'he' : 'en';
  const rest = lang === 'he' ? segments.slice(1) : segments;
  return { lang, page: rest[0] === 'download' ? 'download' : 'home' };
}

export interface PageMeta {
  lang: Lang;
  dir: 'ltr' | 'rtl';
  title: string;
  description: string;
  url: string;
  alternates: { hreflang: string; href: string }[];
  jsonLd: object[];
}

export function pageMeta(lang: Lang, page: Page, t: (key: string) => string): PageMeta {
  const url = SITE_URL + pagePath(lang, page);
  const title = t(page === 'download' ? 'seo.downloadTitle' : 'seo.title');
  const description = t(page === 'download' ? 'seo.downloadDescription' : 'seo.description');
  const alternates = [
    ...LANGS.map((l) => ({ hreflang: l, href: SITE_URL + pagePath(l, page) })),
    { hreflang: 'x-default', href: SITE_URL + pagePath('en', page) },
  ];

  const jsonLd: object[] = [
    {
      '@context': 'https://schema.org',
      '@type': 'SoftwareApplication',
      name: lang === 'he' ? 'זית' : 'Zayit',
      alternateName: lang === 'he' ? 'Zayit' : 'זית',
      description: t('seo.description'),
      url: SITE_URL + pagePath(lang, 'home'),
      downloadUrl: SITE_URL + pagePath(lang, 'download'),
      applicationCategory: 'EducationalApplication',
      operatingSystem: 'Windows, macOS, Linux',
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
      isAccessibleForFree: true,
      image: OG_IMAGE,
      screenshot: `${SITE_URL}/art/HOME-DARK.jpg`,
      inLanguage: lang,
      author: { '@type': 'Organization', name: 'KDroid Filter', url: 'https://github.com/kdroidFilter' },
    },
  ];
  if (page === 'home') {
    jsonLd.push({
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: lang === 'he' ? 'זית' : 'Zayit',
      alternateName: lang === 'he' ? 'Zayit' : 'זית',
      url: SITE_URL + '/',
      inLanguage: lang,
    });
  }

  return { lang, dir: lang === 'he' ? 'rtl' : 'ltr', title, description, url, alternates, jsonLd };
}

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** The head tags of a page, written into its prerendered HTML. */
export function headTags(meta: PageMeta, imageAlt: string): string {
  const other = meta.lang === 'he' ? 'en_US' : 'he_IL';
  const tags = [
    `<title>${escapeHtml(meta.title)}</title>`,
    `<meta name="description" content="${escapeHtml(meta.description)}" />`,
    `<link rel="canonical" href="${meta.url}" />`,
    ...meta.alternates.map((a) => `<link rel="alternate" hreflang="${a.hreflang}" href="${a.href}" />`),
    `<meta property="og:type" content="website" />`,
    `<meta property="og:site_name" content="${meta.lang === 'he' ? 'זית' : 'Zayit'}" />`,
    `<meta property="og:url" content="${meta.url}" />`,
    `<meta property="og:title" content="${escapeHtml(meta.title)}" />`,
    `<meta property="og:description" content="${escapeHtml(meta.description)}" />`,
    `<meta property="og:image" content="${OG_IMAGE}" />`,
    `<meta property="og:image:width" content="1200" />`,
    `<meta property="og:image:height" content="630" />`,
    `<meta property="og:image:alt" content="${escapeHtml(imageAlt)}" />`,
    `<meta property="og:locale" content="${meta.lang === 'he' ? 'he_IL' : 'en_US'}" />`,
    `<meta property="og:locale:alternate" content="${other}" />`,
    `<meta name="twitter:card" content="summary_large_image" />`,
    `<meta name="twitter:title" content="${escapeHtml(meta.title)}" />`,
    `<meta name="twitter:description" content="${escapeHtml(meta.description)}" />`,
    `<meta name="twitter:image" content="${OG_IMAGE}" />`,
    `<meta name="twitter:image:alt" content="${escapeHtml(imageAlt)}" />`,
    ...meta.jsonLd.map((d) => `<script type="application/ld+json">${JSON.stringify(d).replace(/</g, '\\u003c')}</script>`),
  ];
  return tags.join('\n    ');
}
