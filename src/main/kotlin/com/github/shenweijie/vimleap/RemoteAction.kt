package com.github.shenweijie.vimleap

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * Remote leap: jump to a target, execute a motion, then return to the origin.
 * The return is triggered on the next mode change back to Normal via LeapHandler.returnFromRemote().
 */
class RemoteAction : BaseAction() {
    override fun actionPerformed(e: AnActionEvent) = LeapHandler.startRemote(e)
}
