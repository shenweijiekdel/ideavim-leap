package com.github.shenweijie.vimleap

import com.intellij.openapi.editor.Editor
import java.awt.Point
import java.awt.Rectangle

object SearchEngine {

    /**
     * Build a char → representative-char lookup from the equivalence class string.
     * All chars in the same equivalence class map to the first char of that class.
     * Example: " \t\r\n" → {' '→' ', '\t'→' ', '\r'→' ', '\n'→' '}
     */
    fun buildEqvLookup(eqvClassesStr: String): Map<Char, Char> {
        val result = mutableMapOf<Char, Char>()
        for (cls in parseEqvClasses(eqvClassesStr)) {
            val rep = cls.first()
            for (ch in cls) result[ch] = rep
        }
        return result
    }

    private fun parseEqvClasses(str: String): List<List<Char>> {
        if (str.isEmpty()) return emptyList()
        return listOf(str.toList())
    }

    /** Normalize a char through the equivalence lookup. */
    private fun normalize(ch: Char, eqv: Map<Char, Char>, caseSensitive: Boolean): Char {
        val c = if (caseSensitive) ch else ch.lowercaseChar()
        return eqv[c] ?: c
    }

    /**
     * Find all offsets in the visible area where [pattern] matches,
     * respecting equivalence classes and case sensitivity.
     */
    fun findMatches(
        editor: Editor,
        pattern: String,
        caseSensitive: Boolean,
        eqv: Map<Char, Char> = emptyMap(),
    ): List<Int> {
        if (pattern.isEmpty()) return emptyList()
        val (start, end) = visibleRange(editor)
        val text = editor.document.immutableCharSequence
        val norm = pattern.map { normalize(it, eqv, caseSensitive) }
        val results = mutableListOf<Int>()
        var i = start
        while (i <= end - pattern.length) {
            var match = true
            for (j in pattern.indices) {
                if (normalize(text[i + j], eqv, caseSensitive) != norm[j]) {
                    match = false
                    break
                }
            }
            if (match) results.add(i)
            i++
        }
        return results
    }

    /**
     * Return the set of characters that immediately follow each match,
     * used to exclude those characters from label assignment (avoid label/char3 clash).
     */
    fun nextCharsAfterMatches(
        editor: Editor,
        matches: List<Int>,
        patternLen: Int,
        caseSensitive: Boolean,
    ): Set<Char> {
        val text = editor.document.immutableCharSequence
        return matches.mapNotNull { offset ->
            val next = offset + patternLen
            if (next < text.length) {
                val ch = text[next]
                if (caseSensitive) ch else ch.lowercaseChar()
            } else null
        }.toSet()
    }

    fun visibleRange(editor: Editor): Pair<Int, Int> {
        val va: Rectangle = editor.scrollingModel.visibleArea
        val start = editor.logicalPositionToOffset(
            editor.xyToLogicalPosition(Point(va.x, va.y))
        ).coerceAtLeast(0)
        val end = editor.logicalPositionToOffset(
            editor.xyToLogicalPosition(Point(va.x + va.width, va.y + va.height))
        ).coerceAtMost(editor.document.textLength)
        return start to end
    }
}
