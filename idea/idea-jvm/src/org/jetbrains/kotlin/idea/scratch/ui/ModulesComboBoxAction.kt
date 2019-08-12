package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.committed.LabeledComboBoxAction
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.testSourceInfo
import javax.swing.JComponent

class ModulesComboBoxAction(label: String, private val modules: List<Module>) : LabeledComboBoxAction(label) {
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    var selectedModule: Module? = null
        set(value) {
            field = value
            listeners.forEach { it() }
        }

    var isVisible: Boolean = true

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup =
        throw UnsupportedOperationException("Should not be called!")

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val actionGroup = DefaultActionGroup()

        actionGroup.add(ModuleIsNotSelectedAction(ConfigurationModuleSelector.NO_MODULE_TEXT))
        actionGroup.addAll(modules.map { SelectModuleAction(it) })

        return actionGroup
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.apply {
            icon = selectedModule?.let { ModuleType.get(it).icon }
            text = selectedModule?.name ?: ConfigurationModuleSelector.NO_MODULE_TEXT
        }

        e.presentation.isVisible = isVisible
    }

    fun addOnChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private inner class ModuleIsNotSelectedAction(placeholder: String) : DumbAwareAction(placeholder) {
        override fun actionPerformed(e: AnActionEvent) {
            selectedModule = null
        }
    }

    private inner class SelectModuleAction(private val module: Module) : DumbAwareAction(module.name, null, ModuleType.get(module).icon) {
        override fun actionPerformed(e: AnActionEvent) {
            selectedModule = module
        }
    }
}