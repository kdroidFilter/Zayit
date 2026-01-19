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
import { useTheme } from './contexts/ThemeContext';
import './i18n';

function App() {
  const { t, i18n } = useTranslation();
  const { isDark } = useTheme();
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

  // Memoized particles for cinematic effect
  const particles = useMemo(() =>
    [...Array(60)].map((_, i) => ({
      id: i,
      size: Math.random() * 6 + 3,
      x: Math.random() * 100,
      y: 50 + Math.random() * 60,
      opacity: Math.random() * 0.6 + 0.4,
      glow: Math.random() * 15 + 10,
      duration: Math.random() * 12 + 8,
      delay: Math.random() * 6,
      yMove: -200 - Math.random() * 400,
      xMove: (Math.random() - 0.5) * 200,
    }))
  , []);

  return (
    <div
      className="min-h-screen"
      style={{
        background: `radial-gradient(ellipse at top, var(--bg-gradient-top) 0%, var(--bg-main) 60%)`,
        color: 'var(--text-main)',
      }}
    >
      <Navigation />

      {/* Hero Section - Cinematic Title + Image */}
      <section ref={heroRef} className="relative min-h-[60vh] md:min-h-screen w-full flex flex-col items-center justify-center overflow-hidden px-4 pt-16 md:pt-24 pb-2 md:pb-8">

        {/* Floating Particles */}
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
                background: isDark
                  ? `rgba(230, 210, 140, ${p.opacity})`
                  : `rgba(180, 140, 60, ${p.opacity})`,
                boxShadow: isDark
                  ? `0 0 ${p.glow}px rgba(230, 210, 140, 0.7)`
                  : `0 0 ${p.glow * 1.5}px rgba(180, 140, 60, 0.8), 0 0 ${p.glow * 3}px rgba(180, 140, 60, 0.4)`,
              }}
              initial={{ opacity: 0, scale: 0 }}
              animate={{
                opacity: [0, 1, 1, 0],
                scale: [0, 1.5, 1, 0],
                y: [0, p.yMove],
                x: [0, p.xMove],
              }}
              transition={{
                duration: p.duration,
                delay: p.delay,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
            />
          ))}
        </div>

        {/* Secondary layer - larger glowing orbs */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {[...Array(12)].map((_, i) => (
            <motion.div
              key={`orb-${i}`}
              className="absolute rounded-full"
              style={{
                width: 8 + (i % 3) * 4,
                height: 8 + (i % 3) * 4,
                left: `${8 + i * 8}%`,
                top: '70%',
                background: isDark
                  ? 'radial-gradient(circle, rgba(230, 210, 140, 0.8) 0%, rgba(230, 210, 140, 0) 70%)'
                  : 'radial-gradient(circle, rgba(200, 160, 60, 0.9) 0%, rgba(200, 160, 60, 0) 70%)',
                boxShadow: isDark
                  ? '0 0 20px rgba(230, 210, 140, 0.5)'
                  : '0 0 30px rgba(200, 160, 60, 0.7), 0 0 60px rgba(200, 160, 60, 0.3)',
              }}
              initial={{ opacity: 0, scale: 0 }}
              animate={{
                opacity: [0, 0.8, 0.8, 0],
                scale: [0, 2, 1.5, 0],
                y: [0, -300 - (i % 4) * 100],
                x: [(i % 2 === 0 ? 1 : -1) * 50],
              }}
              transition={{
                duration: 10 + (i % 3) * 2,
                delay: i * 0.8,
                repeat: Infinity,
                ease: 'easeOut',
              }}
            />
          ))}
        </div>

        {/* Shimmer sparkles on title reveal */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {[...Array(20)].map((_, i) => (
            <motion.div
              key={`sparkle-${i}`}
              className="absolute"
              style={{
                width: 3,
                height: 3,
                left: `${5 + (i * 4.5)}%`,
                top: '45%',
                background: isDark ? 'rgba(230, 210, 140, 1)' : 'rgba(200, 160, 60, 1)',
                borderRadius: '50%',
              }}
              animate={{
                opacity: [0, 1, 0],
                scale: [0, 2.5, 0],
                boxShadow: [
                  '0 0 0px transparent',
                  isDark
                    ? '0 0 15px rgba(230, 210, 140, 1), 0 0 30px rgba(230, 210, 140, 0.5)'
                    : '0 0 20px rgba(200, 160, 60, 1), 0 0 40px rgba(200, 160, 60, 0.6)',
                  '0 0 0px transparent',
                ],
              }}
              transition={{
                duration: 1.5,
                delay: 1.2 + (i * 0.08),
                ease: 'easeOut',
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
          className="flex flex-col items-center text-center"
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
              textShadow: isDark
                ? '0 0 60px rgba(230, 210, 140, 0.5), 0 0 120px rgba(230, 210, 140, 0.3)'
                : '0 0 40px rgba(139, 115, 85, 0.3), 0 0 80px rgba(139, 115, 85, 0.2)',
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
          className="relative w-full max-w-6xl mt-4 md:mt-8 px-4 md:px-0"
          initial={{ opacity: 0, y: 60 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 1.2, delay: 2.8, ease: [0.16, 1, 0.3, 1] }}
          style={{
            scale: imageScale,
            y: imageY,
          }}
        >
          {/* Glow behind the image */}
          <motion.div
            className="absolute inset-0 -z-10 rounded-2xl"
            style={{
              background: isDark
                ? 'radial-gradient(ellipse at center, rgba(230, 210, 140, 0.15) 0%, transparent 70%)'
                : 'radial-gradient(ellipse at center, rgba(139, 115, 85, 0.1) 0%, transparent 70%)',
              filter: 'blur(40px)',
              transform: 'scale(1.2)',
            }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 2, delay: 4 }}
          />
          <div className="[&_img]:max-h-[35vh] [&_img]:md:max-h-[60vh] [&_img]:w-auto [&_img]:mx-auto">
            <ImageComparison
              lightImage="art/HOME-LIGHT.png"
              darkImage="art/HOME-DARK.png"
              alt=""
            />
          </div>
        </motion.div>
      </section>

      {/* Vision Section */}
      <section className="py-10 md:py-20 px-4 md:px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
          >
            <div
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
              style={{
                background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
                color: 'var(--gold)',
              }}
            >
              <Sparkles size={16} />
              {t('vision.title')}
            </div>

            <p
              className="text-xl md:text-2xl leading-relaxed"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('vision.description')}
            </p>
          </motion.div>
        </div>
      </section>

      {/* Spirit Section */}
      <section className="py-20 px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <div className="max-w-4xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
          >
            <div
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
              style={{
                background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
                color: 'var(--gold)',
              }}
            >
              <Shield size={16} />
              {t('spirit.title')}
            </div>

            <p
              className="text-xl md:text-2xl leading-relaxed"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('spirit.description')}
            </p>
          </motion.div>
        </div>
      </section>

      {/* Interface Section */}
      <section className="py-20 px-6">
        <div className="max-w-6xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
            className="text-center mb-12"
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('interface.title')}
            </h2>
            <p
              className="text-lg max-w-2xl mx-auto mb-4"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('interface.description')}
            </p>
            <p
              className="text-base font-medium"
              style={{ color: 'var(--gold)' }}
            >
              {t('interface.noLearning')}
            </p>
          </motion.div>

          <ImageComparison
            lightImage="art/BOOK-SEARCH-LIGHT.png"
            darkImage="art/BOOK-SEARCH-DARK.png"
            alt={isRTL ? 'חיפוש ספרים' : 'Book Search'}
          />
        </div>
      </section>

      {/* Modular Panels Section */}
      <section className="py-20 px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <div className="max-w-6xl mx-auto space-y-16">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
            className="text-center mb-12"
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('panels.title')}
            </h2>
            <p
              className="text-lg max-w-2xl mx-auto"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('panels.description')}
            </p>
          </motion.div>

          <ImageComparison
            lightImage="art/PIRUSHIM-LIGHT.png"
            darkImage="art/PIRUSHIM-DARK.png"
            alt={isRTL ? 'פירושים' : 'Commentaries'}
          />

          <ImageComparison
            lightImage="art/PIRUSHIM-TARGUMIM-LIGHT.png"
            darkImage="art/PIRUSHIM-TARGUMIM-DARK.png"
            alt={isRTL ? 'פירושים ותרגומים' : 'Commentaries and Translations'}
          />

          <ImageComparison
            lightImage="art/MEKOR-LIGHT.png"
            darkImage="art/MEKOR-DARK.png"
            alt={isRTL ? 'מקורות' : 'Sources'}
          />
        </div>
      </section>

      {/* Search Section */}
      <section id="search" className="py-20 px-6">
        <div className="max-w-6xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
            className="text-center mb-12"
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('search.title')}
            </h2>
            <p
              className="text-lg max-w-2xl mx-auto mb-6"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('search.description')}
            </p>
            <p
              className="text-sm font-medium"
              style={{ color: 'var(--gold-soft)' }}
            >
              {t('search.powered')}
            </p>
          </motion.div>

          {/* Search Features Grid */}
          <div className="grid md:grid-cols-2 gap-4 mb-12 max-w-3xl mx-auto">
            {searchFeatures.map((feature, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, x: index % 2 === 0 ? -20 : 20 }}
                whileInView={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                viewport={{ once: true }}
                className="flex items-start gap-3 p-4 rounded-xl"
                style={isDark ? {
                  background: 'linear-gradient(135deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                  border: '1px solid rgba(230, 210, 140, 0.25)',
                  backdropFilter: 'blur(8px)',
                  boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.08), 0 4px 20px rgba(0, 0, 0, 0.4)',
                } : {
                  background: 'var(--feature-card-bg)',
                  border: '1px solid var(--feature-card-border)',
                  backdropFilter: 'blur(6px)',
                }}
              >
                <div
                  className="w-2 h-2 rounded-full mt-2 flex-shrink-0"
                  style={{ background: 'var(--gold)' }}
                />
                <p style={{ color: 'var(--text-muted)' }}>{feature}</p>
              </motion.div>
            ))}
          </div>

          {/* Search Taglines */}
          <div className="flex justify-center gap-8 mb-12 flex-wrap">
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.simple')}
            </span>
            <span style={{ color: 'var(--text-muted)' }}>|</span>
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.advanced')}
            </span>
          </div>

          <ImageComparison
            lightImage="art/DB-SEARCH-SIMPLE-LIGHT.png"
            darkImage="art/DB-SEARCH-SIMPLE-DARK.png"
            alt={isRTL ? 'חיפוש פשוט' : 'Simple Search'}
          />

          <div className="mt-16">
            <ImageComparison
              lightImage="art/DB-SEARCH-ADVANCED-LIGHT.png"
              darkImage="art/DB-SEARCH-ADVANCED-DARK.png"
              alt={isRTL ? 'חיפוש מתקדם' : 'Advanced Search'}
            />
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-20 px-6">
        <div className="max-w-6xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
            className="text-center mb-16"
          >
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('features.title')}
            </h2>
          </motion.div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((feature, index) => (
              <motion.div
                key={feature.key}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                viewport={{ once: true }}
                className="p-6 rounded-2xl transition-all hover:shadow-lg"
                style={isDark ? {
                  background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                  border: '1px solid rgba(230, 210, 140, 0.2)',
                  backdropFilter: 'blur(8px)',
                  boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
                } : {
                  background: 'var(--feature-card-bg)',
                  border: '1px solid var(--feature-card-border)',
                  backdropFilter: 'blur(6px)',
                }}
              >
                <div
                  className="w-12 h-12 rounded-xl flex items-center justify-center mb-4"
                  style={{
                    background: isDark
                      ? 'linear-gradient(135deg, rgba(230, 210, 140, 0.15) 0%, rgba(230, 210, 140, 0.05) 100%)'
                      : 'linear-gradient(135deg, rgba(139, 115, 85, 0.15) 0%, rgba(139, 115, 85, 0.05) 100%)',
                  }}
                >
                  <feature.icon size={24} style={{ color: 'var(--gold)' }} />
                </div>
                <p
                  className="text-base leading-relaxed"
                  style={{ color: 'var(--text-muted)' }}
                >
                  {t(`features.${feature.key}`)}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Promise Section */}
      <section className="py-20 px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <div className="max-w-5xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
            className="text-center mb-12"
          >
            <h2
              className="text-3xl md:text-4xl font-bold"
              style={{ color: 'var(--text-main)' }}
            >
              {t('promise.title')}
            </h2>
          </motion.div>

          <div className="grid md:grid-cols-2 gap-8">
            {/* Lightning Fast Card */}
            <motion.div
              initial={{ opacity: 0, x: -30 }}
              whileInView={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
              viewport={{ once: true }}
              className="p-8 rounded-2xl text-center"
              style={isDark ? {
                background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                border: '1px solid rgba(230, 210, 140, 0.2)',
                boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
              } : {
                background: 'var(--feature-card-bg)',
                border: '1px solid var(--feature-card-border)',
              }}
            >
              <div
                className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6"
                style={{
                  background: isDark
                    ? 'linear-gradient(135deg, rgba(230, 210, 140, 0.2) 0%, rgba(230, 210, 140, 0.05) 100%)'
                    : 'linear-gradient(135deg, rgba(139, 115, 85, 0.2) 0%, rgba(139, 115, 85, 0.05) 100%)',
                }}
              >
                <Zap size={32} style={{ color: 'var(--gold)' }} />
              </div>
              <h3
                className="text-2xl md:text-3xl font-bold mb-4"
                style={{ color: 'var(--gold)' }}
              >
                {t('promise.speed')}
              </h3>
              <p
                className="text-lg leading-relaxed"
                style={{ color: 'var(--text-muted)' }}
              >
                {t('promise.speedDesc')}
              </p>
            </motion.div>

            {/* Free Forever Card */}
            <motion.div
              initial={{ opacity: 0, x: 30 }}
              whileInView={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              viewport={{ once: true }}
              className="p-8 rounded-2xl text-center"
              style={isDark ? {
                background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                border: '1px solid rgba(230, 210, 140, 0.2)',
                boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
              } : {
                background: 'var(--feature-card-bg)',
                border: '1px solid var(--feature-card-border)',
              }}
            >
              <div
                className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6"
                style={{
                  background: isDark
                    ? 'linear-gradient(135deg, rgba(230, 210, 140, 0.2) 0%, rgba(230, 210, 140, 0.05) 100%)'
                    : 'linear-gradient(135deg, rgba(139, 115, 85, 0.2) 0%, rgba(139, 115, 85, 0.05) 100%)',
                }}
              >
                <InfinityIcon size={32} style={{ color: 'var(--gold)' }} />
              </div>
              <h3
                className="text-2xl md:text-3xl font-bold mb-4"
                style={{ color: 'var(--gold)' }}
              >
                {t('promise.free')}
              </h3>
              <p
                className="text-lg leading-relaxed"
                style={{ color: 'var(--text-muted)' }}
              >
                {t('promise.freeDesc')}
              </p>
            </motion.div>

            {/* Library Card */}
            <motion.div
              initial={{ opacity: 0, x: -30 }}
              whileInView={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
              viewport={{ once: true }}
              className="p-8 rounded-2xl text-center"
              style={isDark ? {
                background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                border: '1px solid rgba(230, 210, 140, 0.2)',
                boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
              } : {
                background: 'var(--feature-card-bg)',
                border: '1px solid var(--feature-card-border)',
              }}
            >
              <div
                className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6"
                style={{
                  background: isDark
                    ? 'linear-gradient(135deg, rgba(230, 210, 140, 0.2) 0%, rgba(230, 210, 140, 0.05) 100%)'
                    : 'linear-gradient(135deg, rgba(139, 115, 85, 0.2) 0%, rgba(139, 115, 85, 0.05) 100%)',
                }}
              >
                <Library size={32} style={{ color: 'var(--gold)' }} />
              </div>
              <h3
                className="text-2xl md:text-3xl font-bold mb-4"
                style={{ color: 'var(--gold)' }}
              >
                {t('promise.library')}
              </h3>
              <p
                className="text-lg leading-relaxed"
                style={{ color: 'var(--text-muted)' }}
              >
                {t('promise.libraryDesc')}
              </p>
            </motion.div>

            {/* Offline Card */}
            <motion.div
              initial={{ opacity: 0, x: 30 }}
              whileInView={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.4 }}
              viewport={{ once: true }}
              className="p-8 rounded-2xl text-center"
              style={isDark ? {
                background: 'linear-gradient(145deg, rgba(18, 15, 10, 0.95) 0%, rgba(8, 6, 4, 0.98) 100%)',
                border: '1px solid rgba(230, 210, 140, 0.2)',
                boxShadow: 'inset 0 1px 0 rgba(230, 210, 140, 0.06), 0 8px 32px rgba(0, 0, 0, 0.5)',
              } : {
                background: 'var(--feature-card-bg)',
                border: '1px solid var(--feature-card-border)',
              }}
            >
              <div
                className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6"
                style={{
                  background: isDark
                    ? 'linear-gradient(135deg, rgba(230, 210, 140, 0.2) 0%, rgba(230, 210, 140, 0.05) 100%)'
                    : 'linear-gradient(135deg, rgba(139, 115, 85, 0.2) 0%, rgba(139, 115, 85, 0.05) 100%)',
                }}
              >
                <WifiOff size={32} style={{ color: 'var(--gold)' }} />
              </div>
              <h3
                className="text-2xl md:text-3xl font-bold mb-4"
                style={{ color: 'var(--gold)' }}
              >
                {t('promise.offline')}
              </h3>
              <p
                className="text-lg leading-relaxed"
                style={{ color: 'var(--text-muted)' }}
              >
                {t('promise.offlineDesc')}
              </p>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Crafted Section */}
      <section className="py-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
          >
            <div
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
              style={{
                background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
                color: 'var(--gold)',
              }}
            >
              <Heart size={16} />
              {t('crafted.title')}
            </div>

            <p
              className="text-xl md:text-2xl leading-relaxed"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('crafted.description')}
            </p>
          </motion.div>
        </div>
      </section>

      {/* Download Section */}
      <section
        id="download"
        className="py-24 px-6"
        style={{
          background: isDark
            ? 'linear-gradient(180deg, rgba(230, 210, 140, 0.08) 0%, rgba(230, 210, 140, 0.02) 100%)'
            : 'linear-gradient(180deg, rgba(139, 115, 85, 0.08) 0%, rgba(139, 115, 85, 0.02) 100%)',
        }}
      >
        <div className="max-w-4xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
          >
            <h2
              className="text-4xl md:text-5xl font-bold mb-6"
              style={{ color: 'var(--text-main)' }}
            >
              {t('download.title')}
            </h2>
            <p
              className="text-xl mb-8"
              style={{ color: 'var(--text-muted)' }}
            >
              {t('download.description')}
            </p>

            {downloadCount !== null && (
              <p
                className="text-2xl font-bold mb-8"
                style={{ color: 'var(--gold)' }}
              >
                +{downloadCount.toLocaleString()} {t('download.downloads')}
              </p>
            )}

            <motion.a
              href="/Zayit/download"
              className="inline-flex items-center gap-3 px-10 py-5 rounded-full text-xl font-semibold text-white"
              style={{
                background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                boxShadow: isDark ? '0 15px 40px rgba(230, 210, 140, 0.3)' : '0 15px 40px rgba(139, 115, 85, 0.4)',
              }}
              whileHover={{ scale: 1.05, boxShadow: '0 20px 50px rgba(139, 115, 85, 0.5)' }}
              whileTap={{ scale: 0.98 }}
            >
              <Download size={26} />
              {t('download.cta')}
            </motion.a>

            <p
              className="mt-6 text-sm"
              style={{ color: 'var(--gold-muted)' }}
            >
              {t('download.platforms')}
            </p>
          </motion.div>
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
