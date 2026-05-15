package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import kotlin.math.abs
import kotlin.math.ceil

private enum class Phase { CHAR1, CHAR2, SELECT, TRAVERSE }

private data class Target(
    val editor: Editor,
    val offset: Int,
    var label: String = "",
    var group: Int = 1,
    var isAutojump: Boolean = false,
    var advanceIndex: Int = 0,
)

private data class SavedSearch(
    val char1: String,
    val char2: String,
    val mode: LeapMode,
    val originOffset: Int,
)

object LeapHandler {

    var isActive: Boolean = false
        private set

    // ── session state ──────────────────────────────────────────────────────────
    private var phase = Phase.CHAR1
    private var mode = LeapMode.FORWARD
    private var char1 = ""
    private var char2 = ""
    private var targets: List<Target> = emptyList()
    private var sublists: Map<String, List<Target>> = emptyMap()   // char2-key → targets, built during preview
    private var autojumpIdx = -1       // index into targets; -1 = no autojump
    private var groupOffset = 0        // 0 = first label group visible
    private var traversalIdx = 0       // index in targets[] during TRAVERSE phase
    private var originEditor: Editor? = null
    private var originOffset = 0
    private var visualAnchor = -1   // ≥0 when started from visual mode; fixed end of selection
    private var savedDataContext: DataContext? = null

    // ── repeat state ───────────────────────────────────────────────────────────
    private var lastSearch: SavedSearch? = null
    private var sessionGen = 0   // incremented on every new session; used to cancel stale re-feeds

    // ── remote state ──────────────────────────────────────────────────────────
    var remoteOriginEditor: Editor? = null
    var remoteOriginOffset: Int = 0

    // ── canvas / backdrop ─────────────────────────────────────────────────────
    private val canvasMap = mutableMapOf<Editor, MarksCanvas>()
    private val backdropMap = mutableMapOf<Editor, RangeHighlighter>()
    private var oldTypedHandler: TypedActionHandler? = null
    private var keyDispatcher: KeyEventDispatcher? = null

    // ══════════════════════════════════════════════════════════════════════════
    // Entry points
    // ══════════════════════════════════════════════════════════════════════════

    fun start(leapMode: LeapMode, event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        if (isActive) stop(editor)
        isActive = true
        sessionGen++
        mode = leapMode
        phase = Phase.CHAR1
        char1 = ""; char2 = ""
        targets = emptyList(); sublists = emptyMap()
        autojumpIdx = -1; groupOffset = 0; traversalIdx = 0
        originEditor = editor
        originOffset = editor.caretModel.offset
        visualAnchor = captureVisualAnchor(editor)
        installHandlers(editor)
        showBackdrop(editor)
    }

    fun startWithMarks(editor: Editor, marks: List<MarksCanvas.Mark>, leapMode: LeapMode) {
        if (isActive) stop(editor)
        isActive = true
        sessionGen++
        mode = leapMode
        phase = Phase.SELECT
        char1 = ""; char2 = ""
        originEditor = editor
        originOffset = editor.caretModel.offset
        visualAnchor = -1
        autojumpIdx = -1; groupOffset = 0; traversalIdx = 0

        targets = marks.map { m -> Target(editor, m.offset, m.label, 1) }
        installHandlers(editor)
        showBackdrop(editor)
        updateCanvas(editor, marks)
    }

    fun stop(editor: Editor, keepBackdrop: Boolean = false) {
        if (!isActive) return
        isActive = false
        removeHandlers()
        if (!keepBackdrop) clearBackdrops()
        clearCanvases()
        targets = emptyList(); sublists = emptyMap()
        char1 = ""; char2 = ""
        visualAnchor = -1
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Input dispatch
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleTyped(editor: Editor, char: Char) {
        when (phase) {
            Phase.CHAR1    -> onChar1(editor, char)
            Phase.CHAR2    -> onChar2(editor, char)
            Phase.SELECT   -> onSelect(editor, char)
            Phase.TRAVERSE -> onTraverse(editor, char)
        }
    }

    // ── CHAR1: first keystroke ─────────────────────────────────────────────────
    private fun onChar1(editor: Editor, char: Char) {
        char1 = char.toString()
        if (mode.singleChar) {
            // flit mode: no second char needed, search immediately
            char2 = ""
            computeAndShow(editor)
            return
        }
        if (!LeapConfig.getInstance().state.preview) {
            phase = Phase.CHAR2
            return
        }
        phase = Phase.CHAR2
        showPreview(editor)
    }

    // ── CHAR2: second keystroke ────────────────────────────────────────────────
    private fun onChar2(editor: Editor, char: Char) {
        char2 = char.toString()
        computeAndShow(editor)
    }

    // ── SELECT: waiting for label / Space / Enter ──────────────────────────────
    private fun onSelect(editor: Editor, char: Char) {
        val cfg = LeapConfig.getInstance().state
        // Space → next group
        if (char == ' ') {
            val numGroups = targets.maxOfOrNull { it.group } ?: 1
            if (groupOffset < numGroups - 1) {
                groupOffset++
                refreshSelectDisplay(editor)
            }
            return
        }
        // Look for label match in the active group
        val activeGroup = groupOffset + 1
        val charStr = char.toString()
        val matching = targets.filter { t ->
            t.group == activeGroup && t.label.isNotEmpty() &&
                    t.label.substring(t.advanceIndex).startsWith(charStr)
        }
        if (matching.isNotEmpty()) {
            val advanced = matching.map { it.copy(advanceIndex = it.advanceIndex + 1) }
            val resolved = advanced.filter { it.advanceIndex >= it.label.length }
            if (resolved.size == 1) {
                val t = resolved.first()
                if (mode == LeapMode.TREESITTER) {
                    val mark = canvasMap[editor]?.marks?.find { m -> m.offset == t.offset }
                    if (mark != null && mark.rangeEnd > mark.offset) {
                        stop(editor)
                        selectRange(editor, mark.offset, mark.rangeEnd)
                        return
                    }
                }
                stop(editor)
                jumpTo(t.editor, adjustedOffset(t.offset, mode))
                return
            }
            // Narrow: update advance indices
            targets = targets.map { t ->
                val adv = advanced.find { it.offset == t.offset && it.editor == t.editor }
                adv ?: t
            }
            refreshSelectDisplay(editor)
            return
        }
        // No label matched — stop session and re-feed the key.
        // keepBackdrop=true keeps the dim overlay alive across the EDT boundary so there
        // is no visible flash when the next session's showBackdrop reuses it.
        // After execute: if no new session started (e.g. lastSearch==null), clear orphaned backdrop.
        val handler = oldTypedHandler
        val ctx = savedDataContext
        val gen = sessionGen
        stop(editor, keepBackdrop = true)
        if (handler != null && ctx != null)
            ApplicationManager.getApplication().invokeLater {
                if (sessionGen == gen) {
                    handler.execute(editor, char, ctx)
                    if (sessionGen == gen) clearBackdrops()  // no new session → clean up
                }
            }
    }

    // ── TRAVERSE: Enter/Backspace to step, any other key exits ─────────────────
    private fun onTraverse(editor: Editor, char: Char) {
        val handler = oldTypedHandler
        val ctx = savedDataContext
        val gen = sessionGen
        stop(editor, keepBackdrop = true)
        if (handler != null && ctx != null)
            ApplicationManager.getApplication().invokeLater {
                if (sessionGen == gen) {
                    handler.execute(editor, char, ctx)
                    if (sessionGen == gen) clearBackdrops()
                }
            }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Special key handling (ESC, Backspace, Enter, Space [for groups])
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleSpecialKey(editor: Editor, keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.VK_ESCAPE -> {
                stop(editor)
                true
            }
            KeyEvent.VK_BACK_SPACE -> {
                when (phase) {
                    Phase.CHAR2 -> {
                        // Back to char1 input
                        char1 = ""; sublists = emptyMap()
                        phase = Phase.CHAR1
                        clearCanvases()
                    }
                    Phase.SELECT -> {
                        if (groupOffset > 0) {
                            groupOffset--
                            refreshSelectDisplay(editor)
                        } else if (autojumpIdx >= 0) {
                            // Step backward in traversal
                            doTraverseStep(editor, forward = false)
                        } else {
                            stop(editor)
                        }
                    }
                    Phase.TRAVERSE -> doTraverseStep(editor, forward = false)
                    else -> stop(editor)
                }
                true
            }
            KeyEvent.VK_ENTER -> {
                when (phase) {
                    Phase.CHAR1 -> {
                        // Repeat last search
                        val ls = lastSearch
                        if (ls != null) repeatLastSearch(editor, ls) else stop(editor)
                    }
                    Phase.CHAR2 -> {
                        // Shortcut: jump to nearest char1-only match (treat char1+<Enter> as 1-char mode)
                        char2 = char1   // same-char trick: search for char1+char1
                        computeAndShow(editor)
                    }
                    Phase.SELECT -> {
                        // If autojumped: start forward traversal; else jump to target[0]
                        if (autojumpIdx >= 0) {
                            doTraverseStep(editor, forward = true)
                        } else if (targets.isNotEmpty()) {
                            val t = targets.first()
                            stop(editor)
                            jumpTo(t.editor, adjustedOffset(t.offset, mode))
                        } else {
                            stop(editor)
                        }
                    }
                    Phase.TRAVERSE -> doTraverseStep(editor, forward = true)
                }
                true
            }
            else -> false
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Core search + label logic
    // ══════════════════════════════════════════════════════════════════════════

    private fun computeAndShow(editor: Editor) {
        val cfg = LeapConfig.getInstance().state
        val eqv = SearchEngine.buildEqvLookup(cfg.equivalenceClasses)
        val pattern = char1 + char2

        // ── Fast path: preview already computed sublists ────────────────────────
        if (sublists.isNotEmpty()) {
            val caseSensitive = cfg.caseSensitive || char1.any { it.isUpperCase() }
            val c = char2.firstOrNull() ?: run { stop(editor); return }
            val norm = if (caseSensitive) c else c.lowercaseChar()
            val key = (eqv[norm] ?: norm).toString()
            val sublist = sublists[key]
            if (sublist.isNullOrEmpty()) { stop(editor); return }

            targets = sublist
            autojumpIdx = if (targets.firstOrNull()?.isAutojump == true) 0 else -1
            groupOffset = 0
            lastSearch = SavedSearch(char1, char2, mode, originOffset)

            if (autojumpIdx >= 0) {
                val t = targets[autojumpIdx]
                jumpTo(t.editor, adjustedOffset(t.offset, mode))
                if (targets.size == 1) { stop(editor); return }
            }
            showBackdrop(editor)
            phase = Phase.SELECT

            val editors = if (cfg.searchAcrossSplits) visibleEditors(editor) else listOf(editor)
            val marksByEditor = targets.groupBy { it.editor }
            for (e in editors) {
                val marks = (marksByEditor[e] ?: emptyList()).map { t ->
                    MarksCanvas.Mark(
                        offset = t.offset,
                        charLength = pattern.length,
                        label = t.label,
                        group = t.group,
                        isAutojump = t.isAutojump,
                        isConcealed = (t.group > 1 && !t.isAutojump),
                    )
                }
                updateCanvas(e, marks)
            }
            (canvasMap.keys - editors.toSet()).forEach { removeCanvas(it) }
            return
        }

        // ── Slow path: no preview, compute fresh ───────────────────────────────
        val caseSensitive = cfg.caseSensitive || pattern.any { it.isUpperCase() }
        val editors = if (cfg.searchAcrossSplits && !mode.bidirectional) visibleEditors(editor)
        else listOf(editor)

        val allMatches = mutableListOf<Pair<Editor, Int>>()
        for (e in editors) {
            val matches = SearchEngine.findMatches(e, pattern, caseSensitive, eqv)
            val cursor = if (e === originEditor) originOffset else e.caretModel.offset
            val filtered = filterByDirection(matches, cursor, mode)
            allMatches += sortByDistance(filtered, cursor, mode).map { e to it }
        }
        if (allMatches.isEmpty()) { stop(editor); return }

        val excludeChars = allMatches.flatMap { (e, off) ->
            SearchEngine.nextCharsAfterMatches(e, listOf(off), pattern.length, caseSensitive)
        }.toSet()

        targets = assignLabels(allMatches, excludeChars, cfg)
        autojumpIdx = if (targets.firstOrNull()?.isAutojump == true) 0 else -1
        groupOffset = 0
        lastSearch = SavedSearch(char1, char2, mode, originOffset)

        if (autojumpIdx >= 0) {
            val t = targets[autojumpIdx]
            jumpTo(t.editor, adjustedOffset(t.offset, mode))
            if (targets.size == 1) { stop(editor); return }
        }
        showBackdrop(editor)
        phase = Phase.SELECT

        val marksByEditor = targets.groupBy { it.editor }
        for (e in editors) {
            val marks = (marksByEditor[e] ?: emptyList()).map { t ->
                MarksCanvas.Mark(
                    offset = t.offset,
                    charLength = pattern.length,
                    label = t.label,
                    group = t.group,
                    isAutojump = t.isAutojump,
                    isConcealed = (t.group > 1 && !t.isAutojump),
                )
            }
            updateCanvas(e, marks)
        }
        (canvasMap.keys - editors.toSet()).forEach { removeCanvas(it) }
    }

    private fun showPreview(editor: Editor) {
        if (char1.isEmpty()) return
        val cfg = LeapConfig.getInstance().state
        val eqv = SearchEngine.buildEqvLookup(cfg.equivalenceClasses)
        val caseSensitive = cfg.caseSensitive || char1.any { it.isUpperCase() }
        val editors = if (cfg.searchAcrossSplits) visibleEditors(editor) else listOf(editor)

        // Collect all char1 matches across editors, sorted by distance
        val allChar1Matches = mutableListOf<Pair<Editor, Int>>()
        for (e in editors) {
            val matches = SearchEngine.findMatches(e, char1, caseSensitive, eqv)
            val cursor = if (e === originEditor) originOffset else e.caretModel.offset
            allChar1Matches += sortByDistance(filterByDirection(matches, cursor, mode), cursor, mode)
                .map { e to it }
        }
        if (allChar1Matches.isEmpty()) return

        // Group matches by char2 (the character immediately after each match), normalized
        val groups = mutableMapOf<String, MutableList<Pair<Editor, Int>>>()
        for ((e, off) in allChar1Matches) {
            val doc = e.document.immutableCharSequence
            val nextOff = off + char1.length
            val key = if (nextOff < doc.length) {
                val c = doc[nextOff]
                val norm = if (caseSensitive) c else c.lowercaseChar()
                (eqv[norm] ?: norm).toString()
            } else "\n"
            groups.getOrPut(key) { mutableListOf() }.add(e to off)
        }

        // Assign labels independently per group; collect all marks for display
        val newSublists = mutableMapOf<String, List<Target>>()
        val allMarksByEditor = mutableMapOf<Editor, MutableList<MarksCanvas.Mark>>()
        for ((key, matches) in groups) {
            val excludeChars = matches.flatMap { (e, off) ->
                SearchEngine.nextCharsAfterMatches(e, listOf(off), 2, caseSensitive)
            }.toSet()
            val groupTargets = assignLabels(matches, excludeChars, cfg)
            newSublists[key] = groupTargets
            for (t in groupTargets) {
                allMarksByEditor.getOrPut(t.editor) { mutableListOf() }.add(
                    MarksCanvas.Mark(
                        offset = t.offset,
                        charLength = char1.length,
                        label = t.label,
                        group = t.group,
                        isConcealed = (t.group > 1),
                    )
                )
            }
        }
        sublists = newSublists

        for (e in editors) updateCanvas(e, allMarksByEditor[e] ?: emptyList())
        (canvasMap.keys - editors.toSet()).forEach { removeCanvas(it) }
    }

    private fun repeatLastSearch(editor: Editor, ls: SavedSearch) {
        char1 = ls.char1; char2 = ls.char2
        computeAndShow(editor)
    }

    // ── Label assignment ────────────────────────────────────────────────────────
    private fun assignLabels(
        matches: List<Pair<Editor, Int>>,
        excludeChars: Set<Char>,
        cfg: LeapConfig.ConfigState,
    ): List<Target> {
        val safeLabels = cfg.safeLabels.filter { it.lowercaseChar() !in excludeChars }
        val labels = cfg.labels.filter { it.lowercaseChar() !in excludeChars }

        val useAutojump = matches.size <= safeLabels.length + 1 &&
                safeLabels.isNotEmpty() && matches.isNotEmpty()

        return if (useAutojump) {
            matches.mapIndexed { i, (e, off) ->
                val isAj = (i == 0)
                val label = if (isAj || i - 1 >= safeLabels.length) ""
                else safeLabels[i - 1].toString()
                Target(e, off, label = label, group = 1, isAutojump = isAj)
            }
        } else {
            val labList = labels.toList()
            if (labList.isEmpty()) return matches.map { (e, off) -> Target(e, off) }
            matches.mapIndexed { i, (e, off) ->
                val labelChar = labList[i % labList.size].toString()
                val group = i / labList.size + 1
                Target(e, off, label = labelChar, group = group)
            }
        }
    }

    // ── Traversal ──────────────────────────────────────────────────────────────
    private fun doTraverseStep(editor: Editor, forward: Boolean) {
        if (targets.isEmpty()) { stop(editor); return }
        val start = if (autojumpIdx >= 0 && phase == Phase.SELECT) autojumpIdx else traversalIdx

        val newIdx = if (forward) {
            (start + 1).coerceAtMost(targets.size - 1)
        } else {
            (start - 1).coerceAtLeast(0)
        }
        traversalIdx = newIdx
        phase = Phase.TRAVERSE

        val t = targets[newIdx]
        jumpTo(t.editor, adjustedOffset(t.offset, mode))

        // Show traversal window: current target + up to N ahead
        val cfg = LeapConfig.getInstance().state
        val window = targets.indices
            .filter { it >= newIdx }
            .take(cfg.maxTraversalTargets + 1)

        val marksByEditor = mutableMapOf<Editor, MutableList<MarksCanvas.Mark>>()
        val patternLen = (char1 + char2).length
        for (idx in window) {
            val tgt = targets[idx]
            val mark = MarksCanvas.Mark(
                offset = tgt.offset,
                charLength = patternLen,
                label = "",
                isAutojump = (idx == newIdx),
                isTraversal = (idx != newIdx),
            )
            marksByEditor.getOrPut(tgt.editor) { mutableListOf() }.add(mark)
        }
        for ((e, marks) in marksByEditor) updateCanvas(e, marks)
        (canvasMap.keys - marksByEditor.keys).forEach { removeCanvas(it) }
    }

    // ── Refresh SELECT display after group change ───────────────────────────────
    private fun refreshSelectDisplay(editor: Editor) {
        val patternLen = (char1 + char2).length
        val activeGroup = groupOffset + 1
        val marksByEditor = targets.groupBy { it.editor }
        val editors = visibleEditors(editor)
        for (e in editors) {
            val marks = (marksByEditor[e] ?: emptyList()).map { t ->
                val concealed = !t.isAutojump && t.group != activeGroup
                MarksCanvas.Mark(
                    offset = t.offset,
                    charLength = patternLen,
                    label = t.label,
                    group = t.group,
                    isAutojump = t.isAutojump,
                    isConcealed = concealed,
                    advanceIndex = t.advanceIndex,
                )
            }
            updateCanvas(e, marks)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Treesitter
    // ══════════════════════════════════════════════════════════════════════════

    fun startTreesitter(event: AnActionEvent) {
        start(LeapMode.TREESITTER, event)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Remote
    // ══════════════════════════════════════════════════════════════════════════

    fun startRepeat(event: AnActionEvent, reversed: Boolean) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val ls = lastSearch ?: return
        if (isActive) stop(editor)
        isActive = true
        sessionGen++
        mode = if (reversed) ls.mode.flipped() else ls.mode
        phase = Phase.CHAR1
        char1 = ls.char1; char2 = ls.char2
        targets = emptyList(); sublists = emptyMap()
        autojumpIdx = -1; groupOffset = 0; traversalIdx = 0
        originEditor = editor
        originOffset = editor.caretModel.offset
        visualAnchor = captureVisualAnchor(editor)
        installHandlers(editor)
        computeAndShow(editor)  // showBackdrop is called inside only when matches are found
        lastSearch = ls  // direction is always relative to the original search, not the repeat
    }

    fun startRemote(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        remoteOriginEditor = editor
        remoteOriginOffset = editor.caretModel.offset
        start(LeapMode.REMOTE, event)
    }

    fun returnFromRemote() {
        val re = remoteOriginEditor ?: return
        jumpTo(re, remoteOriginOffset)
        remoteOriginEditor = null
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Jump / select helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun jumpTo(editor: Editor, offset: Int) {
        val clamped = offset.coerceIn(0, editor.document.textLength - 1)
        val anchor = visualAnchor
        WriteIntentReadAction.run(Runnable {
            editor.caretModel.moveToOffset(clamped)
            if (anchor >= 0) {
                val from = minOf(anchor, clamped)
                val to = maxOf(anchor, clamped)
                editor.selectionModel.setSelection(from, to + 1)
            }
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        })
    }

    private fun selectRange(editor: Editor, start: Int, end: Int) {
        WriteIntentReadAction.run(Runnable {
            editor.caretModel.moveToOffset(end.coerceIn(0, editor.document.textLength))
            editor.selectionModel.setSelection(
                start.coerceIn(0, editor.document.textLength),
                end.coerceIn(0, editor.document.textLength),
            )
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        })
    }

    private fun adjustedOffset(offset: Int, m: LeapMode): Int = when (m.offset) {
        -1 -> (offset - 1).coerceAtLeast(0)
        +1 -> offset + 1
        else -> offset
    }

    private fun captureVisualAnchor(editor: Editor): Int {
        val sel = editor.selectionModel
        if (!sel.hasSelection()) return -1
        val caretOff = editor.caretModel.offset
        val selStart = sel.selectionStart
        val selEnd = sel.selectionEnd
        return if (caretOff <= selStart) selEnd - 1 else selStart
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Direction / distance helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun filterByDirection(matches: List<Int>, cursor: Int, m: LeapMode): List<Int> = when {
        m.backward && !m.bidirectional -> matches.filter { it < cursor }
        !m.backward && !m.bidirectional -> matches.filter { it > cursor }
        else -> matches  // bidirectional: all
    }

    private fun sortByDistance(matches: List<Int>, cursor: Int, m: LeapMode): List<Int> {
        return if (m.bidirectional) {
            matches.sortedWith(compareBy({ abs(it - cursor) }, { if (it > cursor) 0 else 1 }))
        } else if (m.backward) {
            matches.sortedDescending()
        } else {
            matches.sorted()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Canvas helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun updateCanvas(editor: Editor, marks: List<MarksCanvas.Mark>) {
        val canvas = canvasMap.getOrPut(editor) {
            MarksCanvas(editor).also { c ->
                editor.contentComponent.add(c)
                editor.contentComponent.setComponentZOrder(c, 0)
            }
        }
        canvas.marks = marks
        canvas.sync()
        canvas.repaint()
    }

    private fun removeCanvas(editor: Editor) {
        canvasMap.remove(editor)?.let { c ->
            editor.contentComponent.remove(c)
            editor.contentComponent.repaint()
        }
    }

    private fun clearCanvases() {
        for ((editor, canvas) in canvasMap) {
            editor.contentComponent.remove(canvas)
            editor.contentComponent.repaint()
        }
        canvasMap.clear()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Backdrop
    // ══════════════════════════════════════════════════════════════════════════

    private fun showBackdrop(editor: Editor) {
        if (backdropMap.containsKey(editor)) return  // reuse backdrop from previous session to avoid flash
        val (start, end) = SearchEngine.visibleRange(editor)
        val attrs = TextAttributes().apply {
            foregroundColor = java.awt.Color(150, 150, 150, 120)
        }
        backdropMap[editor] = editor.markupModel.addRangeHighlighter(
            start, end,
            HighlighterLayer.SELECTION - 1,
            attrs,
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun clearBackdrops() {
        for ((editor, h) in backdropMap) editor.markupModel.removeHighlighter(h)
        backdropMap.clear()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Handler install / remove
    // ══════════════════════════════════════════════════════════════════════════

    private fun installHandlers(editor: Editor) {
        val typedAction = TypedAction.getInstance()
        oldTypedHandler = typedAction.rawHandler
        typedAction.setupRawHandler(LeapTypedHandler)

        keyDispatcher = KeyEventDispatcher { e ->
            if (!isActive) return@KeyEventDispatcher false
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            val ed = originEditor ?: return@KeyEventDispatcher false
            val consumed = handleSpecialKey(ed, e.keyCode)
            if (consumed) e.consume()
            consumed
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher!!)
    }

    private fun removeHandlers() {
        oldTypedHandler?.let { TypedAction.getInstance().setupRawHandler(it) }
        oldTypedHandler = null
        keyDispatcher?.let { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it) }
        keyDispatcher = null
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Editor utilities
    // ══════════════════════════════════════════════════════════════════════════

    private fun visibleEditors(anchor: Editor): List<Editor> {
        val project = anchor.project ?: return listOf(anchor)
        return FileEditorManager.getInstance(project)
            .allEditors
            .filterIsInstance<TextEditor>()
            .map { it.editor }
            .filter { it.component.isShowing }
            .ifEmpty { listOf(anchor) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TypedActionHandler delegate
    // ══════════════════════════════════════════════════════════════════════════

    private object LeapTypedHandler : TypedActionHandler {
        override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
            if (!isActive) {
                oldTypedHandler?.execute(editor, charTyped, dataContext)
                return
            }
            savedDataContext = dataContext
            handleTyped(editor, charTyped)
        }
    }
}
