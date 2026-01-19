import { useRef, useState, useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useTheme } from '../contexts/ThemeContext';

interface ImageComparisonProps {
  lightImage: string;
  darkImage: string;
  alt: string;
  fullscreen?: boolean;
}

export function ImageComparison({ lightImage, darkImage, alt, fullscreen = false }: ImageComparisonProps) {
  const { i18n } = useTranslation();
  const { isDark } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const [sliderPosition, setSliderPosition] = useState(isDark ? 1 : 99);
  const [isDragging, setIsDragging] = useState(false);
  const [hasUserMoved, setHasUserMoved] = useState(false);
  const isRTL = i18n.language === 'he';

  // Update slider position when theme changes (only if user hasn't moved it)
  useEffect(() => {
    if (!hasUserMoved) {
      setSliderPosition(isDark ? 1 : 99);
    }
  }, [isDark, hasUserMoved]);

  // Reset hasUserMoved when theme changes to allow automatic repositioning
  useEffect(() => {
    setHasUserMoved(false);
  }, [isDark]);

  const handleMove = useCallback(
    (clientX: number) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      let percentage: number;
      if (isRTL) {
        const x = rect.right - clientX;
        percentage = Math.min(Math.max((x / rect.width) * 100, 0), 100);
      } else {
        const x = clientX - rect.left;
        percentage = Math.min(Math.max((x / rect.width) * 100, 0), 100);
      }
      setSliderPosition(percentage);
      setHasUserMoved(true);
    },
    [isRTL]
  );

  const handleMouseDown = () => setIsDragging(true);
  const handleMouseUp = () => setIsDragging(false);

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isDragging) return;
      handleMove(e.clientX);
    },
    [isDragging, handleMove]
  );

  const handleTouchMove = useCallback(
    (e: TouchEvent) => {
      if (!isDragging) return;
      handleMove(e.touches[0].clientX);
    },
    [isDragging, handleMove]
  );

  useEffect(() => {
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
    document.addEventListener('touchmove', handleTouchMove);
    document.addEventListener('touchend', handleMouseUp);

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.removeEventListener('touchmove', handleTouchMove);
      document.removeEventListener('touchend', handleMouseUp);
    };
  }, [handleMouseMove, handleTouchMove]);

  return (
    <motion.div
      initial={{ opacity: 0, y: fullscreen ? 0 : 40 }}
      whileInView={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, ease: 'easeOut' }}
      viewport={{ once: true }}
      className={fullscreen ? "relative w-full h-full" : "relative w-full max-w-5xl mx-auto"}
    >

      {/* Comparison Container */}
      <div
        ref={containerRef}
        className={`relative overflow-hidden cursor-ew-resize select-none ${fullscreen ? 'w-full h-full flex items-center justify-center' : 'rounded-2xl shadow-2xl'}`}
        style={fullscreen ? {} : {
          boxShadow: '0 25px 60px -12px rgba(0, 0, 0, 0.35)',
        }}
        onMouseDown={handleMouseDown}
        onTouchStart={handleMouseDown}
      >
        {fullscreen ? (
          <>
            {/* Fullscreen mode - image takes max space without crop */}
            <img
              src={darkImage}
              alt={`${alt} - Dark mode`}
              className="max-w-full max-h-full w-auto h-auto"
              style={{ objectFit: 'contain' }}
              draggable={false}
            />
            {/* Light Image Overlay */}
            <div
              className="absolute top-0 left-0 bottom-0 overflow-hidden"
              style={{
                width: `${sliderPosition}%`,
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <img
                src={lightImage}
                alt={`${alt} - Light mode`}
                className="max-h-full w-auto h-auto"
                style={{
                  objectFit: 'contain',
                  objectPosition: 'left center',
                  width: `${100 / (sliderPosition / 100)}%`,
                  maxWidth: 'none',
                }}
                draggable={false}
              />
            </div>
          </>
        ) : (
          <>
            {/* Normal mode */}
            <img
              src={darkImage}
              alt={`${alt} - Dark mode`}
              className="w-full h-auto block"
              draggable={false}
            />
            <div
              className="absolute top-0 h-full overflow-hidden"
              style={isRTL ? {
                right: 0,
                width: `${sliderPosition}%`
              } : {
                left: 0,
                width: `${sliderPosition}%`
              }}
            >
              <img
                src={lightImage}
                alt={`${alt} - Light mode`}
                className="h-full object-cover"
                style={isRTL ? {
                  position: 'absolute',
                  right: 0,
                  width: `${100 / (sliderPosition / 100)}%`,
                  maxWidth: 'none',
                  objectPosition: 'right'
                } : {
                  width: `${100 / (sliderPosition / 100)}%`,
                  maxWidth: 'none',
                  objectPosition: 'left'
                }}
                draggable={false}
              />
            </div>
          </>
        )}

        {/* Slider */}
        <div
          className="absolute top-0 bottom-0 z-10 flex items-center justify-center"
          style={isRTL ? {
            right: `${sliderPosition}%`,
            transform: 'translateX(50%)'
          } : {
            left: `${sliderPosition}%`,
            transform: 'translateX(-50%)'
          }}
        >
          {/* Vertical Line */}
          <div
            className="absolute top-0 bottom-0 w-1"
            style={{
              background: 'linear-gradient(180deg, var(--gold) 0%, var(--gold-soft) 50%, var(--gold) 100%)',
              boxShadow: '0 0 10px rgba(139, 115, 85, 0.5)',
            }}
          />

          {/* Handle */}
          <motion.div
            className={`relative z-20 rounded-full flex items-center justify-center cursor-ew-resize ${fullscreen ? 'w-16 h-16' : 'w-12 h-12'}`}
            style={{
              background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
              boxShadow: '0 4px 20px rgba(0, 0, 0, 0.4)',
            }}
            whileHover={{ scale: 1.1 }}
            whileTap={{ scale: 0.95 }}
          >
            <div className={`flex items-center gap-1 text-white font-bold ${fullscreen ? 'text-sm' : 'text-xs'}`}>
              <span>{isRTL ? '▶' : '◀'}</span>
              <span>{isRTL ? '◀' : '▶'}</span>
            </div>
          </motion.div>
        </div>

        {/* Gradient overlay on edges */}
        <div
          className="absolute inset-0 pointer-events-none"
          style={{
            background: 'linear-gradient(90deg, rgba(0,0,0,0.03) 0%, transparent 5%, transparent 95%, rgba(0,0,0,0.03) 100%)',
          }}
        />
      </div>

    </motion.div>
  );
}
