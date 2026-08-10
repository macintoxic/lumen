package com.ceutenant.ridercoverage.ui

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

/**
 * Barra bicolor preenchendo a célula inteira: verde = coberto, rosa = não
 * coberto, percentual escrito por cima — no estilo da coluna "Coverage (%)"
 * do dotCover (Visual Studio).
 */
class CoverageBarCellRenderer : TableCellRenderer {
    private val bar = Bar()

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val node = value as? CoverageNode
        bar.percent = node?.percent ?: 0.0
        bar.bold = node is CoverageNode.SolutionNode
        bar.noData = node?.measured == false
        return bar
    }

    private class Bar : JComponent() {
        var percent: Double = 0.0
        var bold: Boolean = false
        var noData: Boolean = false

        init {
            preferredSize = Dimension(0, 22)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val inset = 2
            val barWidth = width - inset * 2
            val barHeight = height - inset * 2

            if (noData) {
                // Projeto da solution que nunca apareceu em nenhum
                // coverage.cobertura.xml (nenhum teste carregou seu código)
                // — estado neutro, não "0% coberto".
                g2.color = NO_DATA_COLOR
                g2.fillRect(inset, inset, barWidth, barHeight)

                g2.color = NO_DATA_TEXT_COLOR
                g2.font = g2.font.deriveFont(if (bold) Font.BOLD else Font.PLAIN)
                val text = "No data"
                val metrics = g2.fontMetrics
                val x = inset + (barWidth - metrics.stringWidth(text)) / 2
                val y = inset + (barHeight - metrics.height) / 2 + metrics.ascent
                g2.drawString(text, x, y)
                return
            }

            // Fundo inteiro rosa (parte não coberta) e por cima o verde
            // (parte coberta), igual o dotCover — diferente da versão
            // anterior, aqui as duas cores SÃO o dado (não uma trilha neutra).
            g2.color = UNCOVERED_COLOR
            g2.fillRect(inset, inset, barWidth, barHeight)

            val coveredWidth = (barWidth * (percent / 100.0)).toInt().coerceIn(0, barWidth)
            if (coveredWidth > 0) {
                g2.color = COVERED_COLOR
                g2.fillRect(inset, inset, coveredWidth, barHeight)
            }

            g2.color = TEXT_COLOR
            g2.font = g2.font.deriveFont(if (bold) Font.BOLD else Font.PLAIN)
            val text = "%.0f%%".format(percent)
            val metrics = g2.fontMetrics
            val x = inset + (barWidth - metrics.stringWidth(text)) / 2
            val y = inset + (barHeight - metrics.height) / 2 + metrics.ascent
            g2.drawString(text, x, y)
        }
    }

    companion object {
        private val COVERED_COLOR = JBColor(Color(63, 154, 79), Color(63, 134, 79))
        private val UNCOVERED_COLOR = JBColor(Color(240, 200, 200), Color(107, 60, 60))
        private val TEXT_COLOR = JBColor(Color(30, 30, 30), Color(235, 235, 235))
        private val NO_DATA_COLOR = JBColor(Color(225, 225, 225), Color(60, 60, 60))
        private val NO_DATA_TEXT_COLOR = JBColor(Color(90, 90, 90), Color(170, 170, 170))
    }
}
