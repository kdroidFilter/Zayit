package io.github.kdroidfilter.seforimapp.core.deeplink

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.newTabId

/**
 * Public, shareable deep link scheme for Zayit.
 *
 * Links reference the stable book / line database identifiers, so a link resolves to the
 * same content on any machine running the same database version:
 *
 *  - zayit://book/<bookId>                 -> open the book
 *  - zayit://book/<bookId>/line/<lineId>   -> open the book scrolled to a precise line
 *  - zayit://search/<url-encoded-query>    -> open a search
 */
const val ZAYIT_SCHEME = "zayit"

private const val PREFIX = "$ZAYIT_SCHEME://"
private const val HOST_BOOK = "book"
private const val HOST_SEARCH = "search"
private const val SEGMENT_LINE = "line"

/** Builds a shareable link to a book, optionally pinned to a precise line. */
fun bookShareLink(
    bookId: Long,
    lineId: Long? = null,
): String =
    buildString {
        append(PREFIX).append(HOST_BOOK).append('/').append(bookId)
        if (lineId != null && lineId > 0) append('/').append(SEGMENT_LINE).append('/').append(lineId)
    }

/** Builds a shareable link to a search query. */
fun searchShareLink(query: String): String = PREFIX + HOST_SEARCH + "/" + formEncode(query)

/**
 * Returns a shareable link for this destination, or null when there is nothing worth sharing
 * (the Home screen, or a book that has not finished loading — bookId not yet assigned).
 */
fun TabsDestination.toShareLink(): String? =
    when (this) {
        is TabsDestination.BookContent -> if (bookId > 0) bookShareLink(bookId, lineId) else null
        is TabsDestination.Search -> searchShareLink(searchQuery)
        is TabsDestination.Home -> null
        is TabsDestination.History -> null
        is TabsDestination.Favorites -> null
        is TabsDestination.Notes -> null
        is TabsDestination.Siddur -> null
        is TabsDestination.Author -> null
    }

/**
 * Parses a zayit:// deep link into a navigable destination carrying a fresh tabId, or null when
 * the URI does not match a known content scheme. Resolution against the database (e.g. checking
 * the book exists) is the caller's responsibility.
 */
fun parseZayitDeepLink(uri: String): TabsDestination? {
    if (!uri.startsWith(PREFIX)) return null
    val path = uri.removePrefix(PREFIX)
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    val newTabId = newTabId()
    return when (segments[0]) {
        HOST_BOOK -> {
            val bookId = segments.getOrNull(1)?.toLongOrNull() ?: return null
            val lineId =
                if (segments.getOrNull(2) == SEGMENT_LINE) {
                    segments.getOrNull(3)?.toLongOrNull() ?: return null
                } else {
                    null
                }
            TabsDestination.BookContent(bookId = bookId, tabId = newTabId, lineId = lineId)
        }
        HOST_SEARCH -> {
            val encoded = path.substringAfter("$HOST_SEARCH/", "")
            if (encoded.isEmpty()) return null
            TabsDestination.Search(searchQuery = formDecode(encoded), tabId = newTabId)
        }
        else -> null
    }
}

// application/x-www-form-urlencoded in UTF-8, as java.net.URLEncoder writes it and URLDecoder reads it (the links
// already shared must keep working): letters, digits and .-*_ as is, a space as +, any other byte as %XX
private fun formEncode(text: String): String =
    buildString {
        for (byte in text.encodeToByteArray()) {
            val c = (byte.toInt() and 0xFF).toChar()
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in ".-*_" -> append(c)
                c == ' ' -> append('+')
                else -> append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
            }
        }
    }

private fun formDecode(text: String): String {
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '+' -> bytes += ' '.code.toByte()
            c == '%' && i + 2 <= text.lastIndex -> {
                bytes += text.substring(i + 1, i + 3).toInt(16).toByte()
                i += 2
            }
            else -> bytes += c.toString().encodeToByteArray().toList()
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

private const val HEX = "0123456789ABCDEF"
