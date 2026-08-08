package com.ceutenant.ridercoverage.service

import com.ceutenant.ridercoverage.model.CoverageReport
import com.ceutenant.ridercoverage.model.CoverageState
import com.ceutenant.ridercoverage.parser.CoberturaParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Color
import java.io.File

/**
 * Estado de cobertura do projeto: carrega o Cobertura XML mais recente e
 * pinta/limpa o gutter dos editores abertos. Um serviço por projeto (não por
 * aplicação) porque "mais recente sob a raiz do projeto" só faz sentido por
 * projeto.
 */
@Service(Service.Level.PROJECT)
class CoverageService(private val project: Project) {

    private val logger = Logger.getInstance(CoverageService::class.java)
    private val paintedHighlighters = mutableMapOf<Editor, List<RangeHighlighter>>()

    @Volatile
    var report: CoverageReport? = null
        private set

    /** Acha o coverage.cobertura.xml mais recente sob a raiz do projeto, carrega e repinta. Retorna false se não achou nada. */
    fun reload(): Boolean {
        val basePath = project.basePath
        val latest = basePath?.let { findLatestReportFile(File(it)) }

        if (latest == null) {
            logger.info("Nenhum coverage.cobertura.xml encontrado sob ${basePath ?: "?"}")
            report = null
            repaintAllOpenEditors()
            return false
        }

        report = try {
            CoberturaParser.parse(latest)
        } catch (e: Exception) {
            logger.warn("Falha lendo $latest", e)
            null
        }

        repaintAllOpenEditors()
        return report != null
    }

    fun paint(editor: Editor, filePath: String) {
        clear(editor)

        val fileCoverage = report?.files?.get(CoberturaParser.normalize(File(filePath))) ?: return
        val document = editor.document
        val highlighters = mutableListOf<RangeHighlighter>()

        for ((lineNumber, hit) in fileCoverage) {
            val lineIndex = lineNumber - 1 // Cobertura é 1-based, Document é 0-based
            if (lineIndex < 0 || lineIndex >= document.lineCount) continue

            val attributes = TextAttributes().apply {
                backgroundColor = when (hit.state) {
                    CoverageState.NOT_COVERED -> NOT_COVERED_COLOR
                    CoverageState.PARTIALLY_COVERED -> PARTIALLY_COVERED_COLOR
                    CoverageState.COVERED -> COVERED_COLOR
                }
            }

            highlighters += editor.markupModel.addRangeHighlighter(
                document.getLineStartOffset(lineIndex),
                document.getLineEndOffset(lineIndex),
                HighlighterLayer.SELECTION - 1,
                attributes,
                HighlighterTargetArea.LINES_IN_RANGE,
            )
        }

        paintedHighlighters[editor] = highlighters
    }

    fun clear(editor: Editor) {
        paintedHighlighters.remove(editor)?.forEach { it.dispose() }
    }

    /** Usado pelo listener quando um editor novo abre — path vem do VirtualFile, não precisa recarregar o XML. */
    fun paintIfLoaded(editor: Editor, filePath: String) {
        if (report != null) paint(editor, filePath)
    }

    private fun repaintAllOpenEditors() {
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project != project) continue
            val path = FileDocumentManager.getInstance().getFile(editor.document)?.path ?: continue
            paint(editor, path)
        }
    }

    private fun findLatestReportFile(root: File): File? =
        root.walkTopDown()
            .onEnter { it.name !in EXCLUDED_DIRS }
            .filter { it.isFile && it.name == "coverage.cobertura.xml" }
            .maxByOrNull { it.lastModified() }

    companion object {
        private val EXCLUDED_DIRS = setOf(".git", "node_modules", ".idea")
        private val COVERED_COLOR = JBColor(Color(198, 239, 206), Color(30, 70, 40))
        private val PARTIALLY_COVERED_COLOR = JBColor(Color(255, 235, 156), Color(90, 74, 20))
        private val NOT_COVERED_COLOR = JBColor(Color(255, 199, 206), Color(90, 30, 35))
    }
}
