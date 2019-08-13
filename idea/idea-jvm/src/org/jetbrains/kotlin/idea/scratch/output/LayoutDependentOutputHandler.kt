/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.output

import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ui.TextEditorWithPreview

/**
 * This output handler switches between [noPreviewHandler] and [withPreviewHandler] depending on the state of
 * [ScratchFile.editor] selected layout.
 */
class LayoutDependentOutputHandler(
    private val noPreviewHandler: ScratchOutputHandler,
    private val withPreviewHandler: ScratchOutputHandler
) : ScratchOutputHandler {
    override fun onStart(file: ScratchFile) {
        noPreviewHandler.onStart(file)
        withPreviewHandler.onStart(file)
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        selectTargetHandler(file).handle(file, expression, output)
    }

    override fun error(file: ScratchFile, message: String) {
        selectTargetHandler(file).error(file, message)
    }

    override fun onFinish(file: ScratchFile) {
        noPreviewHandler.onFinish(file)
        withPreviewHandler.onFinish(file)
    }

    override fun clear(file: ScratchFile) {
        noPreviewHandler.clear(file)
        withPreviewHandler.clear(file)
    }

    private fun selectTargetHandler(file: ScratchFile): ScratchOutputHandler {
        return if (file.editor.layout == TextEditorWithPreview.Layout.SHOW_EDITOR) noPreviewHandler else withPreviewHandler
    }
}