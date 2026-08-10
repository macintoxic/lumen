package com.ceutenant.lumen.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm

/**
 * Detecta "os testes rodaram" observando o próprio filesystem em vez de se
 * integrar com alguma run configuration específica — assim funciona tanto
 * pra teste disparado pelo Rider quanto por `dotnet test` no terminal (ou
 * por uma automação externa qualquer). Qualquer criação/alteração de um
 * `coverage.cobertura.xml` sob o projeto dispara um reload.
 */
@Service(Service.Level.PROJECT)
class CoverageFileWatcherService(private val project: Project) : Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.none { it.path.endsWith(COVERAGE_FILE_NAME) }) return

                    // debounce: dotnet test costuma gerar mais de um evento pro
                    // mesmo arquivo (create, depois write do conteúdo) — espera
                    // um instante pra não recarregar no meio da escrita nem
                    // duplicar o reload à toa.
                    alarm.cancelAllRequests()
                    alarm.addRequest(
                        { project.getService(CoverageService::class.java).reload() },
                        DEBOUNCE_MS,
                    )
                }
            },
        )
    }

    override fun dispose() = Unit

    companion object {
        private const val COVERAGE_FILE_NAME = "coverage.cobertura.xml"
        private const val DEBOUNCE_MS = 500
    }
}
