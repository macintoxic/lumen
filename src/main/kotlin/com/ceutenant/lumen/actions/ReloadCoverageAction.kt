package com.ceutenant.lumen.actions

import com.ceutenant.lumen.service.CoverageService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** Reimporta o coverage.cobertura.xml mais recente do projeto e repinta todos os editores abertos. */
class ReloadCoverageAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val found = project.getService(CoverageService::class.java).reload()

        val message = if (found) {
            "Cobertura recarregada."
        } else {
            "Nenhum coverage.cobertura.xml encontrado no projeto — rode os testes com " +
                "--collect:\"XPlat Code Coverage\" primeiro."
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Lumen")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
