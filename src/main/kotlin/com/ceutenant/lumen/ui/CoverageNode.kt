package com.ceutenant.lumen.ui

import com.ceutenant.lumen.model.FileCoverageSummary
import com.ceutenant.lumen.model.ProjectCoverageSummary
import com.ceutenant.lumen.model.SolutionCoverageSummary
import com.ceutenant.lumen.model.percent
import com.intellij.icons.AllIcons
import javax.swing.Icon

/** Um nó da árvore do toolwindow — os três níveis pedidos: solution, projeto, arquivo. */
sealed interface CoverageNode {
    val label: String
    val percent: Double
    val totalLines: Int
    val coveredLines: Int
    val icon: Icon

    /** false quando não há cobertura medida (solution sem nenhum coverage.cobertura.xml, ou projeto que nunca apareceu em nenhum relatório) — ver [SolutionCoverageSummary.measured]/[ProjectCoverageSummary.measured]. */
    val measured: Boolean get() = true

    data class SolutionNode(val summary: SolutionCoverageSummary) : CoverageNode {
        override val label get() = "Solution"
        override val percent get() = summary.percent
        override val totalLines get() = summary.totalLines
        override val coveredLines get() = summary.coveredLines
        override val icon: Icon get() = AllIcons.Nodes.Project
        override val measured get() = summary.measured
    }

    data class ProjectNode(val summary: ProjectCoverageSummary) : CoverageNode {
        override val label get() = summary.name
        override val percent get() = summary.percent
        override val totalLines get() = summary.totalLines
        override val coveredLines get() = summary.coveredLines
        override val icon: Icon get() = AllIcons.Nodes.Module
        override val measured get() = summary.measured
    }

    data class FileNode(val summary: FileCoverageSummary) : CoverageNode {
        override val label get() = summary.displayName
        override val percent get() = summary.percent
        override val totalLines get() = summary.totalLines
        override val coveredLines get() = summary.coveredLines
        override val icon: Icon get() = AllIcons.FileTypes.Text
    }
}
