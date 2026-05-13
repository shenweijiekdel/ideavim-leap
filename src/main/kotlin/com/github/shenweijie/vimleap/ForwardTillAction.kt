package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent

class ForwardTillAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.FORWARD_TILL, e)
}
