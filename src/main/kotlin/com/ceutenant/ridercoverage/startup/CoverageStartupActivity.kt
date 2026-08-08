package com.ceutenant.ridercoverage.startup

import com.ceutenant.ridercoverage.service.CoverageService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Carrega o relatório de cobertura mais recente assim que o projeto abre, sem precisar de ação manual. */
class CoverageStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(CoverageService::class.java).reload()
    }
}
