# The Microsoft Store listing

The app is **Zayit** (Store ID `9NC26MP2QMPS`, https://apps.microsoft.com/detail/9NC26MP2QMPS). Its names are reserved
in Partner Center, and each listing's product name must be one of them as it is: `זית`, `Zayit` (to submit within
three months of reserving them, by 28 January 2027).

The package is built locally (`./gradlew :SeforimApp:packageGraalvmAppX`, in
`SeforimApp/build/compose/binaries/main/graalvm-appx/`), versioned from the latest git tag.

What Partner Center asks, ready to paste: the Hebrew listing (the package's language, `he-IL`) first, the English one
second (Store listings > Add/remove languages > English). The pictures are in this folder.

## Properties

| Field | Value |
| :--- | :--- |
| Category | Books + reference (subcategory: Reference), secondary: Education |
| Personal information | Yes (Google account for the Drive backup, crash reports) |
| Privacy policy URL | https://zayitapp.com/privacy.html |
| Website | https://zayitapp.com |
| Support contact | admin@zayitapp.com |
| Product declarations | none of the boxes |
| Memory | minimum 4 GB, recommended 8 GB |
| Restricted capability `runFullTrust` (Submission options) | Zayit is a desktop (Win32) application packaged with the Desktop Bridge: its main executable is a native image compiled from Kotlin/JVM code (GraalVM), with a Skia/Compose UI. runFullTrust is required to run this classic desktop executable as a packaged full-trust process; the app does not use any other restricted capability. It reads and writes only its own data (the library database it downloads, user settings) in its local app data folder. |

## Age rating (IARC questionnaire)

"All other app types". Ratings-relevant content in the package, user interaction, violence, sexuality, language, drugs,
age-restricted products, location sharing, purchases, rewards, browser: **No**. Online content (the library, downloaded
after the install): **Yes**. News or educational product: **Yes**. Rating: 3+ / Everyone.

## Pricing and availability

Free, all markets, public and discoverable.

## Pictures

| Partner Center field | File | Size |
| :--- | :--- | :--- |
| Screenshots (Desktop), in this order | `01-home-light.png` … `10-author-light.png` | 1600 × 1000 |
| Store logos > 9:16 poster art (the main logo on Windows) | `poster_9x16.png` (the website's olive grove and logo) | 1440 × 2160 |
| Store logos > 1:1 box art | `boxart_1x1.png` | 1080 × 1080 |
| Store logos > 1:1 app tile icon | `tile_300.png` | 300 × 300 |

The screenshots are the website's `showcase` scenario run on Windows (its title bar with the Windows buttons):
`ZAYIT_E2E_OUT=<dir> ZAYIT_E2E_SCENARIO=showcase ZAYIT_E2E_QUICK=1 ZAYIT_E2E_SIZE=1600x1000 ./gradlew :SeforimApp:run`.

Screenshot captions (optional, ≤ 200 characters each):

| # | עברית | English |
| :--- | :--- | :--- |
| 01 | דף הבית: חיפוש, זמני היום, לוח עברי וכדור הארץ בתלת-ממד | The home page: search, zmanim, the Hebrew calendar and the Earth in 3D |
| 02 | הגמרא עם רש"י ותוספות לצד הדף | The Gemara with Rashi and Tosafot beside the daf |
| 03 | פירושים ותרגומים סביב הטקסט, בערכת הנושא הכהה | Commentaries and targumim around the text, in the dark theme |
| 04 | חיפוש לפי המילים או לפי המשמעות, כל תוצאה בהקשרה | Search by the words or by the meaning, each result in its context |
| 05 | שנים מקרא: כל פסוק פעמיים ואחריו התרגום | Shnayim Mikra: each verse twice, then its targum |
| 06 | מקורות הקטע נפתחים לצידו | The sources of a passage open beside it |
| 07 | סידור חכם, לפי הנוסח והמקום שלכם | A smart siddur, after your nusach and your place |
| 08 | תוכן עניינים ועץ הספרייה | The table of contents and the library tree |
| 09 | שולחן ערוך, עם פרטי הספר | The Shulchan Aruch, with the book's details |
| 10 | דף מחבר: תקופתו, שמותיו וכל ספריו | An author's page: their era, names and all their books |

---

## עברית (he-IL)

**שם המוצר:** זית

**תיאור קצר:**

ספריית הלימוד שמובילה ישר לטקסט: תנ"ך, תלמוד, מדרש, הלכה ומפרשים, יותר מ-7,800 ספרים, עם חיפוש מהיר ומקורות מקושרים. חינם.

**תיאור:**

זית היא ספרייה תורנית חינמית למחשב, בנויה ללימוד: חיפוש מהיר, ניווט חלק, ציטוטים נקיים.

יותר מ-7,800 ספרים בהישג יד: תורה, נביאים וכתובים, משנה ותלמוד, מדרש, הלכה, מפרשים ועוד. לאחר הורדת הספרייה, זית עובדת לחלוטין ללא חיבור לאינטרנט.

מנוע חיפוש יוצא דופן: סרגל חכם אחד לספרים, למחברים, למראי מקומות ולטקסט. הקלידו "חולין יב" או "בראשית א ג" והגיעו ישר למקום. חפשו קטע לפי מילותיו או לפי משמעותו, גם בלי ניקוד ומאיות משוער.

פאנלים מודולריים: פירושים ותרגומים זה לצד זה, רש"י ותוספות לצד הדף, מקורות הקטע לצידו ופרטי כל ספר במבט אחד.

כרטיסיות, חלונות ושולחנות עבודה וירטואליים כמו ב-Chrome, סימניות בתיקיות, היסטוריה, סימון והערות על הטקסט.

ועוד: סידור חכם לפי הנוסח והמקום שלכם, שנים מקרא לפרשת השבוע, דפי מחברים, ודף בית עם ווידג'טים — זמני היום, לוח עברי, לימוד יומי, מולד וקידוש לבנה, כדור הארץ ומערכת השמש בתלת-ממד.

מהירה, גם על מחשבים ישנים. בהירה או כהה, בצבע ובגופנים שאתם אוהבים. חינם, ותישאר חינם.

**מה חדש בגרסה זו:** הגרסה הראשונה ב-Microsoft Store.

**תכונות עיקריות** (עד 20, עד 200 תווים כל אחת):

1. יותר מ-7,800 ספרים: תנ"ך, תלמוד, מדרש, הלכה ומפרשים
2. חיפוש לפי המילים או לפי המשמעות, גם בלי ניקוד
3. מראי מקומות: הקלידו "חולין יב" והגיעו ישר לדף
4. פירושים ותרגומים זה לצד זה
5. רש"י ותוספות לצד הדף, וכל פסוק שמובא בגמרא במרחק לחיצה
6. כרטיסיות, חלונות ושולחנות עבודה וירטואליים
7. סימניות, היסטוריה, סימון והערות על הטקסט
8. סידור חכם לפי הנוסח והמקום שלכם
9. שנים מקרא לפרשת השבוע
10. דף בית עם ווידג'טים: זמני היום, לוח עברי, לימוד יומי
11. עובדת ללא אינטרנט לאחר הורדת הספרייה
12. גיבוי ההערות וההגדרות ל-Google Drive
13. ערכות נושא בהירה וכהה
14. חינם

**מילות חיפוש** (עד 7, מילה אחת כל אחת: השדה דוחה ביטויים): תורה · תלמוד · גמרא · תנ"ך · סידור · הלכה · יהדות

**זכויות יוצרים:** © 2026 אליהו גמבש

**פותח על ידי:** אליהו גמבש

---

## English (en-US)

**Product name:** Zayit

**Short description:**

The study library that goes straight to the text: Tanakh, Talmud, Midrash, Halacha and commentaries, 7,800+ books, with fast search and linked sources. Free.

**Description:**

Zayit is a free desktop library of Jewish texts, built for study: fast search, smooth navigation, clean citations.

More than 7,800 books at your fingertips: Torah, Prophets and Writings, Mishnah and Talmud, Midrash, Halacha, commentaries and more. Once the library is downloaded, Zayit works completely offline.

An exceptional search engine: one smart bar for books, authors, references and text. Type "חולין יב" or "בראשית א ג" and land right there. Find a passage by its words or by its meaning, even without vocalization or with an approximate spelling.

Modular panels: commentaries and targumim side by side, Rashi and Tosafot beside the daf, a passage's sources beside it, and every book's details at a glance.

Tabs, windows and virtual desktops like Chrome, bookmarks in folders, history, highlights and notes on the text.

And more: a smart siddur after your nusach and your place, Shnayim Mikra for the weekly parasha, author pages, and a home page of widgets — zmanim, the Hebrew calendar, daily study cycles, the molad and Kiddush Levana, the Earth and the solar system in 3D.

Fast, even on old computers. Light or dark, with your accent color and fonts. Free, and it will stay free.

The interface and the texts are in Hebrew.

**What's new in this version:** The first release on the Microsoft Store.

**Product features** (≤ 20, ≤ 200 characters each):

1. 7,800+ books: Tanakh, Talmud, Midrash, Halacha and commentaries
2. Search by the words or by the meaning, even without vocalization
3. References: type "חולין יב" and land on the daf
4. Commentaries and targumim side by side
5. Rashi and Tosafot beside the daf, and every verse the Gemara quotes one click away
6. Tabs, windows and virtual desktops
7. Bookmarks, history, highlights and notes on the text
8. A smart siddur after your nusach and your place
9. Shnayim Mikra for the weekly parasha
10. A home page of widgets: zmanim, the Hebrew calendar, daily study
11. Works offline once the library is downloaded
12. Notes and settings backed up to Google Drive
13. Light and dark themes
14. Free

**Search terms** (≤ 7): Torah · Talmud · Gemara · Tanakh · Jewish · Siddur · Halacha

**Copyright:** © 2026 Elie Gambache

**Developed by:** Elie Gambache
