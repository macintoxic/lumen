package com.ceutenant.lumen.service

import com.ceutenant.lumen.model.ClassCoverageEntry
import com.ceutenant.lumen.model.ClassCoverageSummary
import com.ceutenant.lumen.model.CoverageReport
import com.ceutenant.lumen.model.CoverageState
import com.ceutenant.lumen.model.FileCoverage
import com.ceutenant.lumen.model.FileCoverageEntry
import com.ceutenant.lumen.model.FileCoverageSummary
import com.ceutenant.lumen.model.LineHit
import com.ceutenant.lumen.model.ProjectCoverageSummary
import com.ceutenant.lumen.model.SolutionCoverageSummary
import com.ceutenant.lumen.parser.CoberturaParser
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
 * Estado de cobertura do projeto: carrega os Cobertura XML mais recentes
 * (um por projeto de teste — ver [findLatestReportFiles]) e pinta/limpa o
 * gutter dos editores abertos, além de servir o resumo agregado por
 * arquivo/projeto/solution pro toolwindow. Um serviço por projeto (não por
 * aplicação) porque "sob a raiz do projeto" só faz sentido por projeto.
 */
@Service(Service.Level.PROJECT)
class CoverageService(private val project: Project) {

    private val logger = Logger.getInstance(CoverageService::class.java)
    private val paintedHighlighters = mutableMapOf<Editor, List<RangeHighlighter>>()

    @Volatile
    var report: CoverageReport? = null
        private set

    /** Acha os coverage.cobertura.xml mais recentes sob a raiz do projeto, carrega, repinta e avisa o toolwindow. Retorna false se não achou nada. */
    fun reload(): Boolean {
        val basePath = project.basePath
        val reportFiles = basePath?.let { findLatestReportFiles(File(it)) }.orEmpty()

        if (reportFiles.isEmpty()) {
            logger.info("Nenhum coverage.cobertura.xml encontrado sob ${basePath ?: "?"}")
            report = null
            repaintAllOpenEditors()
            publishReloaded()
            return false
        }

        val merged = mutableMapOf<String, FileCoverageEntry>()
        for (file in reportFiles) {
            try {
                for ((key, entry) in CoberturaParser.parse(file).files) {
                    // Arquivos diferentes normalmente não se repetem entre
                    // relatórios de projetos de teste distintos, então um
                    // merge raso (por caminho de arquivo) é suficiente.
                    val existing = merged[key]
                    val mergedLines = existing?.lines.orEmpty() + entry.lines
                    val mergedClasses = mergeClasses(existing?.classes.orEmpty(), entry.classes)
                    merged[key] = FileCoverageEntry(entry.originalPath, mergedLines, mergedClasses)
                }
            } catch (e: Exception) {
                logger.warn("Falha lendo $file", e)
            }
        }

        report = CoverageReport(reportFiles.joinToString(";") { it.absolutePath }, merged)
        repaintAllOpenEditors()
        publishReloaded()
        return true
    }

    fun paint(editor: Editor, filePath: String) {
        clear(editor)

        val fileCoverage = report?.files?.get(CoberturaParser.normalize(File(filePath)))?.lines ?: return
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

    /**
     * Agrega o relatório carregado por arquivo -> projeto (pasta com .csproj) -> solution, pro toolwindow.
     * Roda a descoberta de projetos mesmo sem relatório nenhum carregado (report == null) — solution sem
     * nenhum teste rodado ainda deve listar os projetos existentes, só que todos como "não medido", em vez
     * de aparecer vazia com a raiz da solution em 100% (não tem cobertura pra medir 100% de coisa nenhuma).
     */
    fun summarize(): SolutionCoverageSummary {
        val basePath = project.basePath ?: return SolutionCoverageSummary(emptyList())
        val root = File(basePath)
        val byProjectDir = linkedMapOf<String, MutableList<FileCoverageSummary>>()

        for (entry in report?.files?.values.orEmpty()) {
            val file = File(entry.originalPath)
            val covered = entry.lines.values.count { it.hits > 0 }
            val classSummaries = entry.classes.map { classEntry ->
                val classCovered = classEntry.lines.values.count { it.hits > 0 }
                ClassCoverageSummary(classEntry.name, entry.originalPath, classEntry.lines.size, classCovered)
            }
            val fileSummary = FileCoverageSummary(entry.originalPath, file.name, entry.lines.size, covered, classSummaries)
            val projectDir = findProjectDir(file.parentFile ?: root, root) ?: root.path
            byProjectDir.getOrPut(projectDir) { mutableListOf() }.add(fileSummary)
        }

        // Cobertura só cobre o que os testes de fato carregaram — um projeto
        // de produção sem nenhum teste referenciando ele (ex.: uma API sem
        // referência de TenantKit.Tests) nunca aparece em nenhum coverage
        // .cobertura.xml. Sem isso, esses projetos simplesmente somem do
        // painel em vez de aparecer como "não medido".
        for (dir in discoverNonTestProjectDirs(root)) {
            byProjectDir.getOrPut(dir.path) { mutableListOf() }
        }

        val projects = byProjectDir.map { (dir, files) ->
            ProjectCoverageSummary(File(dir).name, dir, files.sortedBy { it.displayName })
        }.sortedBy { it.name }

        return SolutionCoverageSummary(projects)
    }

    private fun publishReloaded() {
        project.messageBus.syncPublisher(CoverageReloadListener.TOPIC).onCoverageReloaded()
    }

    private fun repaintAllOpenEditors() {
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project != project) continue
            val path = FileDocumentManager.getInstance().getFile(editor.document)?.path ?: continue
            paint(editor, path)
        }
    }

    /** Sobe a árvore a partir da pasta do arquivo até achar um .csproj — essa pasta vira o "projeto" no resumo. */
    private fun findProjectDir(startDir: File, root: File): String? {
        val rootPath = root.toPath().normalize()
        var dir: File? = startDir
        while (dir != null && dir.toPath().normalize().startsWith(rootPath)) {
            val hasCsproj = dir.listFiles { f -> f.isFile && f.extension.equals("csproj", ignoreCase = true) }
                ?.isNotEmpty() == true
            if (hasCsproj) return dir.path
            dir = dir.parentFile
        }
        return null
    }

    /** Acha todas as pastas com .csproj sob a raiz da solution, exceto as de projeto de teste — ver [isTestProjectDir]. */
    private fun discoverNonTestProjectDirs(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it.name !in PROJECT_DISCOVERY_EXCLUDED_DIRS }
            .filter { dir ->
                dir.isDirectory && dir.listFiles { f -> f.isFile && f.extension.equals("csproj", ignoreCase = true) }
                    ?.isNotEmpty() == true
            }
            .filterNot { isTestProjectDir(it) }
            .toList()

    /** Convenção .NET: projeto de teste termina em .Tests/.Test, ou o .csproj referencia Microsoft.NET.Test.Sdk. */
    private fun isTestProjectDir(dir: File): Boolean {
        if (dir.name.endsWith("Tests", ignoreCase = true) || dir.name.endsWith("Test", ignoreCase = true)) return true
        val csproj = dir.listFiles { f -> f.isFile && f.extension.equals("csproj", ignoreCase = true) }?.firstOrNull()
            ?: return false
        return runCatching { csproj.readText() }.getOrDefault("").contains("Microsoft.NET.Test.Sdk", ignoreCase = true)
    }

    /**
     * `dotnet test` escreve em `<algumaPasta>/<guid>/coverage.cobertura.xml`,
     * um guid novo por execução — sem limpar os antigos. Agrupa pelo avô (a
     * pasta que não muda a cada execução, ex.: `TenantKit.Tests/TestResults`)
     * e fica só com o mais recente de cada grupo, senão relatórios de
     * execuções passadas de um projeto de teste entrariam junto com os de
     * outro projeto rodado por último e o merge ficaria com dado velho.
     */
    private fun findLatestReportFiles(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it.name !in EXCLUDED_DIRS }
            .filter { it.isFile && it.name == "coverage.cobertura.xml" }
            .toList()
            .groupBy { it.parentFile?.parentFile?.path ?: it.path }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.lastModified() } }

    /** Junta as classes de dois relatórios pro mesmo arquivo (ver [reload]) — mesma classe em ambos tem as linhas somadas, igual ao merge de [FileCoverageEntry.lines]. */
    private fun mergeClasses(existing: List<ClassCoverageEntry>, incoming: List<ClassCoverageEntry>): List<ClassCoverageEntry> {
        val byName = linkedMapOf<String, FileCoverage>()
        for (entry in existing) byName[entry.name] = entry.lines
        for (entry in incoming) byName[entry.name] = byName[entry.name].orEmpty() + entry.lines
        return byName.map { (name, lines) -> ClassCoverageEntry(name, lines) }
    }

    companion object {
        private val EXCLUDED_DIRS = setOf(".git", "node_modules", ".idea")
        private val PROJECT_DISCOVERY_EXCLUDED_DIRS = EXCLUDED_DIRS + setOf("bin", "obj")
        private val COVERED_COLOR = JBColor(Color(198, 239, 206), Color(30, 70, 40))
        private val PARTIALLY_COVERED_COLOR = JBColor(Color(255, 235, 156), Color(90, 74, 20))
        private val NOT_COVERED_COLOR = JBColor(Color(255, 199, 206), Color(90, 30, 35))
    }
}
