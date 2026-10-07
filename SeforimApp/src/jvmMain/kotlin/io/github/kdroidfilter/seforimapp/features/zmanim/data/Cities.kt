package io.github.kdroidfilter.seforimapp.features.zmanim.data

import java.util.TimeZone

// Simple value holder for each city's coordinates and IANA time zone
data class Place(
    val lat: Double,
    val lng: Double,
    val elevation: Double,
    val zoneId: String,
) {
    /** The city's civil time zone, daylight saving time included. */
    val timeZone: TimeZone get() = TimeZone.getTimeZone(zoneId)
}

/** Country key used to identify Israel (vs. Diaspora / Chutz LaAretz). */
const val ISRAEL_COUNTRY_NAME = "ישראל"

/** Countries → Cities → Place */
val worldPlaces: Map<String, Map<String, Place>> =
    mapOf(
        "ישראל" to
            mapOf(
                "אופקים" to Place(31.3111, 34.6214, 140.0, "Asia/Jerusalem"),
                "אילת" to Place(29.5581, 34.9482, 12.0, "Asia/Jerusalem"),
                "אריאל" to Place(32.1069, 35.1897, 650.0, "Asia/Jerusalem"),
                "אשדוד" to Place(31.8044, 34.6553, 50.0, "Asia/Jerusalem"),
                "אשקלון" to Place(31.6688, 34.5742, 50.0, "Asia/Jerusalem"),
                "באר שבע" to Place(31.2518, 34.7915, 280.0, "Asia/Jerusalem"),
                "ביתר עילית" to Place(31.7025, 35.1156, 740.0, "Asia/Jerusalem"),
                "בית שמש" to Place(31.7245, 34.9886, 220.0, "Asia/Jerusalem"),
                "בני ברק" to Place(32.0809, 34.8338, 50.0, "Asia/Jerusalem"),
                "בת ים" to Place(32.0167, 34.7500, 5.0, "Asia/Jerusalem"),
                "גבעת זאב" to Place(31.8467, 35.1667, 600.0, "Asia/Jerusalem"),
                "גבעתיים" to Place(32.0706, 34.8103, 80.0, "Asia/Jerusalem"),
                "דימונה" to Place(31.0686, 35.0333, 550.0, "Asia/Jerusalem"),
                "הוד השרון" to Place(32.1506, 34.8889, 40.0, "Asia/Jerusalem"),
                "הרצליה" to Place(32.1624, 34.8443, 40.0, "Asia/Jerusalem"),
                "חיפה" to Place(32.7940, 34.9896, 30.0, "Asia/Jerusalem"),
                "חולון" to Place(32.0117, 34.7689, 54.0, "Asia/Jerusalem"),
                "טבריה" to Place(32.7940, 35.5308, -200.0, "Asia/Jerusalem"),
                "יבנה" to Place(31.8781, 34.7378, 25.0, "Asia/Jerusalem"),
                "ירושלים" to Place(31.7683, 35.2137, 800.0, "Asia/Jerusalem"),
                "כפר סבא" to Place(32.1742, 34.9067, 75.0, "Asia/Jerusalem"),
                "כרמיאל" to Place(32.9186, 35.2958, 300.0, "Asia/Jerusalem"),
                "לוד" to Place(31.9516, 34.8958, 50.0, "Asia/Jerusalem"),
                "מודיעין עילית" to Place(31.9254, 35.0364, 400.0, "Asia/Jerusalem"),
                "מצפה רמון" to Place(30.6097, 34.8017, 860.0, "Asia/Jerusalem"),
                "מעלה אדומים" to Place(31.7767, 35.2973, 740.0, "Asia/Jerusalem"),
                "נתיבות" to Place(31.4214, 34.5911, 140.0, "Asia/Jerusalem"),
                "נתניה" to Place(32.3215, 34.8532, 30.0, "Asia/Jerusalem"),
                "נצרת עילית" to Place(32.6992, 35.3289, 400.0, "Asia/Jerusalem"),
                "עפולה" to Place(32.6078, 35.2897, 60.0, "Asia/Jerusalem"),
                "ערד" to Place(31.2592, 35.2124, 570.0, "Asia/Jerusalem"),
                "פתח תקווה" to Place(32.0870, 34.8873, 80.0, "Asia/Jerusalem"),
                "צפת" to Place(32.9650, 35.4951, 900.0, "Asia/Jerusalem"),
                "קרית אונו" to Place(32.0539, 34.8581, 75.0, "Asia/Jerusalem"),
                "קרית ארבע" to Place(31.5244, 35.1031, 930.0, "Asia/Jerusalem"),
                "קרית גת" to Place(31.6100, 34.7642, 68.0, "Asia/Jerusalem"),
                "קרית מלאכי" to Place(31.7289, 34.7456, 108.0, "Asia/Jerusalem"),
                "קרית שמונה" to Place(33.2072, 35.5692, 135.0, "Asia/Jerusalem"),
                "ראשון לציון" to Place(31.9642, 34.8047, 68.0, "Asia/Jerusalem"),
                "רחובות" to Place(31.8947, 34.8096, 89.0, "Asia/Jerusalem"),
                "רמלה" to Place(31.9297, 34.8667, 108.0, "Asia/Jerusalem"),
                "רמת גן" to Place(32.0719, 34.8244, 80.0, "Asia/Jerusalem"),
                "רעננה" to Place(32.1847, 34.8706, 45.0, "Asia/Jerusalem"),
                "תל אביב" to Place(32.0853, 34.7818, 5.0, "Asia/Jerusalem"),
                "תפרח" to Place(31.3889, 34.6861, 160.0, "Asia/Jerusalem"),
            ),
        "ארצות הברית" to
            mapOf(
                "אטלנטה" to Place(33.7490, -84.3880, 320.0, "America/New_York"),
                "בוסטון" to Place(42.3601, -71.0589, 43.0, "America/New_York"),
                "בלטימור" to Place(39.2904, -76.6122, 10.0, "America/New_York"),
                "דטרויט" to Place(42.3314, -83.0458, 183.0, "America/Detroit"),
                "דנבר" to Place(39.7392, -104.9903, 1609.0, "America/Denver"),
                "יוסטון" to Place(29.7604, -95.3698, 12.0, "America/Chicago"),
                "לאס וגאס" to Place(36.1699, -115.1398, 610.0, "America/Los_Angeles"),
                "לוס אנג'לס" to Place(34.0522, -118.2437, 71.0, "America/Los_Angeles"),
                "לייקווד" to Place(40.0941, -74.2185, 21.0, "America/New_York"),
                "מיאמי" to Place(25.7617, -80.1918, 2.0, "America/New_York"),
                "ניו יורק" to Place(40.7128, -74.0060, 10.0, "America/New_York"),
                "סיאטל" to Place(47.6062, -122.3321, 56.0, "America/Los_Angeles"),
                "סן פרנסיסקו" to Place(37.7749, -122.4194, 16.0, "America/Los_Angeles"),
                "פילדלפיה" to Place(39.9526, -75.1652, 12.0, "America/New_York"),
                "פיניקס" to Place(33.4484, -112.0740, 331.0, "America/Phoenix"),
                "קליבלנד" to Place(41.4993, -81.6944, 199.0, "America/New_York"),
                "שיקגו" to Place(41.8781, -87.6298, 181.0, "America/Chicago"),
            ),
        "קנדה" to
            mapOf(
                "אדמונטון" to Place(53.5461, -113.4938, 645.0, "America/Edmonton"),
                "אוטווה" to Place(45.4215, -75.6972, 70.0, "America/Toronto"),
                "ונקובר" to Place(49.2827, -123.1207, 70.0, "America/Vancouver"),
                "טורונטו" to Place(43.6532, -79.3832, 76.0, "America/Toronto"),
                "מונטריאול" to Place(45.5017, -73.5673, 36.0, "America/Toronto"),
                "קלגרי" to Place(51.0447, -114.0719, 1048.0, "America/Edmonton"),
            ),
        "בריטניה" to
            mapOf(
                "אדינבורו" to Place(55.9533, -3.1883, 47.0, "Europe/London"),
                "לונדון" to Place(51.5074, -0.1278, 35.0, "Europe/London"),
            ),
        "צרפת" to
            mapOf(
                "אקס-לה-בן" to Place(45.6886, 5.9146, 235.0, "Europe/Paris"),
                "ארמנטייר" to Place(44.0527, 0.8144, 150.0, "Europe/Paris"),
                "ליון" to Place(45.7578, 4.8320, 173.0, "Europe/Paris"),
                "מרסיי" to Place(43.2962, 5.3700, 12.0, "Europe/Paris"),
                "ניס" to Place(43.7009, 7.2684, 10.0, "Europe/Paris"),
                "סטרסבורג" to Place(48.5846, 7.7507, 142.0, "Europe/Paris"),
                "סרסל" to Place(48.9961, 2.3796, 50.0, "Europe/Paris"),
                "פנטן" to Place(48.8965, 2.4020, 45.0, "Europe/Paris"),
                "פריז" to Place(48.8566, 2.3522, 35.0, "Europe/Paris"),
            ),
        "גרמניה" to
            mapOf(
                "ברלין" to Place(52.5200, 13.4050, 34.0, "Europe/Berlin"),
            ),
        "איטליה" to
            mapOf(
                "מילאנו" to Place(45.4642, 9.1900, 122.0, "Europe/Rome"),
                "רומא" to Place(41.9028, 12.4964, 21.0, "Europe/Rome"),
            ),
        "ספרד" to
            mapOf(
                "מדריד" to Place(40.4168, -3.7038, 650.0, "Europe/Madrid"),
            ),
        "הולנד" to
            mapOf(
                "אמסטרדם" to Place(52.3676, 4.9041, -2.0, "Europe/Amsterdam"),
            ),
        "שוויץ" to
            mapOf(
                "ג'נבה" to Place(46.2018, 6.1466, 375.0, "Europe/Zurich"),
                "ציריך" to Place(47.3769, 8.5417, 408.0, "Europe/Zurich"),
            ),
        "אוסטריה" to
            mapOf(
                "וינה" to Place(48.2082, 16.3738, 171.0, "Europe/Vienna"),
            ),
        "הונגריה" to
            mapOf(
                "בודפשט" to Place(47.4979, 19.0402, 102.0, "Europe/Budapest"),
            ),
        "צ'כיה" to
            mapOf(
                "פראג" to Place(50.0755, 14.4378, 200.0, "Europe/Prague"),
            ),
        "פולין" to
            mapOf(
                "ורשה" to Place(52.2297, 21.0122, 100.0, "Europe/Warsaw"),
            ),
        "רוסיה" to
            mapOf(
                "מוסקבה" to Place(55.7558, 37.6176, 156.0, "Europe/Moscow"),
            ),
        "טורקיה" to
            mapOf(
                "איסטנבול" to Place(41.0082, 28.9784, 39.0, "Europe/Istanbul"),
            ),
        "פורטוגל" to
            mapOf(
                "ליסבון" to Place(38.7223, -9.1393, 2.0, "Europe/Lisbon"),
            ),
        "אירלנד" to
            mapOf(
                "דבלין" to Place(53.3498, -6.2603, 85.0, "Europe/Dublin"),
            ),
        "שוודיה" to
            mapOf(
                "סטוקהולם" to Place(59.3293, 18.0686, 28.0, "Europe/Stockholm"),
            ),
        "דנמרק" to
            mapOf(
                "קופנהגן" to Place(55.6761, 12.5683, 24.0, "Europe/Copenhagen"),
            ),
        "פינלנד" to
            mapOf(
                "הלסינקי" to Place(60.1699, 24.9384, 26.0, "Europe/Helsinki"),
            ),
        "נורווגיה" to
            mapOf(
                "אוסלו" to Place(59.9139, 10.7522, 23.0, "Europe/Oslo"),
            ),
        "איסלנד" to
            mapOf(
                "רייקיאוויק" to Place(64.1466, -21.9426, 61.0, "Atlantic/Reykjavik"),
            ),
        "ארגנטינה" to
            mapOf(
                "בואנוס איירס" to Place(-34.6118, -58.3960, 25.0, "America/Argentina/Buenos_Aires"),
            ),
        "ברזיל" to
            mapOf(
                "ריו דה ז'נרו" to Place(-22.9068, -43.1729, 2.0, "America/Sao_Paulo"),
                "סאו פאולו" to Place(-23.5505, -46.6333, 760.0, "America/Sao_Paulo"),
            ),
        "צ'ילה" to
            mapOf(
                "סנטיאגו" to Place(-33.4489, -70.6693, 520.0, "America/Santiago"),
            ),
        "ונצואלה" to
            mapOf(
                "קראקס" to Place(10.4806, -66.9036, 900.0, "America/Caracas"),
            ),
        "פרו" to
            mapOf(
                "לימה" to Place(-12.0464, -77.0428, 154.0, "America/Lima"),
            ),
        "מקסיקו" to
            mapOf(
                "מקסיקו סיטי" to Place(19.4326, -99.1332, 2240.0, "America/Mexico_City"),
            ),
        "מרוקו" to
            mapOf(
                "קזבלנקה" to Place(33.5731, -7.5898, 50.0, "Africa/Casablanca"),
            ),
        "דרום אפריקה" to
            mapOf(
                "יוהנסבורג" to Place(-26.2041, 28.0473, 1753.0, "Africa/Johannesburg"),
                "קייפטאון" to Place(-33.9249, 18.4241, 42.0, "Africa/Johannesburg"),
            ),
        "מצרים" to
            mapOf(
                "אלכסנדריה" to Place(31.2001, 29.9187, 12.0, "Africa/Cairo"),
                "קהיר" to Place(30.0444, 31.2357, 74.0, "Africa/Cairo"),
            ),
        "הודו" to
            mapOf(
                "דלהי" to Place(28.7041, 77.1025, 216.0, "Asia/Kolkata"),
                "מומבאי" to Place(19.0760, 72.8777, 14.0, "Asia/Kolkata"),
            ),
        "תאילנד" to
            mapOf(
                "בנגקוק" to Place(13.7563, 100.5018, 1.5, "Asia/Bangkok"),
            ),
        "סינגפור" to
            mapOf(
                "סינגפור" to Place(1.3521, 103.8198, 15.0, "Asia/Singapore"),
            ),
        "הונג קונג" to
            mapOf(
                "הונג קונג" to Place(22.3193, 114.1694, 552.0, "Asia/Hong_Kong"),
            ),
        "יפן" to
            mapOf(
                "טוקיו" to Place(35.6762, 139.6503, 40.0, "Asia/Tokyo"),
            ),
        "דרום קוריאה" to
            mapOf(
                "סיאול" to Place(37.5665, 126.9780, 38.0, "Asia/Seoul"),
            ),
        "סין" to
            mapOf(
                "בייג'ינג" to Place(39.9042, 116.4074, 43.5, "Asia/Shanghai"),
                "שנחאי" to Place(31.2304, 121.4737, 4.0, "Asia/Shanghai"),
            ),
        "איחוד האמירויות" to
            mapOf(
                "דובאי" to Place(25.2048, 55.2708, 16.0, "Asia/Dubai"),
            ),
        "כווית" to
            mapOf(
                "כווית" to Place(29.3759, 47.9774, 55.0, "Asia/Kuwait"),
            ),
        "אוסטרליה" to
            mapOf(
                "בריסביין" to Place(-27.4698, 153.0251, 27.0, "Australia/Brisbane"),
                "מלבורן" to Place(-37.8136, 144.9631, 31.0, "Australia/Melbourne"),
                "פרת" to Place(-31.9505, 115.8605, 46.0, "Australia/Perth"),
                "סידני" to Place(-33.8688, 151.2093, 58.0, "Australia/Sydney"),
            ),
    )
