import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * Hook to dynamically update SEO meta tags based on the current language
 */
export function useSEO() {
  const { t, i18n } = useTranslation();
  const currentLang = i18n.language;

  useEffect(() => {
    // Update document language and direction
    document.documentElement.lang = currentLang;
    document.documentElement.dir = currentLang === 'he' ? 'rtl' : 'ltr';

    // Primary Meta Tags
    const title = t('seo.title');
    document.title = title;

    updateMetaTag('name', 'title', title);
    updateMetaTag('name', 'description', t('seo.description'));
    updateMetaTag('name', 'keywords', t('seo.keywords'));
    updateMetaTag('name', 'language', currentLang);

    // Open Graph
    updateMetaTag('property', 'og:title', t('seo.ogTitle'));
    updateMetaTag('property', 'og:description', t('seo.ogDescription'));
    updateMetaTag('property', 'og:locale', currentLang === 'he' ? 'he_IL' : 'en_US');
    updateMetaTag('property', 'og:image:alt', t('seo.imageAlt'));

    // Twitter Card
    updateMetaTag('name', 'twitter:title', t('seo.twitterTitle'));
    updateMetaTag('name', 'twitter:description', t('seo.twitterDescription'));
    updateMetaTag('name', 'twitter:image:alt', t('seo.imageAlt'));

    // Update canonical and hreflang for current language
    const langParam = currentLang === 'he' ? '?lang=he' : '';
    updateLinkTag('canonical', `https://www.zayitapp.com/${langParam}`);

    // Update JSON-LD structured data
    updateJsonLd(currentLang, t);
  }, [currentLang, t]);
}

function updateMetaTag(
  attributeType: 'name' | 'property',
  attributeValue: string,
  content: string
) {
  let element = document.querySelector(
    `meta[${attributeType}="${attributeValue}"]`
  ) as HTMLMetaElement | null;

  if (element) {
    element.content = content;
  } else {
    element = document.createElement('meta');
    element.setAttribute(attributeType, attributeValue);
    element.content = content;
    document.head.appendChild(element);
  }
}

function updateLinkTag(rel: string, href: string) {
  let element = document.querySelector(
    `link[rel="${rel}"]`
  ) as HTMLLinkElement | null;

  if (element) {
    element.href = href;
  }
}

function updateJsonLd(lang: string, t: (key: string) => string) {
  // Update SoftwareApplication schema
  const softwareAppScript = document.querySelector(
    'script[type="application/ld+json"]'
  );

  if (softwareAppScript) {
    try {
      const data = JSON.parse(softwareAppScript.textContent || '{}');
      if (data['@type'] === 'SoftwareApplication') {
        data.name = lang === 'he' ? 'זית' : 'Zayit';
        data.alternateName = t('seo.title');
        data.description = t('seo.description');
        softwareAppScript.textContent = JSON.stringify(data, null, 2);
      }
    } catch {
      // JSON parsing failed, skip update
    }
  }

  // Update WebSite schema
  const scripts = document.querySelectorAll(
    'script[type="application/ld+json"]'
  );
  scripts.forEach((script) => {
    try {
      const data = JSON.parse(script.textContent || '{}');
      if (data['@type'] === 'WebSite') {
        data.name = t('seo.title');
        data.description = t('seo.description');
        script.textContent = JSON.stringify(data, null, 2);
      }
    } catch {
      // JSON parsing failed, skip update
    }
  });
}

export default useSEO;
