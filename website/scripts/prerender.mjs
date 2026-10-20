// Writes a static HTML file per page and language, the sitemap, and WebP copies of the screenshots.
// Runs after `vite build` (client, in dist/) and `vite build --ssr` (renderer, in dist-ssr/).
import { mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import sharp from 'sharp';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(root, 'dist');
const ssrDir = join(root, 'dist-ssr');

const { render, LANGS, PAGES, SITE_URL, pagePath } = await import(pathToFileURL(join(ssrDir, 'entry-server.js')).href);
const template = await readFile(join(dist, 'index.html'), 'utf8');

for (const lang of LANGS) {
  for (const page of PAGES) {
    const rendered = await render(lang, page);
    // React emits the preloads of high-priority images ahead of the markup; they belong in the head
    const preloads = rendered.html.match(/^(<link [^>]*\/>)+/)?.[0] ?? '';
    const html = rendered.html.slice(preloads.length);
    const out = template
      .replace('<html lang="en" dir="ltr">', `<html lang="${lang}" dir="${rendered.dir}">`)
      .replace('<!--app-head-->', `${rendered.head}\n    ${preloads}`)
      .replace('<!--app-html-->', html);
    const file = join(dist, pagePath(lang, page), 'index.html');
    await mkdir(dirname(file), { recursive: true });
    await writeFile(file, out);
    console.log(`prerendered ${pagePath(lang, page)}`);
  }
}

// Every page with its alternates in each language, as its hreflang in the head
const today = new Date().toISOString().slice(0, 10);
const alternates = (page) =>
  [...LANGS.map((l) => [l, pagePath(l, page)]), ['x-default', pagePath('en', page)]]
    .map(([hreflang, path]) => `    <xhtml:link rel="alternate" hreflang="${hreflang}" href="${SITE_URL}${path}"/>`)
    .join('\n');
const urls = PAGES.flatMap((page) =>
  LANGS.map(
    (lang) => `  <url>
    <loc>${SITE_URL}${pagePath(lang, page)}</loc>
    <lastmod>${today}</lastmod>
${alternates(page)}
  </url>`,
  ),
);
await writeFile(
  join(dist, 'sitemap.xml'),
  `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${urls.join('\n')}
</urlset>
`,
);

// The screenshots stay PNG/JPEG in the sources (the e2e showcase writes them); the site serves WebP
const art = join(dist, 'art');
for (const name of await readdir(art)) {
  if (!/\.(png|jpe?g)$/i.test(name)) continue;
  await sharp(join(art, name))
    .webp({ quality: 82 })
    .toFile(join(art, name.replace(/\.(png|jpe?g)$/i, '.webp')));
}

await rm(ssrDir, { recursive: true, force: true });
