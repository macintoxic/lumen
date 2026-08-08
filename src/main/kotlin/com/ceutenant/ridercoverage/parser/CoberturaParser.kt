package com.ceutenant.ridercoverage.parser

import com.ceutenant.ridercoverage.model.CoverageReport
import com.ceutenant.ridercoverage.model.LineHit
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lê um relatório Cobertura — formato que o `coverlet.collector` gera via
 * `dotnet test --collect:"XPlat Code Coverage"` (já usado no TenantKit, sem
 * precisar de nenhuma configuração nova nos projetos de teste).
 *
 * Cada `<class filename="...">` é relativo a um dos `<source>` declarados no
 * XML — tenta cada um até achar um arquivo que exista de verdade no disco.
 */
object CoberturaParser {

    /** Extrai o "50" de `condition-coverage="50% (1/2)"`. */
    private val PERCENT_PREFIX = Regex("""^(\d+)%""")

    fun parse(xmlFile: File): CoverageReport {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Relatório é gerado localmente pelo próprio dev, não é input
            // externo — mas desliga DOCTYPE/entidades externas por hábito de
            // segurança, custo zero aqui.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = false
        }
        val doc = factory.newDocumentBuilder().parse(xmlFile)

        val sources = doc.getElementsByTagName("source").asElementList()
            .map { it.textContent.trim() }
            .ifEmpty { listOf(xmlFile.parentFile?.absolutePath ?: ".") }

        val files = mutableMapOf<String, MutableMap<Int, LineHit>>()

        for (classElement in doc.getElementsByTagName("class").asElementList()) {
            val filename = classElement.getAttribute("filename").takeIf { it.isNotBlank() } ?: continue
            val resolved = resolveSourceFile(sources, filename) ?: continue
            val lineMap = files.getOrPut(normalize(resolved)) { mutableMapOf() }

            val lineElements = classElement.getElementsByTagName("lines").asElementList()
                .flatMap { it.getElementsByTagName("line").asElementList() }

            for (lineElement in lineElements) {
                val number = lineElement.getAttribute("number").toIntOrNull() ?: continue
                val hits = lineElement.getAttribute("hits").toIntOrNull() ?: 0
                // coverlet escreve "True"/"False" (capitalizado, estilo .NET),
                // não o "true" minúsculo convencional de XML/booleano.
                val isBranch = lineElement.getAttribute("branch").equals("true", ignoreCase = true)
                val branchCoveragePercent = lineElement.getAttribute("condition-coverage")
                    .takeIf { it.isNotBlank() }
                    ?.let { PERCENT_PREFIX.find(it)?.groupValues?.get(1)?.toIntOrNull() }

                val candidate = LineHit(hits, isBranch, branchCoveragePercent)

                // Duas <class> podem cobrir a mesma linha do mesmo arquivo
                // (ex.: partial class, ou o estado gerado pelo compilador pra
                // método async) — fica a entrada com mais hits já vista pra
                // essa linha.
                val existing = lineMap[number]
                if (existing == null || candidate.hits > existing.hits) {
                    lineMap[number] = candidate
                }
            }
        }

        return CoverageReport(xmlFile.absolutePath, files)
    }

    /** Normaliza pra comparação entre plataformas: absoluto, sem `..`/`.`, case-insensitive. */
    fun normalize(file: File): String =
        file.toPath().normalize().toAbsolutePath().toString().lowercase()

    private fun resolveSourceFile(sources: List<String>, filename: String): File? {
        val asIs = File(filename)
        if (asIs.isAbsolute && asIs.exists()) return asIs

        for (source in sources) {
            val candidate = File(source, filename)
            if (candidate.exists()) return candidate
        }
        return null
    }

    private fun NodeList.asElementList(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }
}
