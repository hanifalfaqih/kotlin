/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.scratch.ScratchFile

interface ScratchPresentation : Disposable {
    val sourceTextEditor: TextEditor
    val previewTextEditor: TextEditor
    val selectedLayout: TextEditorWithPreview.Layout?
    val selectedModule: Module?
}

interface ScratchTopPanel : Disposable {
    val scratchFile: ScratchFile
    fun setModule(module: Module)
    fun hideModuleSelector()
    fun addModuleListener(f: (PsiFile, Module?) -> Unit)
    fun changeMakeModuleCheckboxVisibility(isVisible: Boolean)
    fun updateToolbar()

    @TestOnly
    fun setReplMode(isSelected: Boolean)

    @TestOnly
    fun setMakeBeforeRun(isSelected: Boolean)

    @TestOnly
    fun setInteractiveMode(isSelected: Boolean)

    @TestOnly
    fun isModuleSelectorVisible(): Boolean

    @TestOnly
    fun setPreviewEditorEnabled(isEnabled: Boolean)
}

interface ScratchPanelListener {
    fun panelAdded(panel: ScratchTopPanel)

    companion object {
        val TOPIC = Topic.create("ScratchPanelListener", ScratchPanelListener::class.java)
    }
}
