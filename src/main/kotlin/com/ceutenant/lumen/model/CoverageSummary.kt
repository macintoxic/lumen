package com.ceutenant.lumen.model

/** Agregado por linhas rastreadas (não confundir com linhas de código total do arquivo — só o que o Cobertura mediu). */
sealed interface CoverageAggregate {
    val totalLines: Int
    val coveredLines: Int
}

val CoverageAggregate.percent: Double
    get() = if (totalLines == 0) 100.0 else coveredLines * 100.0 / totalLines

data class FileCoverageSummary(
    val absolutePath: String,
    val displayName: String,
    override val totalLines: Int,
    override val coveredLines: Int,
) : CoverageAggregate

data class ProjectCoverageSummary(
    /** Nome da pasta que contém o .csproj (ou a raiz do projeto, se nenhum arquivo pertencer a um .csproj identificável). */
    val name: String,
    val directory: String,
    val files: List<FileCoverageSummary>,
) : CoverageAggregate {
    override val totalLines: Int get() = files.sumOf { it.totalLines }
    override val coveredLines: Int get() = files.sumOf { it.coveredLines }

    /** false = projeto existe na solution mas não apareceu em nenhum coverage.cobertura.xml (nunca foi carregado por um teste) — não confundir com "0% coberto". */
    val measured: Boolean get() = files.isNotEmpty()
}

data class SolutionCoverageSummary(
    val projects: List<ProjectCoverageSummary>,
) : CoverageAggregate {
    override val totalLines: Int get() = projects.sumOf { it.totalLines }
    override val coveredLines: Int get() = projects.sumOf { it.coveredLines }

    /** false = nenhum projeto da solution tem cobertura medida (nenhum coverage.cobertura.xml foi encontrado) — mesmo critério de [ProjectCoverageSummary.measured]. */
    val measured: Boolean get() = projects.any { it.measured }
}
