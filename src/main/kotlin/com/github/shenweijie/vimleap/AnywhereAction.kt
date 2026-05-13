package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent

class AnywhereAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.ANYWHERE, e)
}
