package com.ceutenant.lumen.ui

import com.ceutenant.lumen.service.CoverageReloadListener
import com.ceutenant.lumen.service.CoverageService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.treetable.TreeTable
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/** Conteúdo do toolwindow: TreeTable Symbol / Coverage (%) / Uncovered-Total, no estilo dotCover (Solution/Projeto/Namespace/Classe); duplo-clique numa classe abre o arquivo dela. */
class CoveragePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val rootNode = DefaultMutableTreeNode()
    private val treeTableModel = CoverageTreeTableModel(rootNode)
    private val treeTable = TreeTable(treeTableModel)

    init {
        treeTable.tree.isRootVisible = false
        treeTable.tree.showsRootHandles = true
        treeTable.tree.cellRenderer = CoverageTreeCellRenderer()
        treeTable.rowHeight = 22

        treeTable.columnModel.getColumn(0).preferredWidth = 260
        treeTable.columnModel.getColumn(1).apply {
            preferredWidth = 140
            maxWidth = 160
            cellRenderer = CoverageBarCellRenderer()
        }
        treeTable.columnModel.getColumn(2).preferredWidth = 140

        treeTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val row = treeTable.rowAtPoint(e.point)
                val treePath = treeTable.tree.getPathForRow(row) ?: return
                val node = treePath.lastPathComponent as? DefaultMutableTreeNode ?: return
                val classNode = node.userObject as? CoverageNode.ClassNode ?: return
                openFile(classNode.summary.absolutePath)
            }
        })

        add(buildToolbar(), BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(treeTable), BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            CoverageReloadListener.TOPIC,
            CoverageReloadListener {
                ApplicationManager.getApplication().invokeLater { refresh() }
            },
        )

        refresh()
    }

    private fun buildToolbar(): JPanel {
        val group = DefaultActionGroup()
        group.add(object : AnAction(
            "Reload Coverage Report",
            "Reimporta o(s) coverage.cobertura.xml mais recente(s) do projeto",
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                project.getService(CoverageService::class.java).reload()
            }
        })

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        toolbar.targetComponent = this

        val wrapper = JPanel(BorderLayout())
        wrapper.add(toolbar.component, BorderLayout.WEST)
        return wrapper
    }

    private fun refresh() {
        val summary = project.getService(CoverageService::class.java).summarize()

        rootNode.removeAllChildren()
        val solutionNode = DefaultMutableTreeNode(CoverageNode.SolutionNode(summary))
        for (projectSummary in summary.projects) {
            val projectNode = DefaultMutableTreeNode(CoverageNode.ProjectNode(projectSummary))
            for (namespaceSummary in projectSummary.namespaces) {
                val namespaceNode = DefaultMutableTreeNode(CoverageNode.NamespaceNode(namespaceSummary))
                for (classSummary in namespaceSummary.classes) {
                    namespaceNode.add(DefaultMutableTreeNode(CoverageNode.ClassNode(classSummary)))
                }
                projectNode.add(namespaceNode)
            }
            solutionNode.add(projectNode)
        }
        rootNode.add(solutionNode)

        treeTableModel.reload()
        // Expande só até Namespace ficar visível (mesma ideia de antes da
        // árvore ganhar o nível de Classe, só um degrau abaixo agora) —
        // expandAll aqui abriria toda classe de todo namespace de uma vez,
        // ruído em qualquer solution de tamanho real. Expandir Solution e
        // cada Projeto (mas não os próprios Namespaces) já basta pra revelar
        // os Namespaces, que ficam colapsados por padrão até o usuário abrir um.
        treeTable.tree.expandPath(TreePath(solutionNode.path))
        for (i in 0 until solutionNode.childCount) {
            val projectNode = solutionNode.getChildAt(i) as DefaultMutableTreeNode
            treeTable.tree.expandPath(TreePath(projectNode.path))
        }
    }

    private fun openFile(absolutePath: String) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath) ?: return
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    override fun dispose() = Unit
}
