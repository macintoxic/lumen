package com.ceutenant.lumen.model

enum class CoverageState { NOT_COVERED, PARTIALLY_COVERED, COVERED }

/**
 * Uma linha rastreada pelo Cobertura: quantas vezes rodou, e — se for um
 * ponto de decisão (`if`/`switch`/`&&`/`||`/ternário) — quantos dos
 * caminhos possíveis (`branch-coverage`) foram exercitados. Uma linha com
 * `hits > 0` mas nem todo caminho testado é "parcialmente coberta": rodou,
 * mas não com todas as condições possíveis (ex.: um `if/else` onde só o
 * `if` foi testado).
 */
data class LineHit(
    val hits: Int,
    val isBranch: Boolean = false,
    /** Ex.: 50 pra `condition-coverage="50% (1/2)"`. Null se a linha não é um ponto de decisão. */
    val branchCoveragePercent: Int? = null,
) {
    val state: CoverageState
        get() = when {
            hits <= 0 -> CoverageState.NOT_COVERED
            isBranch && (branchCoveragePercent ?: 100) < 100 -> CoverageState.PARTIALLY_COVERED
            else -> CoverageState.COVERED
        }
}

/** Cobertura de um único arquivo fonte: número da linha (1-based, como no XML) -> hits. */
typealias FileCoverage = Map<Int, LineHit>

/**
 * Cobertura de uma classe (não de um arquivo — dotCover agrupa por
 * namespace, e uma classe às vezes não é 1:1 com um arquivo, ex.: partial
 * class). [namespace] e [name] vêm do atributo `name` do Cobertura
 * (`Namespace.Tipo`) já sem os tipos aninhados/gerados pelo compilador
 * (lambda, state machine de método async, tipo aninhado de verdade) —
 * tudo isso é dobrado pra dentro do tipo de fora antes de chegar aqui (ver
 * [com.ceutenant.lumen.parser.CoberturaParser.parse]). [absolutePath] é o
 * arquivo onde a classe foi vista (o primeiro, se for partial class) — só
 * usado pra abrir o arquivo certo num duplo-clique.
 */
data class ClassCoverageEntry(
    /** Vazio pra classe no namespace global (sem nenhum `.` no nome do Cobertura). */
    val namespace: String,
    val name: String,
    val absolutePath: String,
    val lines: FileCoverage,
)

/**
 * [originalPath] preserva o casing real do arquivo em disco (pra exibir) —
 * é diferente da chave em [CoverageReport.files], que fica em minúsculo só
 * pra permitir comparar com o caminho do editor sem depender de case. Usado
 * só pra pintar o gutter do editor — a árvore do toolwindow usa [CoverageReport.classes].
 */
data class FileCoverageEntry(
    val originalPath: String,
    val lines: FileCoverage,
)

/**
 * Relatório carregado. [files] é indexado por caminho absoluto normalizado
 * (ver [com.ceutenant.lumen.parser.CoberturaParser.normalize]) — é essa
 * normalização que permite comparar o `filename` do XML (relativo a um
 * `<source>`) com o caminho do arquivo aberto no editor, sem se importar
 * com maiúsculo/minúsculo (Windows é case-insensitive). [classes] é a lista
 * achatada de todas as classes do relatório, pro toolwindow agrupar por
 * namespace.
 */
data class CoverageReport(
    val sourceFile: String,
    val files: Map<String, FileCoverageEntry>,
    val classes: List<ClassCoverageEntry>,
)
