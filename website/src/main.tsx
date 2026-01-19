import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter, useLocation } from 'react-router-dom'
import { AnimatePresence } from 'framer-motion'
import './index.css'
import App from './App.tsx'
import { DownloadModal } from './pages/DownloadPage.tsx'
import { ThemeProvider } from './contexts/ThemeContext'

function AppWithModal() {
  const location = useLocation()
  const showDownloadModal = location.pathname === '/download'

  return (
    <>
      <App />
      <AnimatePresence>
        {showDownloadModal && <DownloadModal />}
      </AnimatePresence>
    </>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <HashRouter>
        <AppWithModal />
      </HashRouter>
    </ThemeProvider>
  </StrictMode>,
)
