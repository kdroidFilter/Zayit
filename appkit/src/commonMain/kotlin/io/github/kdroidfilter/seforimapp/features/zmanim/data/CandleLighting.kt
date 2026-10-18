package io.github.kdroidfilter.seforimapp.features.zmanim.data

/**
 * How early a city lights Shabbat candles in the עתים לבינה luach: [minutes] before sunset, counted from the
 * city's height ([fromHeight]) or from sea level.
 */
data class CandleLighting(
    val minutes: Int,
    val fromHeight: Boolean,
)

/** The luach's default for a place it does not print: 20 minutes before sea-level sunset in Israel. */
val ITIM_LABINA_ISRAEL_CANDLES = CandleLighting(20, fromHeight = false)

/** Abroad the luach's American default: 18 minutes before sea-level sunset. */
val ITIM_LABINA_ABROAD_CANDLES = CandleLighting(18, fromHeight = false)

/**
 * Candle lighting per city, as printed by עתים לבינה (itimlabina.co.il, zman group "הדלקת נרות"), keyed by the
 * [worldPlaces] city names. Cities the luach does not print fall back to the defaults above.
 */
val itimLabinaCandleLighting: Map<String, CandleLighting> =
    mapOf(
        // Israel
        "אופקים" to CandleLighting(30, fromHeight = true),
        "אריאל" to CandleLighting(30, fromHeight = true),
        "אשדוד" to CandleLighting(22, fromHeight = true),
        "אשקלון" to CandleLighting(20, fromHeight = true),
        "באר שבע" to CandleLighting(20, fromHeight = true),
        "ביתר עילית" to CandleLighting(40, fromHeight = true),
        "בית שמש" to CandleLighting(40, fromHeight = true),
        "בני ברק" to CandleLighting(22, fromHeight = false),
        "גבעת זאב" to CandleLighting(40, fromHeight = true),
        "דימונה" to CandleLighting(22, fromHeight = true),
        "הרצליה" to CandleLighting(22, fromHeight = false),
        "חיפה" to CandleLighting(30, fromHeight = true),
        "טבריה" to CandleLighting(30, fromHeight = false),
        "ירושלים" to CandleLighting(40, fromHeight = true),
        "כרמיאל" to CandleLighting(30, fromHeight = true),
        "לוד" to CandleLighting(30, fromHeight = false),
        "מודיעין עילית" to CandleLighting(30, fromHeight = true),
        "מצפה רמון" to CandleLighting(20, fromHeight = true),
        "מעלה אדומים" to CandleLighting(40, fromHeight = false),
        "נתיבות" to CandleLighting(30, fromHeight = true),
        "נתניה" to CandleLighting(22, fromHeight = false),
        "עפולה" to CandleLighting(30, fromHeight = false),
        "ערד" to CandleLighting(22, fromHeight = true),
        "פתח תקווה" to CandleLighting(40, fromHeight = false),
        "צפת" to CandleLighting(30, fromHeight = true),
        "קרית ארבע" to CandleLighting(40, fromHeight = true),
        "קרית גת" to CandleLighting(22, fromHeight = false),
        "קרית מלאכי" to CandleLighting(22, fromHeight = true),
        "קרית שמונה" to CandleLighting(30, fromHeight = false),
        "ראשון לציון" to CandleLighting(22, fromHeight = false),
        "רחובות" to CandleLighting(22, fromHeight = false),
        "רעננה" to CandleLighting(22, fromHeight = false),
        "תל אביב" to CandleLighting(22, fromHeight = false),
        "תפרח" to CandleLighting(20, fromHeight = true),
        // Abroad
        "אמסטרדם" to CandleLighting(30, fromHeight = false),
        "יוהנסבורג" to CandleLighting(18, fromHeight = false),
        "לונדון" to CandleLighting(15, fromHeight = false),
        "לייקווד" to CandleLighting(18, fromHeight = false),
        "מיאמי" to CandleLighting(18, fromHeight = false),
        "מקסיקו סיטי" to CandleLighting(18, fromHeight = false),
        "ניו יורק" to CandleLighting(18, fromHeight = false),
    )
