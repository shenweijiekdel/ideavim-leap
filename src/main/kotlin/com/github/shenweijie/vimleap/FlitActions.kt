package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent

class FlitFAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.FLIT_F, e)
}

class FlitFBackwardAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.FLIT_F_BACKWARD, e)
}

class FlitTAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.FLIT_T, e)
}

class FlitTBackwardAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.start(LeapMode.FLIT_T_BACKWARD, e)
}

class FlitRepeatAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.startFlitRepeat(e, reversed = false)
}

class FlitRepeatBackwardAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.startFlitRepeat(e, reversed = true)
}
