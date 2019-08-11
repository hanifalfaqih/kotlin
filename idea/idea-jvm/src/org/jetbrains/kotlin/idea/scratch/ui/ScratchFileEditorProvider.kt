/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.scratch.ScratchFileLanguageProvider
import org.jetbrains.kotlin.idea.scratch.isKotlinScratch
import org.jetbrains.kotlin.idea.scratch.isKotlinWorksheet
import org.jetbrains.kotlin.idea.scratch.output.previewOutputBlocksManager
import org.jetbrains.kotlin.idea.syncPublisherWithDisposeCheck
import org.jetbrains.kotlin.psi.UserDataProperty

private const val KTS_SCRATCH_EDITOR_PROVIDER: String = "KtsScratchFileEditorProvider"

class ScratchTextEditorWithPreview(
    private val sourceEditor: TextEditor,
    val preview: Editor
) : TextEditorWithPreview(sourceEditor, TextEditorProvider.getInstance().getTextEditor(preview)), TextEditor {
    lateinit var topPanel: ScratchTopPanel
        private set

    init {
        sourceEditor.pairedEditor = this
        configureTwoSideSyncScrollSupport(sourceEditor.editor, preview)
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(preview)
        topPanel.dispose()
        super.dispose()
    }

    override fun canNavigateTo(navigatable: Navigatable) = sourceEditor.canNavigateTo(navigatable)

    override fun navigateTo(navigatable: Navigatable) {
        sourceEditor.navigateTo(navigatable)
    }

    override fun createToolbar(): ActionToolbar? = topPanel.toolbar

    override fun getEditor(): Editor = sourceEditor.editor

    override fun getFile(): VirtualFile? = sourceEditor.file
    fun setTopPanel(scratchTopPanel: ScratchTopPanel) {
        topPanel = scratchTopPanel
    }
}

var TextEditor.pairedEditor: ScratchTextEditorWithPreview? by UserDataProperty<TextEditor, ScratchTextEditorWithPreview>(Key.create("paired.editor"))

class ScratchFileEditorProvider : FileEditorProvider, DumbAware {
    override fun getEditorTypeId(): String = KTS_SCRATCH_EDITOR_PROVIDER

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid) return false
        if (!(file.isKotlinScratch || file.isKotlinWorksheet)) return false
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
        return ScratchFileLanguageProvider.get(psiFile.fileType) != null
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val editor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor

        ScratchFileAutoRunner.addListener(project, editor)

        val textEditor = ScratchTextEditorWithPreview(
            editor,
            EditorFactory.getInstance().let { factory -> factory.createViewer(factory.createDocument("")) }
        )

        val scratchTopPanel = ScratchTopPanel.createPanel(project, file, textEditor) ?: return editor
        textEditor.setTopPanel(scratchTopPanel)
        project.syncPublisherWithDisposeCheck(ScratchPanelListener.TOPIC).panelAdded(scratchTopPanel)

        return textEditor
    }

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private fun configureTwoSideSyncScrollSupport(
    mainEditor: Editor,
    previewEditor: Editor
) {
    val scrollSupport = SyncScrollSupport.TwosideSyncScrollSupport(listOf(mainEditor, previewEditor), object : BaseSyncScrollable() {
        override fun processHelper(helper: ScrollHelper) {
            if (!helper.process(0, 0)) return

            val collectedElements = previewEditor.previewOutputBlocksManager?.alignments ?: return

            for ((left, right) in collectedElements) {
                if (!helper.process(left, right)) return
            }

            helper.process(mainEditor.document.lineCount, previewEditor.document.lineCount)
        }

        override fun isSyncScrollEnabled(): Boolean = true
    })

    val listener = VisibleAreaListener { e ->
        scrollSupport.visibleAreaChanged(e)
    }

    mainEditor.scrollingModel.addVisibleAreaListener(listener)
    previewEditor.scrollingModel.addVisibleAreaListener(listener)
}
