import { useRef, useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, useScroll, useTransform } from 'framer-motion';
import {
  Search,
  BookOpen,
  Copy,
  Sun,
  Zap,
  Heart,
  Download,
  Sparkles,
  Shield,
  BookMarked,
  Languages,
  Infinity as InfinityIcon,
  Library,
  WifiOff,
} from 'lucide-react';
import { Navigation } from './components/Navigation';
import { ImageComparison } from './components/ImageComparison';
import { CrystalParticlesGL } from './components/CrystalParticlesGL';
import './i18n';

function App() {
  const { t, i18n } = useTranslation();
  const isRTL = i18n.language === 'he';

  // Scroll-based animation for hero image
  const heroRef = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: heroRef,
    offset: ["start start", "end start"]
  });

  const imageScale = useTransform(scrollYProgress, [0, 1], [1.15, 1]);
  const imageY = useTransform(scrollYProgress, [0, 1], [0, 50]);

  // Fetch download count
  const [downloadCount, setDownloadCount] = useState<number | null>(null);
  useEffect(() => {
    fetch('/Zayit/download-count.json')
      .then(res => res.json())
      .then(data => setDownloadCount(data.count))
      .catch(() => setDownloadCount(null));
  }, []);

  const features = [
    { icon: Search, key: 'find' },
    { icon: BookOpen, key: 'explore' },
    { icon: BookMarked, key: 'inbook' },
    { icon: Languages, key: 'compare' },
    { icon: Zap, key: 'sources' },
    { icon: Copy, key: 'copy' },
    { icon: Sun, key: 'themes' },
  ];

  const searchFeatures = [
    t('search.feature1'),
    t('search.feature2'),
    t('search.feature3'),
    t('search.feature4'),
  ];

  // Cinematic easing curve
  const cinematicEase = [0.16, 1, 0.3, 1] as const;

  // Memoized particles for cinematic effect (reduced for performance)
  const particles = useMemo(() =>
    [...Array(20)].map((_, i) => ({
      id: i,
      size: Math.random() * 4 + 2,
      x: Math.random() * 100,
      y: 60 + Math.random() * 40,
      opacity: Math.random() * 0.4 + 0.2,
      duration: Math.random() * 15 + 10,
      delay: Math.random() * 8,
      yMove: -150 - Math.random() * 200,
    }))
  , []);


  return (
    <div
      className="min-h-screen relative"
      style={{
        background: `radial-gradient(ellipse at top, var(--bg-gradient-top) 0%, var(--bg-main) 60%)`,
        color: 'var(--text-main)',
      }}
    >
      {/* Crystal Sparkle Particles Layer - WebGL with shaders for GPU acceleration */}
      <CrystalParticlesGL count={50} />

      <Navigation />

      {/* Hero Section - Cinematic Title + Image */}
      <section ref={heroRef} className="relative min-h-[60vh] md:min-h-screen w-full flex flex-col items-center justify-center overflow-hidden px-4 pt-16 md:pt-24 pb-2 md:pb-8">

        {/* Floating Particles (lightweight) */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {particles.map((p) => (
            <motion.div
              key={p.id}
              className="absolute rounded-full"
              style={{
                width: p.size,
                height: p.size,
                left: `${p.x}%`,
                top: `${p.y}%`,
                background: `rgba(230, 210, 140, ${p.opacity})`,
              }}
              initial={{ opacity: 0 }}
              animate={{
                opacity: [0, 1, 1, 0],
                y: [0, p.yMove],
              }}
              transition={{
                duration: p.duration,
                delay: p.delay,
                repeat: Infinity,
                ease: 'linear',
              }}
            />
          ))}
        </div>

        {/* Cinematic Dark Overlay that fades away */}
        <motion.div
          className="absolute inset-0 z-10 pointer-events-none"
          style={{ background: 'var(--bg-main)' }}
          initial={{ opacity: 1 }}
          animate={{ opacity: 0 }}
          transition={{ duration: 1.2, ease: 'easeOut' }}
        />

        {/* Hero Content with staggered children */}
        <motion.div
          className="relative z-20 flex flex-col items-center text-center"
          initial="hidden"
          animate="visible"
          variants={{
            hidden: {},
            visible: {
              transition: {
                staggerChildren: 0.4,
                delayChildren: 0.8,
              },
            },
          }}
        >
          {/* Title */}
          <motion.h1
            className="text-6xl md:text-[12rem] font-bold mb-1 md:mb-2"
            style={{
              color: 'var(--gold)',
              textShadow: '0 0 60px rgba(230, 210, 140, 0.5), 0 0 120px rgba(230, 210, 140, 0.3)',
            }}
            variants={{
              hidden: { opacity: 0, scale: 1.3, filter: 'blur(20px)' },
              visible: {
                opacity: 1,
                scale: 1,
                filter: 'blur(0px)',
                transition: { duration: 1.2, ease: [0.16, 1, 0.3, 1] }
              },
            }}
          >
            {t('hero.title')}
          </motion.h1>

          {/* Decorative line */}
          <motion.div
            className="h-[2px] mb-4 md:mb-8"
            style={{ background: `linear-gradient(90deg, transparent, var(--gold), transparent)` }}
            variants={{
              hidden: { width: 0, opacity: 0 },
              visible: {
                width: 200,
                opacity: 1,
                transition: { duration: 0.8, ease: [0.16, 1, 0.3, 1] }
              },
            }}
          />

          {/* Subtitle */}
          <motion.p
            className="text-lg md:text-3xl font-light mb-2 md:mb-4 max-w-3xl px-2"
            style={{ color: 'var(--text-main)' }}
            variants={{
              hidden: { opacity: 0, y: 20 },
              visible: {
                opacity: 1,
                y: 0,
                transition: { duration: 0.8, ease: 'easeOut' }
              },
            }}
          >
            {t('hero.subtitle')}
          </motion.p>

          {/* Tagline */}
          <motion.p
            className="text-base md:text-xl tracking-[0.2em] md:tracking-[0.3em] uppercase mb-6 md:mb-12"
            style={{ color: 'var(--gold-muted)' }}
            variants={{
              hidden: { opacity: 0, y: 10 },
              visible: {
                opacity: 1,
                y: 0,
                transition: { duration: 0.8, ease: 'easeOut' }
              },
            }}
          >
            {t('hero.tagline')}
          </motion.p>
        </motion.div>

        {/* App Screenshot */}
        <motion.div
          className="relative z-20 w-full max-w-5xl mt-4 md:mt-8 px-4 md:px-0"
          initial={{ opacity: 0, y: 60 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 1.2, delay: 2.8, ease: [0.16, 1, 0.3, 1] }}
          style={{
            scale: imageScale,
            y: imageY,
          }}
        >
          <ImageComparison
            lightImage="art/HOME-LIGHT.png"
            darkImage="art/HOME-DARK.png"
            alt=""
          />
        </motion.div>
      </section>

      
      {/* Vision Section - delayed to appear after hero */}
      <section className="py-10 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial={{ opacity: 0, y: 60 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 1.5, delay: 3.5, ease: cinematicEase }}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 1.2, delay: 4.2, ease: cinematicEase }}
          >
            <Sparkles size={16} />
            {t('vision.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: 30, filter: 'blur(10px)' }}
            animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
            transition={{ duration: 1.8, delay: 4.8, ease: cinematicEase }}
          >
            {t('vision.description')}
          </motion.p>
        </motion.div>
      </section>

      
      {/* Spirit Section - Slow dramatic scale reveal */}
      <section className="py-12 md:py-20 px-4 md:px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial={{ opacity: 0, scale: 0.85, filter: 'blur(15px)' }}
          whileInView={{ opacity: 1, scale: 1, filter: 'blur(0px)' }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 2, ease: cinematicEase }}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, y: -30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 1.5, delay: 0.8 }}
          >
            <Shield size={16} />
            {t('spirit.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 2, delay: 1.5 }}
          >
            {t('spirit.description')}
          </motion.p>
        </motion.div>
      </section>

      
      {/* Interface Section - Slow cinematic slide */}
      <section className="py-12 md:py-20 px-4 md:px-6 overflow-hidden">
        <div className="max-w-6xl mx-auto">
          <motion.div
            className="text-center mb-12"
            initial={{ opacity: 0, x: -80, filter: 'blur(10px)' }}
            whileInView={{ opacity: 1, x: 0, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 1.8, ease: cinematicEase }}
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('interface.title')}
            </h2>
            <motion.p
              className="text-lg max-w-2xl mx-auto mb-4"
              style={{ color: 'var(--text-muted)' }}
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1.5, delay: 1 }}
            >
              {t('interface.description')}
            </motion.p>
            <motion.p
              className="text-base font-medium"
              style={{ color: 'var(--gold)' }}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 1.2, delay: 2 }}
            >
              {t('interface.noLearning')}
            </motion.p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, x: 150, scale: 0.9 }}
            whileInView={{ opacity: 1, x: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.2 }}
            transition={{ duration: 2.5, ease: cinematicEase }}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('interface.bookSearch')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('interface.bookSearchDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="art/BOOK-SEARCH-LIGHT.png"
              darkImage="art/BOOK-SEARCH-DARK.png"
              alt={isRTL ? 'חיפוש ספרים' : 'Book Search'}
            />
          </motion.div>
        </div>
      </section>

      
      {/* Modular Panels Section - Slow dramatic alternating reveals */}
      <section className="py-12 md:py-20 px-4 md:px-6 overflow-hidden" style={{ background: 'var(--section-alt-bg)' }}>
        <div className="max-w-6xl mx-auto space-y-16 md:space-y-24">
          <motion.div
            className="text-center mb-12"
            initial={{ opacity: 0, y: 60, filter: 'blur(15px)' }}
            whileInView={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 2, ease: cinematicEase }}
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('panels.title')}
            </h2>
            <motion.p
              className="text-lg max-w-2xl mx-auto"
              style={{ color: 'var(--text-muted)' }}
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1.5, delay: 1.2 }}
            >
              {t('panels.description')}
            </motion.p>
          </motion.div>

          {/* Commentaries */}
          <motion.div
            initial={{ opacity: 0, x: -200, rotate: -3 }}
            whileInView={{ opacity: 1, x: 0, rotate: 0 }}
            viewport={{ once: true, amount: 0.15 }}
            transition={{ duration: 2.5, ease: cinematicEase }}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('panels.commentaries')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('panels.commentariesDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="art/PIRUSHIM-LIGHT.png"
              darkImage="art/PIRUSHIM-DARK.png"
              alt={isRTL ? 'פירושים' : 'Commentaries'}
            />
          </motion.div>

          {/* Commentaries & Translations */}
          <motion.div
            initial={{ opacity: 0, x: 200, rotate: 3 }}
            whileInView={{ opacity: 1, x: 0, rotate: 0 }}
            viewport={{ once: true, amount: 0.15 }}
            transition={{ duration: 2.5, ease: cinematicEase }}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('panels.translations')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('panels.translationsDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="art/PIRUSHIM-TARGUMIM-LIGHT.png"
              darkImage="art/PIRUSHIM-TARGUMIM-DARK.png"
              alt={isRTL ? 'פירושים ותרגומים' : 'Commentaries and Translations'}
            />
          </motion.div>

          {/* Sources */}
          <motion.div
            initial={{ opacity: 0, scale: 0.7, filter: 'blur(20px)' }}
            whileInView={{ opacity: 1, scale: 1, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.15 }}
            transition={{ duration: 2.5, ease: cinematicEase }}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('panels.sources')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('panels.sourcesDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="art/MEKOR-LIGHT.png"
              darkImage="art/MEKOR-DARK.png"
              alt={isRTL ? 'מקורות' : 'Sources'}
            />
          </motion.div>
        </div>
      </section>

      
      {/* Search Section - Slow dramatic reveal with suspense */}
      <section id="search" className="py-12 md:py-20 px-4 md:px-6 overflow-hidden">
        <div className="max-w-6xl mx-auto">
          <motion.div
            className="text-center mb-12"
            initial={{ opacity: 0, y: 80, filter: 'blur(20px)' }}
            whileInView={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 2.2, ease: cinematicEase }}
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('search.title')}
            </h2>
            <motion.p
              className="text-lg max-w-2xl mx-auto mb-6"
              style={{ color: 'var(--text-muted)' }}
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1.5, delay: 1.2 }}
            >
              {t('search.description')}
            </motion.p>
            <motion.p
              className="text-sm font-medium"
              style={{ color: 'var(--gold-soft)' }}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 1.2, delay: 2 }}
            >
              {t('search.powered')}
            </motion.p>
          </motion.div>

          {/* Search Features Grid - slow sequential reveal */}
          <div className="grid md:grid-cols-2 gap-4 mb-12 max-w-3xl mx-auto">
            {searchFeatures.map((feature, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, x: index % 2 === 0 ? -100 : 100, scale: 0.9 }}
                whileInView={{ opacity: 1, x: 0, scale: 1 }}
                viewport={{ once: true, amount: 0.5 }}
                transition={{ duration: 1.5, delay: index * 0.4, ease: cinematicEase }}
                className="flex items-start gap-3 p-4 rounded-xl"
                style={{
                  background: 'linear-gradient(135deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                  border: '1px solid rgba(230, 210, 140, 0.25)',
                  backdropFilter: 'blur(8px)',
                  boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.08), 0 4px 20px rgba(0, 0, 0, 0.4)',
                }}
              >
                <motion.div
                  className="w-2 h-2 rounded-full mt-2 flex-shrink-0"
                  style={{ background: 'var(--gold)' }}
                  initial={{ scale: 0 }}
                  whileInView={{ scale: 1 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.8, delay: index * 0.4 + 0.8 }}
                />
                <p style={{ color: 'var(--text-muted)' }}>{feature}</p>
              </motion.div>
            ))}
          </div>

          {/* Search Taglines */}
          <motion.div
            className="flex justify-center gap-8 mb-12 flex-wrap"
            initial={{ opacity: 0, scale: 0.8 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 1.5 }}
          >
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.simple')}
            </span>
            <span style={{ color: 'var(--text-muted)' }}>|</span>
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.advanced')}
            </span>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 100, rotateX: 15, scale: 0.85 }}
            whileInView={{ opacity: 1, y: 0, rotateX: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.15 }}
            transition={{ duration: 2.5, ease: cinematicEase }}
            style={{ perspective: 1200 }}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('search.simpleTitle')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('search.simpleDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="art/DB-SEARCH-SIMPLE-LIGHT.png"
              darkImage="art/DB-SEARCH-SIMPLE-DARK.png"
              alt={isRTL ? 'חיפוש בבסיס הנתונים' : 'Database Search'}
            />
          </motion.div>
        </div>
      </section>

      
      {/* Features Section - Slow dramatic card reveal */}
      <section id="features" className="py-12 md:py-20 px-4 md:px-6 overflow-hidden">
        <div className="max-w-6xl mx-auto">
          <motion.div
            className="text-center mb-12 md:mb-16"
            initial={{ opacity: 0, scale: 0.7, filter: 'blur(20px)' }}
            whileInView={{ opacity: 1, scale: 1, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 2, ease: cinematicEase }}
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('features.title')}
            </h2>
          </motion.div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
            {features.map((feature, index) => {
              // Slow sequential reveal with generous delays
              const delay = index * 0.35;

              return (
                <motion.div
                  key={feature.key}
                  initial={{ opacity: 0, scale: 0.6, y: 80, filter: 'blur(15px)' }}
                  whileInView={{ opacity: 1, scale: 1, y: 0, filter: 'blur(0px)' }}
                  viewport={{ once: true, amount: 0.2 }}
                  transition={{ duration: 1.8, delay, ease: cinematicEase }}
                  whileHover={{ scale: 1.02, y: -5 }}
                  className="p-6 rounded-2xl transition-colors"
                  style={{
                    background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                    border: '1px solid rgba(230, 210, 140, 0.2)',
                    backdropFilter: 'blur(8px)',
                    boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
                  }}
                >
                  <motion.div
                    className="w-12 h-12 rounded-xl flex items-center justify-center mb-4"
                    style={{
                      background: 'linear-gradient(135deg, rgba(230, 210, 140, 0.15) 0%, rgba(230, 210, 140, 0.05) 100%)',
                    }}
                    initial={{ rotate: -180, scale: 0 }}
                    whileInView={{ rotate: 0, scale: 1 }}
                    viewport={{ once: true }}
                    transition={{ duration: 1.2, delay: delay + 0.6, ease: cinematicEase }}
                  >
                    <feature.icon size={24} style={{ color: 'var(--gold)' }} />
                  </motion.div>
                  <motion.p
                    className="text-base leading-relaxed"
                    style={{ color: 'var(--text-muted)' }}
                    initial={{ opacity: 0 }}
                    whileInView={{ opacity: 1 }}
                    viewport={{ once: true }}
                    transition={{ duration: 1, delay: delay + 1 }}
                  >
                    {t(`features.${feature.key}`)}
                  </motion.p>
                </motion.div>
              );
            })}
          </div>
        </div>
      </section>

      
      {/* Promise Section - Slow 3D reveal with suspense */}
      <section className="py-12 md:py-20 px-4 md:px-6 overflow-hidden" style={{ background: 'var(--section-alt-bg)', perspective: '1200px' }}>
        <div className="max-w-5xl mx-auto">
          <motion.div
            className="text-center mb-12"
            initial={{ opacity: 0, rotateX: 30, filter: 'blur(15px)' }}
            whileInView={{ opacity: 1, rotateX: 0, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 2, ease: cinematicEase }}
          >
            <h2
              className="text-3xl md:text-4xl font-bold"
              style={{ color: 'var(--text-main)' }}
            >
              {t('promise.title')}
            </h2>
          </motion.div>

          <div className="grid md:grid-cols-2 gap-6 md:gap-8">
            {[
              { icon: Zap, title: 'speed', desc: 'speedDesc' },
              { icon: InfinityIcon, title: 'free', desc: 'freeDesc' },
              { icon: Library, title: 'library', desc: 'libraryDesc' },
              { icon: WifiOff, title: 'offline', desc: 'offlineDesc' },
            ].map((item, index) => (
              <motion.div
                key={item.title}
                initial={{ opacity: 0, rotateY: index % 2 === 0 ? -45 : 45, scale: 0.8, filter: 'blur(10px)' }}
                whileInView={{ opacity: 1, rotateY: 0, scale: 1, filter: 'blur(0px)' }}
                viewport={{ once: true, amount: 0.25 }}
                transition={{ duration: 2, delay: index * 0.5, ease: cinematicEase }}
                whileHover={{ scale: 1.02, y: -5 }}
                className="p-6 md:p-8 rounded-2xl text-center"
                style={{
                  background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                  border: '1px solid rgba(230, 210, 140, 0.2)',
                  boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
                  transformStyle: 'preserve-3d',
                }}
              >
                <motion.div
                  className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6"
                  style={{
                    background: 'linear-gradient(135deg, rgba(230, 210, 140, 0.2) 0%, rgba(230, 210, 140, 0.05) 100%)',
                  }}
                  initial={{ scale: 0, rotate: -180 }}
                  whileInView={{ scale: 1, rotate: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 1.5, delay: index * 0.5 + 0.8, ease: cinematicEase }}
                >
                  <item.icon size={32} style={{ color: 'var(--gold)' }} />
                </motion.div>
                <motion.h3
                  className="text-2xl md:text-3xl font-bold mb-4"
                  style={{ color: 'var(--gold)' }}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 1.2, delay: index * 0.5 + 1.2 }}
                >
                  {t(`promise.${item.title}`)}
                </motion.h3>
                <motion.p
                  className="text-base md:text-lg leading-relaxed"
                  style={{ color: 'var(--text-muted)' }}
                  initial={{ opacity: 0, y: 15 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 1.2, delay: index * 0.5 + 1.6 }}
                >
                  {t(`promise.${item.desc}`)}
                </motion.p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      
      {/* Crafted Section - Slow elegant text reveal */}
      <section className="py-12 md:py-20 px-4 md:px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, y: -40, scale: 0.7 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 1.5, ease: cinematicEase }}
          >
            <motion.span
              initial={{ rotate: 0, scale: 0 }}
              whileInView={{ rotate: [0, -20, 20, -15, 15, 0], scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1.5, delay: 0.8 }}
            >
              <Heart size={16} />
            </motion.span>
            {t('crafted.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: 60, filter: 'blur(20px)' }}
            whileInView={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 2.5, delay: 0.5, ease: cinematicEase }}
          >
            {t('crafted.description')}
          </motion.p>
        </div>
      </section>

      
      {/* Download Section - Slow dramatic finale */}
      <section
        id="download"
        className="py-16 md:py-24 px-4 md:px-6 overflow-hidden"
        style={{
          background: 'linear-gradient(180deg, rgba(230, 210, 140, 0.08) 0%, rgba(230, 210, 140, 0.02) 100%)',
        }}
      >
        <div className="max-w-4xl mx-auto text-center">
          <motion.h2
            className="text-4xl md:text-5xl font-bold mb-6"
            style={{ color: 'var(--text-main)' }}
            initial={{ opacity: 0, scale: 2, filter: 'blur(30px)' }}
            whileInView={{ opacity: 1, scale: 1, filter: 'blur(0px)' }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 2.5, ease: cinematicEase }}
          >
            {t('download.title')}
          </motion.h2>

          <motion.p
            className="text-xl mb-8"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 1.5, delay: 1.2, ease: cinematicEase }}
          >
            {t('download.description')}
          </motion.p>

          {downloadCount !== null && (
            <motion.p
              className="text-2xl font-bold mb-8"
              style={{ color: 'var(--gold)' }}
              initial={{ opacity: 0, scale: 0.3 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1.5, delay: 2, ease: cinematicEase }}
            >
              +{downloadCount.toLocaleString()} {t('download.downloads')}
            </motion.p>
          )}

          <motion.div
            initial={{ opacity: 0, y: 50, scale: 0.8 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 1.8, delay: 2.5, ease: cinematicEase }}
          >
            <motion.a
              href="/Zayit/download"
              className="inline-flex items-center gap-3 px-8 md:px-10 py-4 md:py-5 rounded-full text-lg md:text-xl font-semibold text-white"
              style={{
                background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                boxShadow: '0 15px 40px rgba(230, 210, 140, 0.3)',
              }}
              whileHover={{ scale: 1.05, boxShadow: '0 20px 50px rgba(230, 210, 140, 0.5)' }}
              whileTap={{ scale: 0.98 }}
              animate={{
                boxShadow: ['0 15px 40px rgba(230, 210, 140, 0.3)', '0 20px 60px rgba(230, 210, 140, 0.5)', '0 15px 40px rgba(230, 210, 140, 0.3)'],
              }}
              transition={{
                boxShadow: { duration: 3, repeat: Infinity, ease: 'easeInOut' }
              }}
            >
              <Download size={26} />
              {t('download.cta')}
            </motion.a>
          </motion.div>

          <motion.p
            className="mt-6 text-sm"
            style={{ color: 'var(--gold-muted)' }}
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 1.5, delay: 3.5 }}
          >
            {t('download.platforms')}
          </motion.p>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-12 px-6" style={{ borderTop: '1px solid var(--card-border)' }}>
        <div className="max-w-6xl mx-auto text-center">
          <p className="text-sm mb-2" style={{ color: 'var(--text-muted)' }}>
            {t('footer.createdBy')} &#10084;
          </p>
          <p className="text-xs" style={{ color: 'var(--gold-muted)' }}>
            {t('footer.license')}
          </p>
        </div>
      </footer>
    </div>
  );
}

export default App;
