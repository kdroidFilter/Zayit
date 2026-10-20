# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

```bash
# Start development server with hot reload
npm run dev

# Build for production (runs TypeScript check then Vite build)
npm run build

# Run ESLint
npm run lint

# Preview production build locally
npm run preview
```

## Project Overview

This is the **landing page website** for Zayit (זית), a Jewish religious text study application. The website is built with React 19, TypeScript, Vite, and Tailwind CSS 4.

**Base URL**: The site is deployed at `/` on `zayitapp.com` (configured in `vite.config.ts`).

## Architecture

### Core Stack
- **React 19** with TypeScript
- **Vite 7** for build tooling
- **Tailwind CSS 4** via `@tailwindcss/vite` plugin
- **Framer Motion** for animations
- **i18next** for internationalization (Hebrew/English with RTL support)
- **Lucide React** for icons

### Source Structure
```
src/
├── App.tsx              # Main landing page component
├── main.tsx             # Browser entry: legacy URL redirects, BrowserRouter
├── entry-server.tsx     # Prerender entry (StaticRouter)
├── Root.tsx             # Shared tree: theme, routes (landing or download page), language sync
├── seo.ts               # Pages, URLs, head tags and JSON-LD
├── index.css            # Tailwind + CSS custom properties for theming
├── components/
│   ├── Navigation.tsx   # Responsive nav with mobile menu, theme toggle, language selector
│   └── ImageComparison.tsx  # Light/dark image comparison slider component
├── contexts/
│   └── ThemeContext.tsx # Theme state (light/dark/system) with localStorage persistence
├── hooks/
│   └── useSEO.ts        # Keeps the head in step with in-app navigation
└── i18n/
    ├── index.ts         # i18next configuration (language from the path)
    ├── en.json          # English translations
    └── he.json          # Hebrew translations
```

### Key Patterns

**Theming**: Uses CSS custom properties defined in `index.css` that change based on `.dark` class on `<html>`. The `ThemeContext` manages the theme state and applies the class.

**Pages & URLs**: four pages, one per language and page: `/`, `/he/`, `/download/`, `/he/download/` (trailing slash, as GitHub Pages serves directories). `src/seo.ts` is the single source for paths (`pagePath`, `parseRoute`), titles, descriptions, canonical, hreflang and JSON-LD. The download page is its own page (`pages/DownloadPage.tsx`), with the shared `Navigation` and `SiteFooter`.

**Internationalization**: the path sets the language (`/he/…` is Hebrew, RTL); `Root.tsx` keeps i18next in step with it. Language switches are links to the other path, which save the choice to localStorage (`language`). On load, `main.tsx` moves old URLs (`?lang=he`, `#/download`) to their paths and sends a reader whose preferred language is Hebrew from `/` to `/he/`.

**Prerender (SEO)**: `npm run build` also builds `src/entry-server.tsx` for SSR and runs `scripts/prerender.mjs`, which writes each page's static HTML (markup + head from `seo.ts`), `sitemap.xml`, and a WebP copy of every screenshot in `art/` (the components load the WebP in production). The client renders afresh over the prerendered markup (`createRoot`, not hydration). SEO strings live under `seo` in `en.json`/`he.json`. Images in `public/art` are referenced with absolute paths (`/art/...`).

**Image Comparison Component**: A reusable slider that shows light/dark mode screenshots with drag-to-compare functionality. Automatically positions based on current theme.

## Important Conventions

- **Styling**: Use Tailwind utility classes combined with inline `style` props for theme-aware colors (e.g., `style={{ color: 'var(--gold)' }}`). Theme variables are defined in `index.css`.
- **Translations**: All user-facing text uses `t()` from `useTranslation()`. Add new keys to both `en.json` and `he.json`.
- **RTL Support**: Check `i18n.language === 'he'` for RTL-specific layout adjustments.
- **Assets**: Static images go in `public/` and are referenced with `/` prefix (e.g., `/icon.png`).

## Relationship to Parent Project

This website is a subproject of the main Zayit repository (`/Users/elie/IdeaProjects/Zayit/`). The main application is a Kotlin Multiplatform desktop app built with Compose. This website serves as the marketing/download landing page. See the parent directory's `CLAUDE.md` for the main application documentation.
