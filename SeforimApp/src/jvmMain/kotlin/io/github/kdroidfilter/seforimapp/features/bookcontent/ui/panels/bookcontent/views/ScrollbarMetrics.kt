package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

/**
 * Realistic Hebrew prose used by `TextMeasurer` to compute chars-per-visual-line.
 *
 * A continuous `"א"×N` reference would pack char-by-char with no word-boundary waste,
 * which over-estimates capacity versus real content: Compose word-wraps real text at
 * spaces, leaving a few unused pixels at the end of each line. Using text with natural
 * Hebrew word lengths and spaces yields a capacity that matches what Compose will
 * actually wrap in the rendered items.
 *
 * Shared between [BookContentView]'s main scrollbar metrics and [LineCommentsView]'s
 * commentary scrollbar metrics — both pieces measure prose-rendered Hebrew text.
 */
internal val CAPACITY_REFERENCE =
    ("ועל כן ראוי לנו לומר בדבר הזה ולהבין על מה כוונת המחבר בהזכירו דברים אלו ").repeat(200)

/**
 * Re-latch the thumb size when the viewport drifts by at least this fraction from
 * the previously latched value. Catches resize/SplitPane changes while ignoring
 * sub-pixel jitter from layout settle. Shared between the book and commentary
 * scrollbars.
 */
internal const val RELATCH_VIEWPORT_THRESHOLD = 0.10f
