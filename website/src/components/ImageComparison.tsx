import { useRef, type KeyboardEvent, type PointerEvent } from 'react';
import { animate, motion, useMotionValue, useMotionValueEvent, useTransform, type AnimationPlaybackControls } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { press, reveal, settle, RISE } from '../motion';

interface ImageComparisonProps {
  lightImage: string;
  darkImage: string;
  alt: string;
  /** Above the fold: fetched first instead of lazily. */
  priority?: boolean;
}

// The screenshots' size, reserved before they load so nothing shifts
const IMAGE_WIDTH = 1600;
const IMAGE_HEIGHT = 1000;

// The build writes a WebP copy of every screenshot (scripts/prerender.mjs); the dev server has only the originals
const webp = (src: string) => (import.meta.env.PROD ? src.replace(/\.(png|jpe?g)$/i, '.webp') : src);

const clamp = (value: number) => Math.min(Math.max(value, 0), 100);

/**
 * The light screenshot over the dark one, split by a divider the reader drags. The divider answers on pointer-down,
 * follows the pointer 1:1 from where it was grabbed, and can be caught mid-glide; it is drawn by the compositor
 * (clip-path and translation), with no React render per move.
 */
export function ImageComparison({ lightImage, darkImage, alt, priority = false }: ImageComparisonProps) {
  const { t } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  // Percent of the width showing the light image
  const position = useMotionValue(99);
  const glide = useRef<AnimationPlaybackControls | null>(null);
  // Offset between the pointer and the divider when grabbed, in percent: the divider never jumps under the pointer
  const grabOffset = useRef<number | null>(null);
  const dividerRef = useRef<HTMLDivElement>(null);
  // Screen readers hear the split's value without a render per move
  useMotionValueEvent(position, 'change', (p) => dividerRef.current?.setAttribute('aria-valuenow', String(Math.round(p))));

  const lightClip = useTransform(position, (p) => `inset(0 ${100 - p}% 0 0)`);
  const dividerX = useTransform(position, (p) => `${p - 100}%`);
  const lightLabelOpacity = useTransform(position, [8, 20], [0, 1]);
  const darkLabelOpacity = useTransform(position, [80, 92], [1, 0]);

  const percentAt = (clientX: number) => {
    const rect = containerRef.current!.getBoundingClientRect();
    return ((clientX - rect.left) / rect.width) * 100;
  };

  const glideTo = (target: number) => {
    glide.current?.stop();
    glide.current = animate(position, clamp(target), settle);
  };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    const pointer = percentAt(e.clientX);
    const onDivider = (e.target as HTMLElement).closest('[data-divider]') !== null;
    if (onDivider) {
      // Caught where it is, even mid-glide
      glide.current?.stop();
      grabOffset.current = pointer - position.get();
    } else {
      // A press elsewhere sends the divider there, then the drag carries on from it
      glideTo(pointer);
      grabOffset.current = 0;
    }
  };

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    if (grabOffset.current === null) return;
    glide.current?.stop();
    position.set(clamp(percentAt(e.clientX) - grabOffset.current));
  };

  const release = () => {
    grabOffset.current = null;
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const step = e.shiftKey ? 20 : 5;
    const targets: Record<string, number> = {
      ArrowLeft: position.get() - step,
      ArrowRight: position.get() + step,
      Home: 0,
      End: 100,
    };
    if (!(e.key in targets)) return;
    e.preventDefault();
    glideTo(targets[e.key]);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: RISE }}
      whileInView={{ opacity: 1, y: 0 }}
      transition={reveal()}
      viewport={{ once: true }}
      className="relative w-full max-w-5xl mx-auto"
    >
      {/* dir="ltr": the split is visual, it must not flip in Hebrew */}
      <div
        ref={containerRef}
        dir="ltr"
        className="relative overflow-hidden rounded-2xl select-none cursor-ew-resize"
        style={{
          touchAction: 'pan-y',
          boxShadow: '0 25px 60px -12px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(230, 210, 140, 0.12)',
        }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={release}
        onPointerCancel={release}
      >
        <img
          src={webp(darkImage)}
          alt={`${alt} - ${t('comparison.dark')}`}
          width={IMAGE_WIDTH}
          height={IMAGE_HEIGHT}
          className="w-full h-auto block"
          loading={priority ? 'eager' : 'lazy'}
          fetchPriority={priority ? 'high' : 'auto'}
          decoding="async"
          draggable={false}
        />
        <motion.img
          src={webp(lightImage)}
          alt={`${alt} - ${t('comparison.light')}`}
          width={IMAGE_WIDTH}
          height={IMAGE_HEIGHT}
          className="absolute inset-0 w-full h-full block"
          style={{ clipPath: lightClip }}
          loading={priority ? 'eager' : 'lazy'}
          fetchPriority={priority ? 'high' : 'auto'}
          decoding="async"
          draggable={false}
        />

        {/* Theme labels on a translucent material, fading as their side closes */}
        <motion.span className="comparison-label left-3" style={{ opacity: lightLabelOpacity }}>
          {t('comparison.light')}
        </motion.span>
        <motion.span className="comparison-label right-3" style={{ opacity: darkLabelOpacity }}>
          {t('comparison.dark')}
        </motion.span>

        {/* Divider: a full-width layer translated so that its right edge is the split */}
        <motion.div className="absolute inset-0 pointer-events-none" style={{ x: dividerX }}>
          <div
            className="absolute top-0 bottom-0 right-0 w-[3px] translate-x-1/2"
            style={{
              background: 'linear-gradient(180deg, var(--gold) 0%, var(--gold-soft) 50%, var(--gold) 100%)',
              boxShadow: '0 0 10px rgba(139, 115, 85, 0.5)',
            }}
          />
          <motion.div
            ref={dividerRef}
            data-divider
            role="slider"
            tabIndex={0}
            aria-label={t('comparison.drag')}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={99}
            onKeyDown={onKeyDown}
            className="absolute top-1/2 right-0 w-12 h-12 -mt-6 -mr-6 rounded-full flex items-center justify-center pointer-events-auto cursor-ew-resize focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4"
            style={{
              background: 'linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 100%)',
              boxShadow: '0 4px 20px rgba(0, 0, 0, 0.4)',
              outlineColor: 'var(--gold)',
            }}
            whileHover={{ scale: 1.08, transition: press }}
            whileTap={{ scale: 0.94, transition: press }}
          >
            <div className="flex items-center gap-1 text-white text-xs font-bold">
              <span>◀</span>
              <span>▶</span>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </motion.div>
  );
}
