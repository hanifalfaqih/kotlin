/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.actions.KOTLIN_WORKSHEET_EXTENSION
import org.jetbrains.kotlin.idea.scratch.ui.KtsScratchTextEditorWithPreview
import org.jetbrains.kotlin.idea.scratch.ui.ScratchTopPanel
import org.jetbrains.kotlin.idea.scratch.ui.parentEditorWithPreview

internal val LOG = Logger.getInstance("#org.jetbrains.kotlin.idea.scratch")
internal fun Logger.printDebugMessage(str: String) {
    if (isDebugEnabled) debug("SCRATCH: $str")
}

val VirtualFile.isKotlinWorksheet: Boolean
    get() = name.endsWith(".$KOTLIN_WORKSHEET_EXTENSION")

val VirtualFile.isKotlinScratch: Boolean
    get() = ScratchFileService.getInstance().getRootType(this) is ScratchRootType

fun getEditorWithScratchPanel(fileManager: FileEditorManager, virtualFile: VirtualFile): Pair<TextEditor, ScratchTopPanel>? {
    val editor = fileManager.getSelectedEditor(virtualFile) as? TextEditor ?: return null
    val scratchTopPanel = editor.getScratchPanel() ?: return null
    return editor to scratchTopPanel
}

fun getScratchPanelFromSelectedEditor(project: Project): ScratchTopPanel? {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
    return TextEditorProvider.getInstance().getTextEditor(editor).getScratchPanel()
}

fun TextEditor.getScratchPanel(): ScratchTopPanel? {
    return if (this is KtsScratchTextEditorWithPreview) topPanel else parentEditorWithPreview?.topPanel
}

fun clearScratchFileOutputHandler(scratchFile: ScratchFile) {
    val psiFile = scratchFile.getPsiFile() ?: return
    ScratchFileLanguageProvider.get(psiFile.language)?.getOutputHandler()?.clear(scratchFile)
}
