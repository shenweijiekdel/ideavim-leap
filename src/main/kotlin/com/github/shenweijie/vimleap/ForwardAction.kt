package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent

class ForwardAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.FORWARD, e)
}
