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

  // Cinematic animation variants
  const cinematicEase = [0.16, 1, 0.3, 1] as const;

  const sectionVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.15,
        delayChildren: 0.1,
      },
    },
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 30, filter: 'blur(10px)' },
    visible: {
      opacity: 1,
      y: 0,
      filter: 'blur(0px)',
      transition: { duration: 0.8, ease: cinematicEase },
    },
  };

  const cardVariants = {
    hidden: { opacity: 0, y: 40, scale: 0.95 },
    visible: {
      opacity: 1,
      y: 0,
      scale: 1,
      transition: { duration: 0.6, ease: cinematicEase },
    },
  };

  const imageVariants = {
    hidden: { opacity: 0, y: 60, scale: 0.9 },
    visible: {
      opacity: 1,
      y: 0,
      scale: 1,
      transition: { duration: 1, ease: cinematicEase },
    },
  };

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
      className="min-h-screen"
      style={{
        background: `radial-gradient(ellipse at top, var(--bg-gradient-top) 0%, var(--bg-main) 60%)`,
        color: 'var(--text-main)',
      }}
    >
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
                background: isDark
                  ? `rgba(230, 210, 140, ${p.opacity})`
                  : `rgba(180, 140, 60, ${p.opacity + 0.2})`,
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
          className="relative z-20 w-full max-w-6xl mt-4 md:mt-8 px-4 md:px-0"
          initial={{ opacity: 0, y: 60 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 1.2, delay: 2.8, ease: [0.16, 1, 0.3, 1] }}
          style={{
            scale: imageScale,
            y: imageY,
          }}
        >
          <div className="[&_img]:max-h-[35vh] [&_img]:md:max-h-[60vh] [&_img]:w-auto [&_img]:mx-auto">
            <ImageComparison
              lightImage="art/HOME-LIGHT.png"
              darkImage="art/HOME-DARK.png"
              alt=""
            />
          </div>
        </motion.div>
      </section>

      {/* Vision Section - delayed to appear after hero */}
      <section className="py-10 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 3.5, ease: cinematicEase }}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 3.7, ease: cinematicEase }}
          >
            <Sparkles size={16} />
            {t('vision.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 3.9, ease: cinematicEase }}
          >
            {t('vision.description')}
          </motion.p>
        </motion.div>
      </section>

      {/* Spirit Section */}
      <section className="py-12 md:py-20 px-4 md:px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
              color: 'var(--gold)',
            }}
            variants={itemVariants}
          >
            <Shield size={16} />
            {t('spirit.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            variants={itemVariants}
          >
            {t('spirit.description')}
          </motion.p>
        </motion.div>
      </section>

      {/* Interface Section */}
      <section className="py-12 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-6xl mx-auto"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div className="text-center mb-12" variants={itemVariants}>
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

          <motion.div variants={imageVariants}>
            <ImageComparison
              lightImage="art/BOOK-SEARCH-LIGHT.png"
              darkImage="art/BOOK-SEARCH-DARK.png"
              alt={isRTL ? 'חיפוש ספרים' : 'Book Search'}
            />
          </motion.div>
        </motion.div>
      </section>

      {/* Modular Panels Section */}
      <section className="py-12 md:py-20 px-4 md:px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <motion.div
          className="max-w-6xl mx-auto space-y-12 md:space-y-16"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div className="text-center mb-12" variants={itemVariants}>
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

          <motion.div variants={imageVariants}>
            <ImageComparison
              lightImage="art/PIRUSHIM-LIGHT.png"
              darkImage="art/PIRUSHIM-DARK.png"
              alt={isRTL ? 'פירושים' : 'Commentaries'}
            />
          </motion.div>

          <motion.div variants={imageVariants}>
            <ImageComparison
              lightImage="art/PIRUSHIM-TARGUMIM-LIGHT.png"
              darkImage="art/PIRUSHIM-TARGUMIM-DARK.png"
              alt={isRTL ? 'פירושים ותרגומים' : 'Commentaries and Translations'}
            />
          </motion.div>

          <motion.div variants={imageVariants}>
            <ImageComparison
              lightImage="art/MEKOR-LIGHT.png"
              darkImage="art/MEKOR-DARK.png"
              alt={isRTL ? 'מקורות' : 'Sources'}
            />
          </motion.div>
        </motion.div>
      </section>

      {/* Search Section */}
      <section id="search" className="py-12 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-6xl mx-auto"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div className="text-center mb-12" variants={itemVariants}>
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
          <motion.div
            className="grid md:grid-cols-2 gap-4 mb-12 max-w-3xl mx-auto"
            variants={sectionVariants}
          >
            {searchFeatures.map((feature, index) => (
              <motion.div
                key={index}
                variants={cardVariants}
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
          </motion.div>

          {/* Search Taglines */}
          <motion.div className="flex justify-center gap-8 mb-12 flex-wrap" variants={itemVariants}>
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.simple')}
            </span>
            <span style={{ color: 'var(--text-muted)' }}>|</span>
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.advanced')}
            </span>
          </motion.div>

          <motion.div variants={imageVariants}>
            <ImageComparison
              lightImage="art/DB-SEARCH-SIMPLE-LIGHT.png"
              darkImage="art/DB-SEARCH-SIMPLE-DARK.png"
              alt={isRTL ? 'חיפוש פשוט' : 'Simple Search'}
            />
          </motion.div>

          <motion.div className="mt-12 md:mt-16" variants={imageVariants}>
            <ImageComparison
              lightImage="art/DB-SEARCH-ADVANCED-LIGHT.png"
              darkImage="art/DB-SEARCH-ADVANCED-DARK.png"
              alt={isRTL ? 'חיפוש מתקדם' : 'Advanced Search'}
            />
          </motion.div>
        </motion.div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-12 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-6xl mx-auto"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div className="text-center mb-12 md:mb-16" variants={itemVariants}>
            <h2
              className="text-3xl md:text-4xl font-bold mb-4"
              style={{ color: 'var(--text-main)' }}
            >
              {t('features.title')}
            </h2>
          </motion.div>

          <motion.div
            className="grid md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6"
            variants={sectionVariants}
          >
            {features.map((feature) => (
              <motion.div
                key={feature.key}
                variants={cardVariants}
                whileHover={{ scale: 1.02, y: -5 }}
                className="p-6 rounded-2xl transition-colors"
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
          </motion.div>
        </motion.div>
      </section>

      {/* Promise Section */}
      <section className="py-12 md:py-20 px-4 md:px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <motion.div
          className="max-w-5xl mx-auto"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div className="text-center mb-12" variants={itemVariants}>
            <h2
              className="text-3xl md:text-4xl font-bold"
              style={{ color: 'var(--text-main)' }}
            >
              {t('promise.title')}
            </h2>
          </motion.div>

          <motion.div className="grid md:grid-cols-2 gap-6 md:gap-8" variants={sectionVariants}>
            {[
              { icon: Zap, title: 'speed', desc: 'speedDesc' },
              { icon: InfinityIcon, title: 'free', desc: 'freeDesc' },
              { icon: Library, title: 'library', desc: 'libraryDesc' },
              { icon: WifiOff, title: 'offline', desc: 'offlineDesc' },
            ].map((item) => (
              <motion.div
                key={item.title}
                variants={cardVariants}
                whileHover={{ scale: 1.02, y: -5 }}
                className="p-6 md:p-8 rounded-2xl text-center"
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
                  <item.icon size={32} style={{ color: 'var(--gold)' }} />
                </div>
                <h3
                  className="text-2xl md:text-3xl font-bold mb-4"
                  style={{ color: 'var(--gold)' }}
                >
                  {t(`promise.${item.title}`)}
                </h3>
                <p
                  className="text-base md:text-lg leading-relaxed"
                  style={{ color: 'var(--text-muted)' }}
                >
                  {t(`promise.${item.desc}`)}
                </p>
              </motion.div>
            ))}
          </motion.div>
        </motion.div>
      </section>

      {/* Crafted Section */}
      <section className="py-12 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: isDark ? 'rgba(230, 210, 140, 0.1)' : 'rgba(139, 115, 85, 0.1)',
              color: 'var(--gold)',
            }}
            variants={itemVariants}
          >
            <Heart size={16} />
            {t('crafted.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            variants={itemVariants}
          >
            {t('crafted.description')}
          </motion.p>
        </motion.div>
      </section>

      {/* Download Section */}
      <section
        id="download"
        className="py-16 md:py-24 px-4 md:px-6"
        style={{
          background: isDark
            ? 'linear-gradient(180deg, rgba(230, 210, 140, 0.08) 0%, rgba(230, 210, 140, 0.02) 100%)'
            : 'linear-gradient(180deg, rgba(139, 115, 85, 0.08) 0%, rgba(139, 115, 85, 0.02) 100%)',
        }}
      >
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          variants={sectionVariants}
        >
          <motion.h2
            className="text-4xl md:text-5xl font-bold mb-6"
            style={{ color: 'var(--text-main)' }}
            variants={itemVariants}
          >
            {t('download.title')}
          </motion.h2>

          <motion.p
            className="text-xl mb-8"
            style={{ color: 'var(--text-muted)' }}
            variants={itemVariants}
          >
            {t('download.description')}
          </motion.p>

          {downloadCount !== null && (
            <motion.p
              className="text-2xl font-bold mb-8"
              style={{ color: 'var(--gold)' }}
              variants={itemVariants}
            >
              +{downloadCount.toLocaleString()} {t('download.downloads')}
            </motion.p>
          )}

          <motion.div variants={itemVariants}>
            <motion.a
              href="/Zayit/download"
              className="inline-flex items-center gap-3 px-8 md:px-10 py-4 md:py-5 rounded-full text-lg md:text-xl font-semibold text-white"
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
          </motion.div>

          <motion.p
            className="mt-6 text-sm"
            style={{ color: 'var(--gold-muted)' }}
            variants={itemVariants}
          >
            {t('download.platforms')}
          </motion.p>
        </motion.div>
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
