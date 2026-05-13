package com.github.shenweijie.vimleap

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class LeapConfigurable : Configurable {

    private lateinit var labelsField: JBTextField
    private lateinit var safeLabelsField: JBTextField
    private lateinit var eqvClassesField: JBTextField
    private lateinit var maxTraversalSpinner: JSpinner
    private lateinit var previewBox: JBCheckBox
    private lateinit var searchAcrossSplitsBox: JBCheckBox
    private lateinit var caseSensitiveBox: JBCheckBox
    private lateinit var labelFgPanel: ColorPanel
    private lateinit var labelBgPanel: ColorPanel
    private lateinit var concealedFgPanel: ColorPanel
    private lateinit var concealedBgPanel: ColorPanel
    private lateinit var matchFgPanel: ColorPanel
    private lateinit var matchBgPanel: ColorPanel
    private lateinit var matchNearestFgPanel: ColorPanel
    private lateinit var matchNearestBgPanel: ColorPanel

    override fun getDisplayName() = "vim-leap"

    override fun createComponent(): JComponent {
        val cfg = LeapConfig.getInstance().state
        labelsField = JBTextField(cfg.labels)
        safeLabelsField = JBTextField(cfg.safeLabels)
        eqvClassesField = JBTextField(cfg.equivalenceClasses)
        maxTraversalSpinner = JSpinner(SpinnerNumberModel(cfg.maxTraversalTargets, 0, 100, 1))
        previewBox = JBCheckBox("Show char1 preview highlights", cfg.preview)
        searchAcrossSplitsBox = JBCheckBox("Search across splits", cfg.searchAcrossSplits)
        caseSensitiveBox = JBCheckBox("Always case-sensitive", cfg.caseSensitive)
        labelFgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.labelFg) }
        labelBgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.labelBg) }
        concealedFgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.concealedFg) }
        concealedBgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.concealedBg) }
        matchFgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.matchFg) }
        matchBgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.matchBg) }
        matchNearestFgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.matchNearestFg) }
        matchNearestBgPanel = ColorPanel().also { it.selectedColor = Color.decode(cfg.matchNearestBg) }

        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; insets = Insets(4, 8, 4, 8) }
        var row = 0
        fun row(label: String, comp: JComponent) {
            c.gridx = 0; c.gridy = row; c.weightx = 0.3; panel.add(JBLabel(label), c)
            c.gridx = 1; c.weightx = 0.7; panel.add(comp, c)
            row++
        }
        fun full(comp: JComponent) {
            c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1.0; panel.add(comp, c)
            c.gridwidth = 1; row++
        }

        row("Labels:", labelsField)
        row("Safe labels:", safeLabelsField)
        row("Equivalence classes:", eqvClassesField)
        row("Max traversal targets:", maxTraversalSpinner)
        row("Label fg:", labelFgPanel)
        row("Label bg:", labelBgPanel)
        row("Concealed fg:", concealedFgPanel)
        row("Concealed bg:", concealedBgPanel)
        row("Match fg:", matchFgPanel)
        row("Match bg:", matchBgPanel)
        row("Nearest fg:", matchNearestFgPanel)
        row("Nearest bg:", matchNearestBgPanel)
        full(previewBox)
        full(searchAcrossSplitsBox)
        full(caseSensitiveBox)
        return panel
    }

    override fun isModified(): Boolean {
        val cfg = LeapConfig.getInstance().state
        return labelsField.text != cfg.labels ||
                safeLabelsField.text != cfg.safeLabels ||
                eqvClassesField.text != cfg.equivalenceClasses ||
                (maxTraversalSpinner.value as Int) != cfg.maxTraversalTargets ||
                previewBox.isSelected != cfg.preview ||
                searchAcrossSplitsBox.isSelected != cfg.searchAcrossSplits ||
                caseSensitiveBox.isSelected != cfg.caseSensitive ||
                labelFgPanel.selectedColor?.toHex() != cfg.labelFg ||
                labelBgPanel.selectedColor?.toHex() != cfg.labelBg ||
                concealedFgPanel.selectedColor?.toHex() != cfg.concealedFg ||
                concealedBgPanel.selectedColor?.toHex() != cfg.concealedBg ||
                matchFgPanel.selectedColor?.toHex() != cfg.matchFg ||
                matchBgPanel.selectedColor?.toHex() != cfg.matchBg ||
                matchNearestFgPanel.selectedColor?.toHex() != cfg.matchNearestFg ||
                matchNearestBgPanel.selectedColor?.toHex() != cfg.matchNearestBg
    }

    override fun apply() {
        val cfg = LeapConfig.getInstance().state
        cfg.labels = labelsField.text.ifBlank { cfg.labels }
        cfg.safeLabels = safeLabelsField.text
        cfg.equivalenceClasses = eqvClassesField.text
        cfg.maxTraversalTargets = maxTraversalSpinner.value as Int
        cfg.preview = previewBox.isSelected
        cfg.searchAcrossSplits = searchAcrossSplitsBox.isSelected
        cfg.caseSensitive = caseSensitiveBox.isSelected
        labelFgPanel.selectedColor?.toHex()?.let { cfg.labelFg = it }
        labelBgPanel.selectedColor?.toHex()?.let { cfg.labelBg = it }
        concealedFgPanel.selectedColor?.toHex()?.let { cfg.concealedFg = it }
        concealedBgPanel.selectedColor?.toHex()?.let { cfg.concealedBg = it }
        matchFgPanel.selectedColor?.toHex()?.let { cfg.matchFg = it }
        matchBgPanel.selectedColor?.toHex()?.let { cfg.matchBg = it }
        matchNearestFgPanel.selectedColor?.toHex()?.let { cfg.matchNearestFg = it }
        matchNearestBgPanel.selectedColor?.toHex()?.let { cfg.matchNearestBg = it }
    }

    override fun reset() {
        val cfg = LeapConfig.getInstance().state
        labelsField.text = cfg.labels
        safeLabelsField.text = cfg.safeLabels
        eqvClassesField.text = cfg.equivalenceClasses
        maxTraversalSpinner.value = cfg.maxTraversalTargets
        previewBox.isSelected = cfg.preview
        searchAcrossSplitsBox.isSelected = cfg.searchAcrossSplits
        caseSensitiveBox.isSelected = cfg.caseSensitive
        labelFgPanel.selectedColor = Color.decode(cfg.labelFg)
        labelBgPanel.selectedColor = Color.decode(cfg.labelBg)
        concealedFgPanel.selectedColor = Color.decode(cfg.concealedFg)
        concealedBgPanel.selectedColor = Color.decode(cfg.concealedBg)
        matchFgPanel.selectedColor = Color.decode(cfg.matchFg)
        matchBgPanel.selectedColor = Color.decode(cfg.matchBg)
        matchNearestFgPanel.selectedColor = Color.decode(cfg.matchNearestFg)
        matchNearestBgPanel.selectedColor = Color.decode(cfg.matchNearestBg)
    }

    private fun Color.toHex() = "#%02x%02x%02x".format(red, green, blue)
}
