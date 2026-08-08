package com.ceutenant.ridercoverage.ui

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.tree.DefaultMutableTreeNode

/** Coluna "Symbol" do TreeTable: ícone + nome, sem a barra (que agora é a própria coluna "Coverage (%)"). */
class CoverageTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: javax.swing.JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject as? CoverageNode ?: return
        icon = node.icon
        val attributes = if (node is CoverageNode.SolutionNode) {
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        } else {
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        append(node.label, attributes)
    }
}
