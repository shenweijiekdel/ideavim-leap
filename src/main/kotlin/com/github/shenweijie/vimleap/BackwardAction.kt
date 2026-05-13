package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent

class BackwardAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.BACKWARD, e)
}
