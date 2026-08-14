package com.kanicream.repolens.text

/** Line-splitting helpers shared by analyzers and formatters. */
object TextLines {

    /**
     * Splits [text] into lines without their terminators, accepting `\n`, `\r\n` and `\r`.
     * Empty text yields an empty list; a trailing newline yields a final empty line,
     * matching how IntelliJ documents count physical lines.
     */
    fun split(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.split("\r\n", "\n", "\r")

    /** Physical line count of [text]; `0` for empty text. */
    fun physicalLineCount(text: String): Int = split(text).size
}
