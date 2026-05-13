package com.github.shenweijie.vimleap

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.TextRange
import java.awt.*
import javax.swing.JComponent

/**
 * Transparent overlay component painted on top of the editor content.
 * Renders match highlights and jump labels for all leap states.
 */
class MarksCanvas(private val editor: Editor) : JComponent() {

    data class Mark(
        val offset: Int,
        val charLength: Int,         // chars to highlight as a match; 0 = range mark (treesitter)
        val label: String,           // label text (empty = autojump/traversal target — no label)
        val group: Int = 1,          // label group; only group == activeGroup is rendered fully
        val isAutojump: Boolean = false,   // nearest/autojump target: rendered as nearest highlight
        val isPreview: Boolean = false,    // char1 preview: semi-transparent highlight only
        val isConcealed: Boolean = false,  // non-active group: show dimmed placeholder
        val isTraversal: Boolean = false,  // traversal match: highlighted but no label
        val advanceIndex: Int = 0,         // chars of multi-char label already typed
        val rangeEnd: Int = 0,             // for treesitter range marks
    )

    var marks: List<Mark> = emptyList()

    init {
        isOpaque = false
        cursor = Cursor.getDefaultCursor()
        sync()
    }

    fun sync() {
        bounds = Rectangle(editor.contentComponent.size)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val cfg = LeapConfig.getInstance().state
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        g2.font = font
        val fm = g2.fontMetrics
        val lineHeight = editor.lineHeight
        val doc = editor.document

        for (mark in marks) {
            if (mark.offset < 0 || mark.offset >= doc.textLength) continue
            when {
                mark.isPreview    -> paintPreview(g2, mark, fm, lineHeight, cfg, doc)
                mark.charLength == 0 && mark.rangeEnd > mark.offset -> paintRangeMark(g2, mark, fm, lineHeight, cfg)
                mark.isConcealed  -> paintConcealed(g2, mark, fm, lineHeight, cfg, doc)
                else              -> {
                    paintMatchHighlight(g2, mark, fm, lineHeight, cfg, doc)
                    if (mark.label.isNotEmpty() && !mark.isAutojump && !mark.isTraversal) {
                        paintLabel(g2, mark, fm, lineHeight, cfg, doc)
                    }
                }
            }
        }
    }

    private fun paintMatchHighlight(
        g: Graphics2D, mark: Mark, fm: FontMetrics, lineHeight: Int,
        cfg: LeapConfig.ConfigState, doc: com.intellij.openapi.editor.Document,
    ) {
        val end = (mark.offset + mark.charLength).coerceAtMost(doc.textLength)
        val matchText = doc.getText(TextRange(mark.offset, end))
        val xy = editor.offsetToXY(mark.offset)
        val bg = parseColor(if (mark.isAutojump || mark.isTraversal) cfg.matchNearestBg else cfg.matchBg)
        val fg = parseColor(if (mark.isAutojump || mark.isTraversal) cfg.matchNearestFg else cfg.matchFg)
        val w = fm.stringWidth(matchText)
        g.color = bg
        g.fillRect(xy.x, xy.y, w, lineHeight)
        g.color = fg
        g.drawString(matchText, xy.x, xy.y + fm.ascent)
    }

    private fun paintLabel(
        g: Graphics2D, mark: Mark, fm: FontMetrics, lineHeight: Int,
        cfg: LeapConfig.ConfigState, doc: com.intellij.openapi.editor.Document,
    ) {
        val remaining = mark.label.substring(mark.advanceIndex)
        if (remaining.isEmpty()) return
        val end = (mark.offset + mark.charLength).coerceAtMost(doc.textLength)
        val matchText = doc.getText(TextRange(mark.offset, end))
        val xy = editor.offsetToXY(mark.offset)
        val labelX = xy.x + fm.stringWidth(matchText)
        val tagW = fm.stringWidth(remaining)
        g.color = parseColor(cfg.labelBg)
        g.fillRect(labelX, xy.y, tagW, lineHeight)
        g.color = parseColor(cfg.labelFg)
        g.drawString(remaining, labelX, xy.y + fm.ascent)
    }

    /** Non-active group: draw dimmed bullet placeholder where the label would be. */
    private fun paintConcealed(
        g: Graphics2D, mark: Mark, fm: FontMetrics, lineHeight: Int,
        cfg: LeapConfig.ConfigState, doc: com.intellij.openapi.editor.Document,
    ) {
        val end = (mark.offset + mark.charLength).coerceAtMost(doc.textLength)
        val matchText = doc.getText(TextRange(mark.offset, end))
        val xy = editor.offsetToXY(mark.offset)
        val labelX = xy.x + fm.stringWidth(matchText)
        val tagW = fm.stringWidth("·")
        g.color = parseColor(cfg.concealedBg)
        g.fillRect(labelX, xy.y, tagW, lineHeight)
        g.color = parseColor(cfg.concealedFg)
        g.drawString("·", labelX, xy.y + fm.ascent)
    }

    /** Char1 preview: semi-transparent match highlight only, no label. */
    private fun paintPreview(
        g: Graphics2D, mark: Mark, fm: FontMetrics, lineHeight: Int,
        cfg: LeapConfig.ConfigState, doc: com.intellij.openapi.editor.Document,
    ) {
        val end = (mark.offset + mark.charLength).coerceAtMost(doc.textLength)
        if (end <= mark.offset) return
        val matchText = doc.getText(TextRange(mark.offset, end))
        val xy = editor.offsetToXY(mark.offset)
        val w = fm.stringWidth(matchText)
        val old = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
        g.color = parseColor(cfg.matchBg)
        g.fillRect(xy.x, xy.y, w, lineHeight)
        g.composite = old
        g.color = parseColor(cfg.matchFg)
        g.drawString(matchText, xy.x, xy.y + fm.ascent)
    }

    /** Treesitter range label: label at start + mirror at end. */
    private fun paintRangeMark(
        g: Graphics2D, mark: Mark, fm: FontMetrics, lineHeight: Int,
        cfg: LeapConfig.ConfigState,
    ) {
        if (mark.label.isEmpty()) return
        fun drawAt(offset: Int) {
            if (offset < 0 || offset >= editor.document.textLength) return
            val xy = editor.offsetToXY(offset)
            val w = fm.stringWidth(mark.label)
            g.color = parseColor(cfg.labelBg)
            g.fillRect(xy.x, xy.y, w, lineHeight)
            g.color = parseColor(cfg.labelFg)
            g.drawString(mark.label, xy.x, xy.y + fm.ascent)
        }
        drawAt(mark.offset)
        if (mark.rangeEnd > mark.offset) drawAt(mark.rangeEnd)
    }

    companion object {
        fun parseColor(hex: String): Color = try {
            Color.decode(hex)
        } catch (_: NumberFormatException) {
            Color.ORANGE
        }
    }
}
