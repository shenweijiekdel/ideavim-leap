package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent

class BackwardTillAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.BACKWARD_TILL, e)
}
