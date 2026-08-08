package com.ceutenant.ridercoverage.model

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
 * Relatório carregado, indexado por caminho absoluto normalizado
 * (ver [com.ceutenant.ridercoverage.parser.CoberturaParser.normalize]) —
 * é essa normalização que permite comparar o `filename` do XML (relativo a
 * um `<source>`) com o caminho do arquivo aberto no editor.
 */
data class CoverageReport(
    val sourceFile: String,
    val files: Map<String, FileCoverage>,
)
