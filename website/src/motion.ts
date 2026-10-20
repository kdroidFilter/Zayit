import type { Transition } from 'framer-motion';

/*
 * The site's motion, after Apple's fluid interfaces: critically damped springs (no overshoot, since nothing on
 * the page is thrown), short enough to never keep the reader waiting, and small offsets that hint where things
 * come from instead of flying across the screen.
 */

/** The default spring: damping 1.0, response ~0.5s. */
export const settle: Transition = { type: 'spring', bounce: 0, duration: 0.5 };

/** A content reveal as it scrolls into view, after [delay] seconds (keep it under ~0.3s). */
export const reveal = (delay = 0): Transition => ({ type: 'spring', bounce: 0, duration: 0.7, delay });

/** A press or hover: quick, so the feedback is felt on pointer-down. */
export const press: Transition = { type: 'spring', bounce: 0, duration: 0.2 };

/** How far a revealed block rises, in px: a hint of direction, not a journey. */
export const RISE = 24;
