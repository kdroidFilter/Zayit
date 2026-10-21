import { useRef, useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { animate, motion, useInView, useMotionValue, useReducedMotion, useScroll, useTransform } from 'framer-motion';
import {
  Search,
  BookOpen,
  Copy,
  Sun,
  Zap,
  Heart,
  Download,
  Sparkles,
  AppWindow,
  Star,
  Highlighter,
  CloudUpload,
  RefreshCw,
  Keyboard,
  LayoutGrid,
  Shield,
  BookMarked,
  Languages,
  Infinity as InfinityIcon,
  Library,
  WifiOff,
  PlayCircle,
} from 'lucide-react';
import { Navigation } from './components/Navigation';
import { SiteFooter } from './components/SiteFooter';
import { useDownloadCount } from './hooks/useDownloadCount';
import { ImageComparison } from './components/ImageComparison';
import { VideoEmbed } from './components/VideoEmbed';
import { CrystalParticlesGL } from './components/CrystalParticlesGL';
import { pagePath, type Lang } from './seo';
import { reveal, settle, press, RISE } from './motion';

interface Slide {
  titleKey: string;
  descKey: string;
  lightImage: string;
  darkImage: string;
}

// Modular panels of the reader
const panelsSlides: Slide[] = [
  { titleKey: 'panels.commentaries', descKey: 'panels.commentariesDesc', lightImage: '/art/PIRUSHIM-LIGHT.png', darkImage: '/art/PIRUSHIM-DARK.png' },
  { titleKey: 'panels.translations', descKey: 'panels.translationsDesc', lightImage: '/art/PIRUSHIM-TARGUMIM-LIGHT.png', darkImage: '/art/PIRUSHIM-TARGUMIM-DARK.png' },
  { titleKey: 'panels.talmud', descKey: 'panels.talmudDesc', lightImage: '/art/TALMUD-LIGHT.png', darkImage: '/art/TALMUD-DARK.png' },
  { titleKey: 'panels.sources', descKey: 'panels.sourcesDesc', lightImage: '/art/MEKOR-LIGHT.png', darkImage: '/art/MEKOR-DARK.png' },
  { titleKey: 'panels.details', descKey: 'panels.detailsDesc', lightImage: '/art/BOOK-DETAILS-LIGHT.png', darkImage: '/art/BOOK-DETAILS-DARK.png' },
];

// Study companions beyond the books
const companionsSlides: Slide[] = [
  { titleKey: 'companions.siddur', descKey: 'companions.siddurDesc', lightImage: '/art/SIDDUR-LIGHT.png', darkImage: '/art/SIDDUR-DARK.png' },
  { titleKey: 'companions.shnayim', descKey: 'companions.shnayimDesc', lightImage: '/art/SHNAYIM-MIKRA-LIGHT.png', darkImage: '/art/SHNAYIM-MIKRA-DARK.png' },
  { titleKey: 'companions.author', descKey: 'companions.authorDesc', lightImage: '/art/AUTHOR-LIGHT.png', darkImage: '/art/AUTHOR-DARK.png' },
];

// Floating particles of the hero, drawn once per page load (reduced for performance)
const heroParticles = [...Array(20)].map((_, i) => ({
  id: i,
  size: Math.random() * 4 + 2,
  x: Math.random() * 100,
  y: 60 + Math.random() * 40,
  opacity: Math.random() * 0.4 + 0.2,
  duration: Math.random() * 15 + 10,
  delay: Math.random() * 8,
  yMove: -150 - Math.random() * 200,
}));

const SLIDE_DURATION = 10000; // 10 seconds

// Apple-style showcase slider: chips to pick a slide, auto-advance with a progress dot
function ShowcaseSlider({
  id,
  titleKey,
  descriptionKey,
  slides,
  alternate = false,
  t,
  isRTL,
}: {
  id?: string;
  titleKey: string;
  descriptionKey: string;
  slides: Slide[];
  alternate?: boolean;
  t: (key: string) => string;
  isRTL: boolean;
}) {
  const [currentIndex, setCurrentIndex] = useState(0);
  // Once the reader picks a slide, the slider is theirs: no more autoplay
  const [autoplay, setAutoplay] = useState(true);
  const [hovered, setHovered] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);
  const inView = useInView(sectionRef, { amount: 0.3 });
  const reduceMotion = useReducedMotion();
  // Drawn by the compositor (scaleX), not by a React render per frame
  const progress = useMotionValue(0);

  // Auto-advance, paused while off screen or hovered, resumed where it stopped
  useEffect(() => {
    if (!autoplay || !inView || hovered || reduceMotion) return;
    const controls = animate(progress, 1, {
      duration: ((1 - progress.get()) * SLIDE_DURATION) / 1000,
      ease: 'linear',
      onComplete: () => {
        progress.set(0);
        setCurrentIndex((prev) => (prev + 1) % slides.length);
      },
    });
    return () => controls.stop();
  }, [autoplay, inView, hovered, reduceMotion, currentIndex, slides.length, progress]);

  const goToSlide = useCallback((index: number) => {
    setAutoplay(false);
    progress.set(1);
    setCurrentIndex(index);
  }, [progress]);

  return (
    <section
      ref={sectionRef}
      id={id}
      onPointerEnter={() => setHovered(true)}
      onPointerLeave={() => setHovered(false)}
      className="py-12 md:py-20 px-4 md:px-6 overflow-hidden"
      style={alternate ? { background: 'var(--section-alt-bg)' } : undefined}
    >
      <div className="max-w-6xl mx-auto">
        {/* Section Header */}
        <motion.div
          className="text-center mb-8 md:mb-12"
          initial={{ opacity: 0, y: RISE }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.5 }}
          transition={reveal()}
        >
          <h2
            className="text-3xl md:text-4xl font-bold mb-4"
            style={{ color: 'var(--text-main)' }}
          >
            {t(titleKey)}
          </h2>
          <motion.p
            className="text-lg max-w-2xl mx-auto"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={reveal(0.18)}
          >
            {t(descriptionKey)}
          </motion.p>
        </motion.div>

        {/* Chips: one per slide */}
        <div className="flex justify-center flex-wrap gap-2 mb-6 md:mb-8">
          {slides.map((slide, index) => {
            const isActive = index === currentIndex;
            return (
              <motion.button
                key={slide.titleKey}
                onClick={() => goToSlide(index)}
                aria-pressed={isActive}
                className="px-4 py-1.5 rounded-full text-sm md:text-base font-medium cursor-pointer transition-colors"
                style={{
                  background: isActive ? 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)' : 'rgba(230, 210, 140, 0.08)',
                  color: isActive ? '#1a1608' : 'var(--text-muted)',
                  border: `1px solid ${isActive ? 'transparent' : 'rgba(230, 210, 140, 0.2)'}`,
                }}
                whileHover={{ scale: 1.05, transition: press }}
                whileTap={{ scale: 0.97, transition: press }}
              >
                {t(slide.titleKey)}
              </motion.button>
            );
          })}
        </div>

        {/* All slides in a row, translated to the current one; in Hebrew the row runs right to left, as the chips */}
        <div className="overflow-hidden" dir="ltr">
          <motion.div
            className={isRTL ? 'flex flex-row-reverse' : 'flex'}
            animate={{ x: `${(isRTL ? 1 : -1) * currentIndex * 100}%` }}
            transition={settle}
          >
            {slides.map((slide, index) => (
              <div key={index} className="w-full flex-shrink-0 px-1" dir={isRTL ? 'rtl' : 'ltr'}>
                <p
                  className="text-center text-sm md:text-base mb-4 max-w-2xl mx-auto"
                  style={{ color: 'var(--text-muted)' }}
                >
                  {t(slide.descKey)}
                </p>
                <ImageComparison
                  lightImage={slide.lightImage}
                  darkImage={slide.darkImage}
                  alt={t(slide.titleKey)}
                />
              </div>
            ))}
          </motion.div>
        </div>

        {/* Apple-style Dot Indicators with Progress */}
        <div className="flex justify-center items-center gap-3 mt-8">
          {slides.map((_, index) => {
            const isActive = index === currentIndex;
            return (
              <button
                key={index}
                onClick={() => goToSlide(index)}
                className="group relative py-2 px-1 cursor-pointer rounded-full focus-visible:outline focus-visible:outline-2"
                style={{ outlineColor: 'var(--gold)' }}
                aria-label={t(slides[index].titleKey)}
              >
                {/* Track, widened for the current slide */}
                <motion.div
                  className="rounded-full relative overflow-hidden h-2"
                  style={{ backgroundColor: 'rgba(128, 128, 128, 0.35)' }}
                  animate={{ width: isActive ? 40 : 8 }}
                  whileHover={{ backgroundColor: 'rgba(128, 128, 128, 0.6)', transition: press }}
                  transition={settle}
                >
                  {/* Gold progress fill */}
                  {isActive && (
                    <motion.div
                      className="absolute inset-0 rounded-full"
                      style={{
                        backgroundColor: 'var(--gold)',
                        scaleX: progress,
                        transformOrigin: isRTL ? 'right' : 'left',
                      }}
                    />
                  )}
                </motion.div>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function App() {
  const { t, i18n } = useTranslation();
  const isRTL = i18n.language === 'he';
  // Reduced motion: no ambient particles (their loops would run for nothing)
  const reduceMotion = useReducedMotion();

  // Scroll-based animation for hero image
  const heroRef = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: heroRef,
    offset: ["start start", "end start"]
  });

  const imageScale = useTransform(scrollYProgress, [0, 1], [1.15, 1]);
  const imageY = useTransform(scrollYProgress, [0, 1], [0, 50]);

  const downloadCount = useDownloadCount();

  const features = [
    { icon: Search, key: 'find' },
    { icon: BookOpen, key: 'explore' },
    { icon: BookMarked, key: 'inbook' },
    { icon: Languages, key: 'compare' },
    { icon: AppWindow, key: 'tabs' },
    { icon: Star, key: 'bookmarks' },
    { icon: Highlighter, key: 'notes' },
    { icon: Copy, key: 'copy' },
    { icon: CloudUpload, key: 'backup' },
    { icon: RefreshCw, key: 'updates' },
    { icon: Keyboard, key: 'keyboard' },
    { icon: Sun, key: 'themes' },
  ];

  const widgets = ['zmanim', 'luach', 'limud', 'molad', 'temple', 'solar', 'shnayim', 'dictionary', 'measures', 'history'];

  const searchFeatures = [
    t('search.feature1'),
    t('search.feature2'),
    t('search.feature3'),
    t('search.feature4'),
    t('search.feature5'),
    t('search.feature6'),
  ];




  return (
    <div
      className="min-h-screen relative"
      style={{
        background: `radial-gradient(ellipse at top, var(--bg-gradient-top) 0%, var(--bg-main) 60%)`,
        color: 'var(--text-main)',
      }}
    >
      {/* Crystal Sparkle Particles Layer - WebGL with shaders for GPU acceleration */}
      {!reduceMotion && <CrystalParticlesGL count={50} />}

      <Navigation />

      {/* Hero Section - Cinematic Title + Image */}
      <section ref={heroRef} className="relative min-h-[60vh] md:min-h-screen w-full flex flex-col items-center justify-center overflow-hidden px-4 pt-16 md:pt-24 pb-2 md:pb-8">

        {/* Floating Particles (lightweight) */}
        <div className="ambient-motion absolute inset-0 overflow-hidden pointer-events-none">
          {heroParticles.map((p) => (
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
          transition={reveal()}
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
                staggerChildren: 0.08,
                delayChildren: 0.1,
              },
            },
          }}
        >
          {/* Brand and subtitle form one heading: what Zayit is, for readers and search engines alike */}
          <h1 className="flex flex-col items-center">
            <motion.span
              className="block text-6xl md:text-[12rem] font-bold mb-1 md:mb-2"
              style={{
                color: 'var(--gold)',
                textShadow: '0 0 60px rgba(230, 210, 140, 0.5), 0 0 120px rgba(230, 210, 140, 0.3)',
              }}
              variants={{
                hidden: { opacity: 0, scale: 1.04 },
                visible: {
                  opacity: 1,
                  scale: 1,
                  transition: settle,
                },
              }}
            >
              {t('hero.title')}
            </motion.span>{' '}

            {/* Decorative line */}
            <motion.span
              aria-hidden
              className="block h-[2px] w-[200px] mb-4 md:mb-8"
              style={{ background: `linear-gradient(90deg, transparent, var(--gold), transparent)` }}
              variants={{
                // scaleX rather than width: the compositor draws it, no layout per frame
                hidden: { scaleX: 0, opacity: 0 },
                visible: {
                  scaleX: 1,
                  opacity: 1,
                  transition: settle,
                },
              }}
            />

            {/* Subtitle */}
            <motion.span
              className="block text-lg md:text-3xl font-light mb-2 md:mb-4 max-w-3xl px-2"
              style={{ color: 'var(--text-main)' }}
              variants={{
                hidden: { opacity: 0, y: RISE },
                visible: {
                  opacity: 1,
                  y: 0,
                  transition: settle,
                },
              }}
            >
              {t('hero.subtitle')}
            </motion.span>
          </h1>

          {/* Tagline */}
          <motion.p
            className="text-base md:text-xl tracking-[0.2em] md:tracking-[0.3em] uppercase mb-6 md:mb-12"
            style={{ color: 'var(--gold-muted)' }}
            variants={{
              hidden: { opacity: 0, y: RISE / 2 },
              visible: {
                opacity: 1,
                y: 0,
                transition: settle,
              },
            }}
          >
            {t('hero.tagline')}
          </motion.p>
        </motion.div>

        {/* App Screenshot */}
        <motion.div
          className="relative z-20 w-full max-w-5xl mt-4 md:mt-8 px-4 md:px-0"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={reveal(0.45)}
          style={{
            scale: imageScale,
            y: imageY,
          }}
        >
          <ImageComparison
            lightImage="/art/HOME-LIGHT.jpg"
            darkImage="/art/HOME-DARK.jpg"
            alt={t('seo.imageAlt')}
            priority
          />
        </motion.div>
      </section>


      {/* Video Section - the presentation video in the page's language */}
      <section id="video" className="py-10 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial={{ opacity: 0, y: RISE }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.3 }}
          transition={reveal()}
        >
          <div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
          >
            <PlayCircle size={16} />
            {t('video.badge')}
          </div>
          <h2 className="text-3xl md:text-4xl font-bold mb-8 md:mb-10" style={{ color: 'var(--text-main)' }}>
            {t('video.title')}
          </h2>
          <VideoEmbed videoId={isRTL ? 'T7VU3K7EL5w' : 'e8ch0H9dlHs'} title={t('video.title')} />
        </motion.div>
      </section>


      {/* Vision Section */}
      <section className="py-10 md:py-20 px-4 md:px-6">
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial={{ opacity: 0, y: RISE }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={reveal()}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, scale: 0.96 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={reveal(0.06)}
          >
            <Sparkles size={16} />
            {t('vision.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={reveal(0.12)}
          >
            {t('vision.description')}
          </motion.p>
        </motion.div>
      </section>


      {/* Spirit Section - Slow dramatic scale reveal */}
      <section className="py-12 md:py-20 px-4 md:px-6" style={{ background: 'var(--section-alt-bg)' }}>
        <motion.div
          className="max-w-4xl mx-auto text-center"
          initial={{ opacity: 0, scale: 0.96 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={reveal()}
        >
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={reveal(0.12)}
          >
            <Shield size={16} />
            {t('spirit.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={reveal(0.22)}
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
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={reveal()}
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
              transition={reveal(0.15)}
            >
              {t('interface.description')}
            </motion.p>
            <motion.p
              className="text-base font-medium"
              style={{ color: 'var(--gold)' }}
              initial={{ opacity: 0, y: RISE }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={reveal(0.3)}
            >
              {t('interface.noLearning')}
            </motion.p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: RISE, scale: 0.96 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.2 }}
            transition={reveal()}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('interface.navTitle')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('interface.navDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="/art/TOC-LIGHT.png"
              darkImage="/art/TOC-DARK.png"
              alt={t('interface.navTitle')}
            />
          </motion.div>
        </div>
      </section>


      {/* Modular Panels Section - Apple-style horizontal slider */}
      <ShowcaseSlider
        titleKey="panels.title"
        descriptionKey="panels.description"
        slides={panelsSlides}
        alternate
        t={t}
        isRTL={isRTL}
      />


      {/* Search Section - Slow dramatic reveal with suspense */}
      <section id="search" className="py-12 md:py-20 px-4 md:px-6 overflow-hidden">
        <div className="max-w-6xl mx-auto">
          <motion.div
            className="text-center mb-12"
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={reveal()}
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
              transition={reveal(0.18)}
            >
              {t('search.description')}
            </motion.p>
            <motion.p
              className="text-sm font-medium"
              style={{ color: 'var(--gold-soft)' }}
              initial={{ opacity: 0, y: RISE }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={reveal(0.3)}
            >
              {t('search.powered')}
            </motion.p>
          </motion.div>

          {/* Search Features Grid - slow sequential reveal */}
          <div className="grid md:grid-cols-2 gap-4 mb-12 max-w-3xl mx-auto">
            {searchFeatures.map((feature, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, y: RISE, scale: 0.96 }}
                whileInView={{ opacity: 1, y: 0, scale: 1 }}
                viewport={{ once: true, amount: 0.5 }}
                transition={reveal(index * 0.08)}
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
                  transition={reveal(index * 0.08)}
                />
                <p style={{ color: 'var(--text-muted)' }}>{feature}</p>
              </motion.div>
            ))}
          </div>

          {/* Search Taglines */}
          <motion.div
            className="flex justify-center gap-8 mb-12 flex-wrap"
            initial={{ opacity: 0, scale: 0.96 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={reveal()}
          >
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.byWords')}
            </span>
            <span style={{ color: 'var(--text-muted)' }}>|</span>
            <span className="text-lg font-medium" style={{ color: 'var(--gold)' }}>
              {t('search.byMeaning')}
            </span>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: RISE, scale: 0.96 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.15 }}
            transition={reveal()}
            style={{ perspective: 1200 }}
          >
            <div className="text-center mb-4">
              <h3 className="text-lg md:text-xl font-medium mb-1" style={{ color: 'var(--gold)' }}>
                {t('search.resultsTitle')}
              </h3>
              <p className="text-sm md:text-base" style={{ color: 'var(--text-muted)' }}>
                {t('search.resultsDesc')}
              </p>
            </div>
            <ImageComparison
              lightImage="/art/SEARCH-LIGHT.png"
              darkImage="/art/SEARCH-DARK.png"
              alt={t('search.resultsTitle')}
            />
          </motion.div>
        </div>
      </section>


      {/* Home Widgets Section */}
      <section id="widgets" className="py-12 md:py-20 px-4 md:px-6 overflow-hidden" style={{ background: 'var(--section-alt-bg)' }}>
        <div className="max-w-6xl mx-auto">
          <motion.div
            className="text-center mb-10"
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={reveal()}
          >
            <motion.div
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
              style={{ background: 'rgba(230, 210, 140, 0.1)', color: 'var(--gold)' }}
            >
              <LayoutGrid size={16} />
              {t('widgets.badge')}
            </motion.div>
            <h2 className="text-3xl md:text-4xl font-bold mb-4" style={{ color: 'var(--text-main)' }}>
              {t('widgets.title')}
            </h2>
            <p className="text-lg max-w-2xl mx-auto" style={{ color: 'var(--text-muted)' }}>
              {t('widgets.description')}
            </p>
          </motion.div>

          <div className="flex justify-center flex-wrap gap-2 mb-10 max-w-4xl mx-auto">
            {widgets.map((key, index) => (
              <motion.span
                key={key}
                className="px-4 py-1.5 rounded-full text-sm md:text-base"
                style={{
                  background: 'rgba(230, 210, 140, 0.08)',
                  border: '1px solid rgba(230, 210, 140, 0.2)',
                  color: 'var(--text-muted)',
                }}
                initial={{ opacity: 0, y: RISE, scale: 0.96 }}
                whileInView={{ opacity: 1, y: 0, scale: 1 }}
                viewport={{ once: true }}
                transition={reveal(index * 0.06)}
              >
                {t(`widgets.${key}`)}
              </motion.span>
            ))}
          </div>

          <motion.div
            initial={{ opacity: 0, y: RISE, scale: 0.96 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.15 }}
            transition={reveal()}
          >
            <ImageComparison
              lightImage="/art/WIDGETS-LIGHT.jpg"
              darkImage="/art/WIDGETS-DARK.jpg"
              alt={t('widgets.title')}
            />
          </motion.div>
        </div>
      </section>


      {/* Study Companions - siddur, Shnayim Mikra, authors */}
      <ShowcaseSlider
        id="companions"
        titleKey="companions.title"
        descriptionKey="companions.description"
        slides={companionsSlides}
        t={t}
        isRTL={isRTL}
      />


      {/* Features Section - Slow dramatic card reveal */}
      <section id="features" className="py-12 md:py-20 px-4 md:px-6 overflow-hidden">
        <div className="max-w-6xl mx-auto">
          <motion.div
            className="text-center mb-12 md:mb-16"
            initial={{ opacity: 0, scale: 0.96 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={reveal()}
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
              // Fast sequential reveal
              const delay = (index % 3) * 0.06;

              return (
                <motion.div
                  key={feature.key}
                  initial={{ opacity: 0, scale: 0.96, y: RISE }}
                  whileInView={{ opacity: 1, scale: 1, y: 0 }}
                  viewport={{ once: true, amount: 0.2 }}
                  transition={reveal(delay)}
                  whileHover={{ y: -4, transition: press }}
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
                    initial={{ opacity: 0, scale: 0.8 }}
                    whileInView={{ opacity: 1, scale: 1 }}
                    viewport={{ once: true }}
                    transition={reveal(delay)}
                  >
                    <feature.icon size={24} style={{ color: 'var(--gold)' }} />
                  </motion.div>
                  <motion.p
                    className="text-base leading-relaxed"
                    style={{ color: 'var(--text-muted)' }}
                    initial={{ opacity: 0 }}
                    whileInView={{ opacity: 1 }}
                    viewport={{ once: true }}
                    transition={reveal(delay)}
                  >
                    {t(`features.${feature.key}`)}
                  </motion.p>
                </motion.div>
              );
            })}
          </div>
        </div>
      </section>


      {/* Promise Section - Fast 3D reveal */}
      <section className="py-12 md:py-20 px-4 md:px-6 overflow-hidden" style={{ background: 'var(--section-alt-bg)', perspective: '1200px' }}>
        <div className="max-w-5xl mx-auto">
          <motion.div
            className="text-center mb-12"
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={reveal()}
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
                initial={{ opacity: 0, scale: 0.96 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true, amount: 0.25 }}
                transition={reveal(index * 0.06)}
                whileHover={{ y: -4, transition: press }}
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
                  initial={{ opacity: 0, scale: 0.8 }}
                  whileInView={{ opacity: 1, scale: 1 }}
                  viewport={{ once: true }}
                  transition={reveal(index * 0.06)}
                >
                  <item.icon size={32} style={{ color: 'var(--gold)' }} />
                </motion.div>
                <motion.h3
                  className="text-2xl md:text-3xl font-bold mb-4"
                  style={{ color: 'var(--gold)' }}
                  initial={{ opacity: 0, y: RISE }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={reveal(index * 0.06)}
                >
                  {t(`promise.${item.title}`)}
                </motion.h3>
                <motion.p
                  className="text-base md:text-lg leading-relaxed"
                  style={{ color: 'var(--text-muted)' }}
                  initial={{ opacity: 0, y: 15 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={reveal(index * 0.06)}
                >
                  {t(`promise.${item.desc}`)}
                </motion.p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>


      {/* Crafted Section - Fast elegant text reveal */}
      <section className="py-12 md:py-20 px-4 md:px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-6"
            style={{
              background: 'rgba(230, 210, 140, 0.1)',
              color: 'var(--gold)',
            }}
            initial={{ opacity: 0, y: RISE, scale: 0.96 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={reveal()}
          >
            <motion.span
              initial={{ scale: 0 }}
              whileInView={{ rotate: [0, -20, 20, -15, 15, 0], scale: 1 }}
              viewport={{ once: true }}
              transition={reveal(0.03)}
            >
              <Heart size={16} />
            </motion.span>
            {t('crafted.title')}
          </motion.div>

          <motion.p
            className="text-xl md:text-2xl leading-relaxed"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={reveal(0.02)}
          >
            {t('crafted.description')}
          </motion.p>
        </div>
      </section>


      {/* Download Section - Fast finale */}
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
            initial={{ opacity: 0, scale: 1.04 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={reveal()}
          >
            {t('download.title')}
          </motion.h2>

          <motion.p
            className="text-xl mb-8"
            style={{ color: 'var(--text-muted)' }}
            initial={{ opacity: 0, y: RISE }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={reveal(0.04)}
          >
            {t('download.description')}
          </motion.p>

          {downloadCount !== null && (
            <motion.p
              className="text-2xl font-bold mb-8"
              style={{ color: 'var(--gold)' }}
              initial={{ opacity: 0, scale: 0.96 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={reveal(0.07)}
            >
              +{downloadCount.toLocaleString(i18n.language)} {t('download.downloads')}
            </motion.p>
          )}

          <motion.div
            initial={{ opacity: 0, y: RISE, scale: 0.96 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true }}
            transition={reveal(0.09)}
          >
            <Link to={pagePath(i18n.language as Lang, 'download')}>
              <motion.span
                className="inline-flex items-center gap-3 px-8 md:px-10 py-4 md:py-5 rounded-full text-lg md:text-xl font-semibold text-white"
                style={{
                  background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
                  boxShadow: '0 15px 40px rgba(230, 210, 140, 0.3)',
                }}
                whileHover={{ scale: 1.03, boxShadow: '0 20px 50px rgba(230, 210, 140, 0.45)', transition: press }}
                whileTap={{ scale: 0.97, transition: press }}
                transition={press}
              >
                <Download size={26} />
                {t('download.cta')}
              </motion.span>
            </Link>
          </motion.div>

          <motion.p
            className="mt-6 text-sm"
            style={{ color: 'var(--gold-muted)' }}
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={reveal(0.12)}
          >
            {t('download.platforms')}
          </motion.p>
        </div>
      </section>

      <SiteFooter page="home" />
    </div>
  );
}

export default App;
