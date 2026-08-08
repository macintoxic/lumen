package com.ceutenant.ridercoverage.listener

import com.ceutenant.ridercoverage.service.CoverageService
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager

/** Pinta um editor assim que ele abre (se já tiver um relatório carregado) e limpa os highlighters quando fecha. */
class CoverageEditorFactoryListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return

        project.getService(CoverageService::class.java).paintIfLoaded(editor, file.path)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        project.getService(CoverageService::class.java).clear(editor)
    }
}
