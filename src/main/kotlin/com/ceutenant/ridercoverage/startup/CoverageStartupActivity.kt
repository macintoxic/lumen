package com.ceutenant.ridercoverage.startup

import com.ceutenant.ridercoverage.service.CoverageFileWatcherService
import com.ceutenant.ridercoverage.service.CoverageService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Carrega o relatório de cobertura mais recente assim que o projeto abre, e
 * liga o watcher que detecta quando os testes rodam de novo. Serviços de
 * projeto no IntelliJ Platform são lazy — só existem quando alguém pede via
 * getService() — então o watcher precisa ser "tocado" aqui pra realmente
 * começar a escutar; sem isso ele nunca seria instanciado.
 */
class CoverageStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(CoverageFileWatcherService::class.java)
        project.getService(CoverageService::class.java).reload()
    }
}
