package io.github.kdroidfilter.seforimapp.logger

import java.text.SimpleDateFormat
import java.util.Date

var allowLogging: Boolean = true
var loggingLevel: LoggingLevel = LoggingLevel.VERBOSE

class LoggingLevel(
    val priority: Int,
) {
    companion object {
        val VERBOSE = LoggingLevel(0)
        val DEBUG = LoggingLevel(1)
        val INFO = LoggingLevel(2)
        val WARN = LoggingLevel(3)
        val ERROR = LoggingLevel(4)
    }
}

private const val COLOR_RED = "\u001b[31m"
private const val COLOR_AQUA = "\u001b[36m"
private const val COLOR_LIGHT_GRAY = "\u001b[37m"
private const val COLOR_ORANGE = "\u001b[38;2;255;165;0m"
private const val COLOR_RESET = "\u001b[0m"

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

private fun getCurrentTimestamp(): String = dateFormat.format(Date())

fun debugln(message: () -> String) {
    if (allowLogging && loggingLevel.priority <= LoggingLevel.DEBUG.priority) {
        println("${getCurrentTimestamp()} ${message()}")
    }
}

fun verboseln(message: () -> String) {
    if (allowLogging && loggingLevel.priority <= LoggingLevel.VERBOSE.priority) {
        println(message(), COLOR_LIGHT_GRAY)
    }
}

fun infoln(message: () -> String) {
    if (allowLogging && loggingLevel.priority <= LoggingLevel.INFO.priority) {
        println(message(), COLOR_AQUA)
    }
}

fun warnln(message: () -> String) {
    if (allowLogging && loggingLevel.priority <= LoggingLevel.WARN.priority) {
        println(message(), COLOR_ORANGE)
    }
}

fun errorln(message: () -> String) {
    if (allowLogging && loggingLevel.priority <= LoggingLevel.ERROR.priority) {
        println(message(), COLOR_RED)
    }
}

private fun println(
    message: String,
    color: String,
) {
    println(color + getCurrentTimestamp() + " " + message + COLOR_RESET)
}
