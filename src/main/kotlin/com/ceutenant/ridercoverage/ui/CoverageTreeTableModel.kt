package com.ceutenant.ridercoverage.ui

import com.intellij.ui.treeStructure.treetable.TreeTableModel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Modela as 3 colunas pedidas: Symbol (a própria árvore, coluna 0 — convenção
 * do TreeTable pra saber onde desenhar os handles de expandir/colapsar),
 * Coverage % (renderizada como barra bicolor, ver [CoverageBarCellRenderer])
 * e Uncovered/Total Stmts.
 */
class CoverageTreeTableModel(root: DefaultMutableTreeNode) : DefaultTreeModel(root), TreeTableModel {

    private var tree: JTree? = null

    override fun setTree(tree: JTree?) {
        this.tree = tree
    }

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getColumnName(column: Int): String = COLUMN_NAMES[column]

    override fun getColumnClass(column: Int): Class<*> =
        if (column == 0) TreeTableModel::class.java else String::class.java

    override fun getValueAt(node: Any?, column: Int): Any? {
        val coverageNode = ((node as? DefaultMutableTreeNode)?.userObject as? CoverageNode) ?: return null
        return when (column) {
            1 -> coverageNode // a própria bar renderer decide o que desenhar a partir do node
            2 -> "${coverageNode.totalLines - coverageNode.coveredLines}/${coverageNode.totalLines}"
            else -> null
        }
    }

    override fun isCellEditable(node: Any?, column: Int): Boolean = false

    override fun setValueAt(value: Any?, node: Any?, column: Int) = Unit

    // O resto de TreeModel (getRoot, getChild, getChildCount, isLeaf,
    // getIndexOfChild, valueForPathChanged, addTreeModelListener,
    // removeTreeModelListener) já vem pronto de DefaultTreeModel.

    companion object {
        private val COLUMN_NAMES = arrayOf("Symbol", "Coverage (%)", "Uncovered/Total Stmts.")
    }
}
