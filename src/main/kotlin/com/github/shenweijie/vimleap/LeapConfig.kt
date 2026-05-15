package com.github.shenweijie.vimleap

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "VimLeapConfig", storages = [Storage("vim-leap.xml")])
class LeapConfig : PersistentStateComponent<LeapConfig.ConfigState> {

    data class ConfigState(
        // Label characters used for jump targets.
        // Right-hand keys prioritized (left hand triggers the motion key).
        var labels: String = "sfnjklhodweimbuyvrgtaqpcxz/SFNJKLHODWEIMBUYVRGTAQPCXZ?",
        // Safe labels: autojump is triggered when all remaining matches fit in this set.
        // These should be keys you never press right after leaping.
        var safeLabels: String = "sfnut/SFNLHMUGTZ?",
        // Equivalence classes: characters in each group are treated as interchangeable.
        // Default: space/tab/CR/LF all match each other, so typing space targets line endings too.
        var equivalenceClasses: String = " \t\r\n",
        // Max targets shown as unlabeled ahead-of-cursor highlights during traversal.
        var maxTraversalTargets: Int = 10,
        // Show char1 preview highlights before char2 is typed.
        var preview: Boolean = true,
        // Search in all visible editor splits simultaneously.
        var searchAcrossSplits: Boolean = true,
        // Case-sensitive matching.
        var caseSensitive: Boolean = false,
        // Label appearance
        var labelFg: String = "#000000",
        var labelBg: String = "#ccff88",
        // Concealed label (non-active group) appearance
        var concealedFg: String = "#888888",
        var concealedBg: String = "#2a2a3a",
        // Match highlight
        var matchFg: String = "#ccff88",
        var matchBg: String = "#1e2030",
        // Nearest/autojump target
        var matchNearestFg: String = "#000000",
        var matchNearestBg: String = "#ff9900",
    )

    private var state = ConfigState()

    override fun getState(): ConfigState = state

    override fun loadState(s: ConfigState) {
        state = s
    }

    companion object {
        fun getInstance(): LeapConfig =
            ApplicationManager.getApplication().getService(LeapConfig::class.java)
    }
}
