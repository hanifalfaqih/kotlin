/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.testSourceInfo
import org.jetbrains.kotlin.idea.scratch.*
import org.jetbrains.kotlin.idea.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.StopScratchAction
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.scratch.output.previewOutputBlocksManager
import org.jetbrains.kotlin.idea.syncPublisherWithDisposeCheck
import org.jetbrains.kotlin.psi.UserDataProperty

private const val KTS_SCRATCH_EDITOR_PROVIDER: String = "KtsScratchFileEditorProvider"

class KtsScratchTextEditorWithPreview(
    override val sourceTextEditor: TextEditor,
    override val previewTextEditor: TextEditor
) : TextEditorWithPreview(sourceTextEditor, previewTextEditor), TextEditor, ScratchTopPanel, ScratchPresentation {

    override lateinit var scratchFile: ScratchFile
        private set

    private val previewEditor = previewTextEditor.editor
    private val sourceEditor = sourceTextEditor.editor

    private var moduleChooserAction: ModulesComboBoxAction = ModulesComboBoxAction("Use classpath of module")
    private val isReplCheckbox: ListenableCheckboxAction = ListenableCheckboxAction("Use REPL")
    private val isMakeBeforeRunCheckbox: ListenableCheckboxAction = ListenableCheckboxAction("Make before run")
    private val isInteractiveCheckbox: ListenableCheckboxAction = ListenableCheckboxAction("Interactive mode")
    private var actionsToolbar: ActionToolbar? = null

    init {
        sourceTextEditor.parentEditorWithPreview = this
        configureScroll()
        configureControlsListeners()
    }

    private fun configureScroll() {
        val syncScrollable = object : BaseSyncScrollable() {
            override fun processHelper(helper: ScrollHelper) {
                if (!helper.process(0, 0)) return

                val collectedElements = previewEditor.previewOutputBlocksManager?.alignments ?: return

                for ((left, right) in collectedElements) {
                    if (!helper.process(left, right)) return
                }

                helper.process(sourceEditor.document.lineCount, previewEditor.document.lineCount)
            }

            override fun isSyncScrollEnabled(): Boolean = true
        }

        val scrollSupport = SyncScrollSupport.TwosideSyncScrollSupport(listOf(sourceEditor, previewEditor), syncScrollable)

        val listener = VisibleAreaListener { e ->
            scrollSupport.visibleAreaChanged(e)
        }

        sourceEditor.scrollingModel.addVisibleAreaListener(listener)
        previewEditor.scrollingModel.addVisibleAreaListener(listener)
    }

    private fun configureControlsListeners() {
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
    }

    override fun dispose() {
        scratchFile.replScratchExecutor?.stop()
        scratchFile.compilingScratchExecutor?.stop()
        super.dispose()
    }

    override val selectedLayout: Layout? get() = layout

    override val selectedModule: Module?
        get() = moduleChooserAction.selectedModule

    override fun canNavigateTo(navigatable: Navigatable): Boolean = sourceTextEditor.canNavigateTo(navigatable)

    override fun navigateTo(navigatable: Navigatable) {
        sourceTextEditor.navigateTo(navigatable)
    }

    override fun getEditor(): Editor = sourceEditor

    override fun getFile(): VirtualFile? = sourceTextEditor.file

    override fun createToolbar(): ActionToolbar {
        val currentActionsToolbar = actionsToolbar
        if (currentActionsToolbar != null) return currentActionsToolbar

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

        return ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true).also {
            actionsToolbar = it
        }
    }

    override fun getShowEditorAction(): ToggleAction {
        return TrackingToggleAction(super.getShowEditorAction()) { previous ->
            if (previous == Layout.SHOW_EDITOR_AND_PREVIEW || previous == Layout.SHOW_PREVIEW) {
                clearScratchFileOutputHandler(scratchFile)
            }
        }
    }

    override fun getShowEditorAndPreviewAction(): ToggleAction {
        return TrackingToggleAction(super.getShowEditorAndPreviewAction()) { previous ->
            if (previous == Layout.SHOW_EDITOR) {
                clearScratchFileOutputHandler(scratchFile)
            }
        }
    }

    override fun getShowPreviewAction(): ToggleAction {
        return TrackingToggleAction(super.getShowPreviewAction()) { previous ->
            if (previous == Layout.SHOW_EDITOR) {
                clearScratchFileOutputHandler(scratchFile)
            }
        }
    }

    fun attachScratchFile(file: ScratchFile) {
        scratchFile = file

        setupAutoRunnerListener()
        setupScratchFileOutputHandlers()
        setupModuleChooser()

        changeMakeModuleCheckboxVisibility(false)

        scratchFile.options.let {
            isReplCheckbox.isSelected = it.isRepl
            isMakeBeforeRunCheckbox.isSelected = it.isMakeBeforeRun
            isInteractiveCheckbox.isSelected = it.isInteractiveMode
        }

        notifyScratchFileAttached()
    }

    private fun setupAutoRunnerListener() {
        ScratchFileAutoRunner.addListener(scratchFile.project, sourceTextEditor)
    }

    private fun setupScratchFileOutputHandlers() {
        setupToolbarOutputHandlers()
        setupReplRestarterOutputHandler()
    }

    private fun setupToolbarOutputHandlers() {
        val toolbarHandler = createUpdateToolbarHandler()
        scratchFile.replScratchExecutor?.addOutputHandler(toolbarHandler)
        scratchFile.compilingScratchExecutor?.addOutputHandler(toolbarHandler)
    }

    private fun setupReplRestarterOutputHandler() {
        scratchFile.replScratchExecutor?.addOutputHandler(object : ScratchOutputHandlerAdapter() {
            override fun onFinish(file: ScratchFile) {
                ApplicationManager.getApplication().invokeLater {
                    if (!file.project.isDisposed) {
                        val scratch = file.getPsiFile()
                        if (scratch?.isValid == true) {
                            DaemonCodeAnalyzer.getInstance(scratchFile.project).restart(scratch)
                        }
                    }
                }
            }
        })
    }

    private fun setupModuleChooser() {
        val modulesWithSources = ModuleManager.getInstance(scratchFile.project).modules.filter {
            it.productionSourceInfo() != null || it.testSourceInfo() != null
        }
        moduleChooserAction.setModules(modulesWithSources)
    }

    private fun createUpdateToolbarHandler(): ScratchOutputHandlerAdapter {
        return object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: ScratchFile) {
                updateToolbar()
            }

            override fun onFinish(file: ScratchFile) {
                updateToolbar()
            }
        }
    }

    private fun notifyScratchFileAttached() {
        scratchFile.project.syncPublisherWithDisposeCheck(ScratchPanelListener.TOPIC).panelAdded(this)
    }

    override fun setModule(module: Module) {
        moduleChooserAction.selectedModule = module
    }

    override fun hideModuleSelector() {
        moduleChooserAction.isVisible = false
    }

    override fun addModuleListener(f: (PsiFile, Module?) -> Unit) {
        moduleChooserAction.addOnChangeListener {
            val currentModule = selectedModule

            changeMakeModuleCheckboxVisibility(currentModule != null)

            val psiFile = scratchFile.getPsiFile()
            if (psiFile != null) {
                f(psiFile, currentModule)
            }
        }
    }

    override fun changeMakeModuleCheckboxVisibility(isVisible: Boolean) {
        isMakeBeforeRunCheckbox.isVisible = isVisible
    }

    override fun updateToolbar() {
        ApplicationManager.getApplication().invokeLater {
            actionsToolbar?.updateActionsImmediately()
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

    /**
     * Temporary solution to avoid changing [TextEditorWithPreview] class.
     */
    @TestOnly
    override fun setPreviewEditorEnabled(isEnabled: Boolean) {
        val layout = if (isEnabled) Layout.SHOW_EDITOR_AND_PREVIEW else Layout.SHOW_EDITOR
        val field = TextEditorWithPreview::class.java.declaredFields.find { it.name == "myLayout" } ?: error("Cannot file layout field!")
        field.isAccessible = true
        field.set(this, layout)
    }

    /**
     * This class is needed to track when [TextEditorWithPreview] changes its layout state.
     */
    private inner class TrackingToggleAction(
        private val action: ToggleAction,
        private val onSetSelected: (previous: Layout?) -> Unit
    ) : ToggleAction(
        action.templatePresentation.text,
        action.templatePresentation.description,
        action.templatePresentation.icon
    ) {
        override fun isSelected(e: AnActionEvent): Boolean = action.isSelected(e)

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val previous = layout
            action.setSelected(e, state)
            onSetSelected(previous)
        }
    }
}

var TextEditor.parentEditorWithPreview: KtsScratchTextEditorWithPreview? by UserDataProperty(Key.create("paired.editor"))

class KtsScratchFileEditorProvider : FileEditorProvider, DumbAware {
    override fun getEditorTypeId(): String = KTS_SCRATCH_EDITOR_PROVIDER

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid) return false
        if (!(file.isKotlinScratch || file.isKotlinWorksheet)) return false
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
        return ScratchFileLanguageProvider.get(psiFile.fileType) != null
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val sourceTextEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val previewTextEditor = createPreviewTextEditor(project, parentDisposable = sourceTextEditor)
        val scratchEditor = KtsScratchTextEditorWithPreview(sourceTextEditor, previewTextEditor)

        val scratchFile = createScratchFile(project, file, scratchEditor) ?: return sourceTextEditor

        scratchEditor.attachScratchFile(scratchFile)
        return scratchEditor
    }

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private fun createPreviewTextEditor(project: Project, parentDisposable: Disposable): TextEditor {
    val editorFactory = EditorFactory.getInstance()
    val previewEditor = editorFactory.createViewer(editorFactory.createDocument(""), project)
    Disposer.register(parentDisposable, Disposable { editorFactory.releaseEditor(previewEditor) })

    return TextEditorProvider.getInstance().getTextEditor(previewEditor)
}

private fun createScratchFile(project: Project, virtualFile: VirtualFile, editor: ScratchPresentation): ScratchFile? {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
    return ScratchFileLanguageProvider.get(psiFile.language)?.newScratchFile(project, editor)
}
