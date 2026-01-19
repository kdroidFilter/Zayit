# Zayit Website - HTML Version

A clean, vanilla HTML website for the Zayit Jewish study library application.

## Structure

```
website-html/
├── index.html              # Main homepage
├── download.html           # Download page with platform detection
├── css/
│   └── styles.css         # All website styles
├── images/
│   ├── icons.svg          # SVG icon sprite sheet
│   ├── art/               # Screenshot images
│   ├── *.png              # Favicon and app icons
│   └── *.ico, *.svg       # Additional icons
├── downloads/
│   ├── windows/           # Windows installers (.exe, .msi)
│   ├── macos/             # macOS packages (.dmg)
│   ├── linux/             # Linux packages (.deb, .rpm)
│   └── database/          # Offline database files
├── download-count.json    # Download statistics
├── manifest.json          # Web app manifest
├── robots.txt             # Search engine directives
├── sitemap.xml            # Site structure for SEO
└── humans.txt             # Credits and info

```

## Features

- **Clean vanilla HTML/CSS/JS** - No frameworks or dependencies
- **Responsive design** - Works on desktop and mobile
- **Theme switching** - Light/dark mode support
- **Bilingual** - English and Hebrew with RTL support
- **System detection** - Automatically detects user's OS
- **Professional download page** - Multiple formats and architectures
- **SEO optimized** - Proper meta tags, sitemap, structured data

## Usage

Simply serve the files from any web server. No build process required.

## Browser Support

- Modern browsers with CSS Grid and Flexbox support
- JavaScript required for theme switching and download functionality