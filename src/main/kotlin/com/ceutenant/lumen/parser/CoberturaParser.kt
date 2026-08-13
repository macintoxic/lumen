package com.ceutenant.lumen.parser

import com.ceutenant.lumen.model.ClassCoverageEntry
import com.ceutenant.lumen.model.CoverageReport
import com.ceutenant.lumen.model.FileCoverageEntry
import com.ceutenant.lumen.model.LineHit
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
        // nome completo da classe "de fora" (ver outerClassName abaixo) ->
        // suas linhas, achatado pro relatório inteiro (dotCover agrupa
        // classe por namespace, não por arquivo — ver CoverageService.summarize()).
        val classLines = linkedMapOf<String, MutableMap<Int, LineHit>>()
        // Primeiro arquivo onde cada classe foi vista, só pra abrir o
        // arquivo certo no duplo-clique (uma partial class pode ter partes
        // em mais de um arquivo; fica só a primeira).
        val classFileKey = mutableMapOf<String, String>()
        // Guarda o caminho com o casing real do disco por chave normalizada
        // (minúscula) — a chave em si não pode ser usada pra exibir porque
        // perde o casing original do arquivo.
        val originalPaths = mutableMapOf<String, String>()

        for (classElement in doc.getElementsByTagName("class").asElementList()) {
            val filename = classElement.getAttribute("filename").takeIf { it.isNotBlank() } ?: continue
            val resolved = resolveSourceFile(sources, filename) ?: continue
            val key = normalize(resolved)
            originalPaths.putIfAbsent(key, resolved.canonicalPathOrAbsolute())
            val lineMap = files.getOrPut(key) { mutableMapOf() }

            // O coverlet separa tipo aninhado/gerado pelo compilador (lambda,
            // state machine de método async, tipo aninhado de verdade) do
            // tipo de fora com "/" no nome (ex.: "TenantKit.Catalog.TenantCatalog/<>c",
            // ".../<CreateAsync>d__7") — dobra tudo pra dentro do tipo de
            // fora, senão a árvore de classes fica cheia de "tipos" que não
            // existem de verdade no código-fonte.
            val outerClassName = classElement.getAttribute("name").substringBefore('/')
            classFileKey.putIfAbsent(outerClassName, key)
            val classLineMap = classLines.getOrPut(outerClassName) { mutableMapOf() }

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
                // essa linha. Mesma regra nos dois mapas (arquivo inteiro e
                // por classe).
                val existing = lineMap[number]
                if (existing == null || candidate.hits > existing.hits) {
                    lineMap[number] = candidate
                }
                val existingClassLine = classLineMap[number]
                if (existingClassLine == null || candidate.hits > existingClassLine.hits) {
                    classLineMap[number] = candidate
                }
            }
        }

        val fileEntries = files.mapValues { (key, lines) -> FileCoverageEntry(originalPaths[key] ?: key, lines) }

        val classEntries = classLines.map { (outerClassName, lines) ->
            // "" (sem namespace) se o nome não tiver "." nenhum — classe no namespace global.
            val namespace = outerClassName.substringBeforeLast('.', missingDelimiterValue = "")
            val shortName = outerClassName.substringAfterLast('.')
            val fileKey = classFileKey.getValue(outerClassName)
            ClassCoverageEntry(namespace, shortName, originalPaths[fileKey] ?: fileKey, lines)
        }.sortedBy { "${it.namespace}.${it.name}" }

        return CoverageReport(xmlFile.absolutePath, fileEntries, classEntries)
    }

    /** canonicalPath resolve o casing real do arquivo no disco (Windows é case-insensitive mas preserva o case gravado); cai pra absolutePath se o arquivo sumir entre o parse e aqui. */
    private fun File.canonicalPathOrAbsolute(): String =
        try {
            canonicalPath
        } catch (e: java.io.IOException) {
            absolutePath
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
