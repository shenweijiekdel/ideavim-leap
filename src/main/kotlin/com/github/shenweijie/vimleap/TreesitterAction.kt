package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

/**
 * PSI-based treesitter-style structural selection.
 * Mirrors leap.nvim's get_nodes: walks up from the element at cursor collecting
 * ancestors, deduplicates by range, then labels them innermost-first.
 */
class TreesitterAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val offset = editor.caretModel.offset
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val leaf = psiFile.findElementAt(offset) ?: return

        var start: PsiElement = leaf
        while (start is PsiWhiteSpace || (start.textRange?.length ?: 0) == 0) {
            start = start.parent ?: return
            if (start is PsiFile) return
        }

        val seen = LinkedHashSet<Pair<Int, Int>>()
        val nodes = generateSequence(start) { it.parent }
            .filter { it !is PsiFile }
            .filter { el ->
                val r = el.textRange ?: return@filter false
                seen.add(r.startOffset to r.endOffset)
            }
            .toList()
        if (nodes.isEmpty()) return

        val cfg = LeapConfig.getInstance().state
        val labelChars = cfg.labels.toList()
        val marks = nodes.mapIndexedNotNull { i, el ->
            if (i >= labelChars.size) return@mapIndexedNotNull null
            val r = el.textRange ?: return@mapIndexedNotNull null
            MarksCanvas.Mark(
                offset = r.startOffset,
                charLength = 0,
                label = labelChars[i].toString(),
                rangeEnd = r.endOffset,
            )
        }
        if (marks.isEmpty()) return
        LeapHandler.startWithMarks(editor, marks, LeapMode.TREESITTER)
    }
}
