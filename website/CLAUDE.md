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
├── App.tsx              # Main landing page component (single-page app)
├── main.tsx             # React entry point with ThemeProvider
├── index.css            # Tailwind + CSS custom properties for theming
├── components/
│   ├── Navigation.tsx   # Responsive nav with mobile menu, theme toggle, language selector
│   └── ImageComparison.tsx  # Light/dark image comparison slider component
├── contexts/
│   └── ThemeContext.tsx # Theme state (light/dark/system) with localStorage persistence
├── hooks/
│   └── useTheme.ts      # Theme hook (re-exports from context)
└── i18n/
    ├── index.ts         # i18next configuration with browser detection
    ├── en.json          # English translations
    └── he.json          # Hebrew translations
```

### Key Patterns

**Theming**: Uses CSS custom properties defined in `index.css` that change based on `.dark` class on `<html>`. The `ThemeContext` manages the theme state and applies the class.

**Internationalization**: i18next with automatic browser language detection. Hebrew (`he`) triggers RTL mode via `document.documentElement.dir = 'rtl'`. Language preference persists to localStorage under key `language`.

**Image Comparison Component**: A reusable slider that shows light/dark mode screenshots with drag-to-compare functionality. Automatically positions based on current theme.

## Important Conventions

- **Styling**: Use Tailwind utility classes combined with inline `style` props for theme-aware colors (e.g., `style={{ color: 'var(--gold)' }}`). Theme variables are defined in `index.css`.
- **Translations**: All user-facing text uses `t()` from `useTranslation()`. Add new keys to both `en.json` and `he.json`.
- **RTL Support**: Check `i18n.language === 'he'` for RTL-specific layout adjustments.
- **Assets**: Static images go in `public/` and are referenced with `/` prefix (e.g., `/icon.png`).

## Relationship to Parent Project

This website is a subproject of the main Zayit repository (`/Users/elie/IdeaProjects/Zayit/`). The main application is a Kotlin Multiplatform desktop app built with Compose. This website serves as the marketing/download landing page. See the parent directory's `CLAUDE.md` for the main application documentation.
