/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.scratch.ui


import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileLanguageProvider
import org.jetbrains.kotlin.idea.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.StopScratchAction
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter

interface ScratchTopPanel : Disposable {
    val scratchFile: ScratchFile
    val toolbar: ActionToolbar
    fun getModule(): Module?
    fun setModule(module: Module)
    fun hideModuleSelector()
    fun addModuleListener(f: (PsiFile, Module?) -> Unit)

    @TestOnly
    fun setReplMode(isSelected: Boolean)

    @TestOnly
    fun setMakeBeforeRun(isSelected: Boolean)

    @TestOnly
    fun setInteractiveMode(isSelected: Boolean)

    @TestOnly
    fun isModuleSelectorVisible(): Boolean

    fun changeMakeModuleCheckboxVisibility(isVisible: Boolean)
    fun updateToolbar()

    companion object {
        fun createPanel(
            project: Project,
            virtualFile: VirtualFile,
            editor: ScratchTextEditorWithPreview
        ): ScratchTopPanel? {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
            val scratchFile = ScratchFileLanguageProvider.get(psiFile.language)?.newScratchFile(project, editor) ?: return null
            val panel = ActionsScratchTopPanel(scratchFile)

            val toolbarHandler = createUpdateToolbarHandler(panel)
            scratchFile.replScratchExecutor?.addOutputHandler(object : ScratchOutputHandlerAdapter() {
                override fun onFinish(file: ScratchFile) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!file.project.isDisposed) {
                            val scratch = file.getPsiFile()
                            if (scratch?.isValid == true) {
                                DaemonCodeAnalyzer.getInstance(project).restart(scratch)
                            }
                        }
                    }
                }
            })
            scratchFile.replScratchExecutor?.addOutputHandler(toolbarHandler)
            scratchFile.compilingScratchExecutor?.addOutputHandler(toolbarHandler)

            return panel
        }

        private fun createUpdateToolbarHandler(panel: ScratchTopPanel) = object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: ScratchFile) {
                panel.updateToolbar()
            }

            override fun onFinish(file: ScratchFile) {
                panel.updateToolbar()
            }
        }
    }
}

private class ActionsScratchTopPanel(override val scratchFile: ScratchFile) : ScratchTopPanel {
    override fun dispose() {
        scratchFile.replScratchExecutor?.stop()
        scratchFile.compilingScratchExecutor?.stop()
    }

    private val moduleChooserAction: ModulesComboBoxAction = ModulesComboBoxAction("Use classpath of module")

    private val isReplCheckbox: ListenableCheckboxAction = ListenableCheckboxAction("Use REPL")
    private val isMakeBeforeRunCheckbox: ListenableCheckboxAction = ListenableCheckboxAction("Make before run")
    private val isInteractiveCheckbox: ListenableCheckboxAction = ListenableCheckboxAction("Interactive mode")

    private val actionsToolbar: ActionToolbar

    init {
        moduleChooserAction.addOnChangeListener { updateToolbar() }

        isMakeBeforeRunCheckbox.addOnChangeListener {
            scratchFile.saveOptions {
                copy(isMakeBeforeRun = isMakeBeforeRunCheckbox.isSelected)
            }
        }

        isInteractiveCheckbox.addOnChangeListener {
            scratchFile.saveOptions {
                copy(isInteractiveMode = isInteractiveCheckbox.isSelected)
            }
        }

        isReplCheckbox.addOnChangeListener {
            scratchFile.saveOptions {
                copy(isRepl = isReplCheckbox.isSelected)
            }
            if (isReplCheckbox.isSelected) {
                // TODO start REPL process when checkbox is selected to speed up execution
                // Now it is switched off due to KT-18355: REPL process is keep alive if no command is executed
                //scratchFile.replScratchExecutor?.start()
            } else {
                scratchFile.replScratchExecutor?.stop()
            }
        }

        val toolbarGroup = DefaultActionGroup().apply {
            add(RunScratchAction())
            add(StopScratchAction())
            addSeparator()
            add(ClearScratchAction())
            addSeparator()
            add(moduleChooserAction)
            add(isMakeBeforeRunCheckbox)
            add(isInteractiveCheckbox)
            add(isReplCheckbox)
        }

        actionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)

        changeMakeModuleCheckboxVisibility(false)

        scratchFile.options.let {
            isReplCheckbox.isSelected = it.isRepl
            isMakeBeforeRunCheckbox.isSelected = it.isMakeBeforeRun
            isInteractiveCheckbox.isSelected = it.isInteractiveMode
        }
    }

    override val toolbar: ActionToolbar
        get() = actionsToolbar

    override fun getModule(): Module? = moduleChooserAction.selectedModule

    override fun setModule(module: Module) {
        moduleChooserAction.selectedModule = module
    }

    override fun hideModuleSelector() {
        moduleChooserAction.isVisible = false
    }

    override fun addModuleListener(f: (PsiFile, Module?) -> Unit) {
        moduleChooserAction.addOnChangeListener {
            val selectedModule = getModule()

            changeMakeModuleCheckboxVisibility(selectedModule != null)

            val psiFile = scratchFile.getPsiFile()
            if (psiFile != null) {
                f(psiFile, selectedModule)
            }
        }
    }

    @TestOnly
    override fun setReplMode(isSelected: Boolean) {
        isReplCheckbox.isSelected = isSelected
    }

    @TestOnly
    override fun setMakeBeforeRun(isSelected: Boolean) {
        isMakeBeforeRunCheckbox.isSelected = isSelected
    }

    @TestOnly
    override fun setInteractiveMode(isSelected: Boolean) {
        isInteractiveCheckbox.isSelected = isSelected
    }

    @TestOnly
    override fun isModuleSelectorVisible(): Boolean = moduleChooserAction.isVisible

    override fun changeMakeModuleCheckboxVisibility(isVisible: Boolean) {
        isMakeBeforeRunCheckbox.isVisible = isVisible
    }

    override fun updateToolbar() {
        ApplicationManager.getApplication().invokeLater {
            actionsToolbar.updateActionsImmediately()
        }
    }
}

interface ScratchPanelListener {
    fun panelAdded(panel: ScratchTopPanel)

    companion object {
        val TOPIC = Topic.create("ScratchPanelListener", ScratchPanelListener::class.java)
    }
}
