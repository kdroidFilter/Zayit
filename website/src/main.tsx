import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import i18n, { preferredLanguage } from './i18n'
import { Root } from './Root'
import { pagePath, parseRoute, type Lang } from './seo'

// Old URLs (?lang=he, #/download) move to their paths; a reader landing on the English root in Hebrew goes to /he/
function canonicalLocation(): string | null {
  const { pathname, search, hash } = window.location
  const params = new URLSearchParams(search)
  const queryLang = params.get('lang')
  const { lang: pathLang, page: pathPage } = parseRoute(pathname)
  const page = hash.startsWith('#/download') ? 'download' : pathPage

  let lang: Lang = pathLang
  if (queryLang === 'he' || queryLang === 'en') lang = queryLang
  else if (pathLang === 'en' && preferredLanguage() === 'he') lang = 'he'

  params.delete('lang')
  const target = pagePath(lang, page)
  const query = params.toString() ? `?${params}` : ''
  const fragment = hash.startsWith('#/') ? '' : hash
  return target !== pathname || queryLang !== null || fragment !== hash ? target + query + fragment : null
}

const redirect = canonicalLocation()
if (redirect) window.history.replaceState(null, '', redirect)
i18n.changeLanguage(parseRoute(window.location.pathname).lang)

// The prerendered markup is for crawlers and the first paint; the app renders afresh over it
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Root />
    </BrowserRouter>
  </StrictMode>,
)
