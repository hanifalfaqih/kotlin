/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.output

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.psi.UserDataProperty
import java.util.*
import kotlin.math.max

object RightPanelOutputHandler : ScratchOutputHandler {
    override fun onStart(file: ScratchFile) {
//        getToolwindowHandler().onStart(file)
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        if (output.text.isBlank()) return

        printToWindow(file, expression, output)

//        if (output.type == ScratchOutputType.ERROR) {
//            getToolwindowHandler().handle(file, expression, output)
//        }
    }

    override fun error(file: ScratchFile, message: String) {
//        getToolwindowHandler().error(file, message)
    }

    override fun onFinish(file: ScratchFile) {
//        getToolwindowHandler().onFinish(file)
    }

    override fun clear(file: ScratchFile) {
        file.previewEditor.previewOutputBlocksManager?.clear()
        file.previewEditor.previewOutputBlocksManager = null

        TransactionGuard.submitTransaction(file.project, Runnable {
            runWriteAction {
                executeCommand {
                    file.previewEditor.document.setText("")
                }
            }
        })
    }

    private fun printToWindow(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        TransactionGuard.submitTransaction(file.project, Runnable {
            val outputManager = file.previewEditor.previewOutputBlocksManager ?: run {
                val previewOutputManager = PreviewOutputBlocksManager(file.previewEditor.document, file.previewEditor.foldingModel)
                file.previewEditor.previewOutputBlocksManager = previewOutputManager
                previewOutputManager
            }

            val targetCell = outputManager.getBlock(expression) ?: outputManager.addBlockToTheEnd(expression)
            targetCell.addOutput(output)
        })
    }
}

var Editor.previewOutputBlocksManager: PreviewOutputBlocksManager? by UserDataProperty(Key.create("outputManager"))

private val ScratchExpression.height: Int get() = lineEnd - lineStart + 1

class PreviewOutputBlocksManager(private val targetDocument: Document, private val foldingModel: FoldingModel) {

    private val blocks: NavigableMap<ScratchExpression, OutputBlock> = TreeMap(Comparator.comparingInt { it.lineStart })

    val alignments: List<Pair<Int, Int>> get() = blocks.values.map { it.sourceExpression.lineStart to it.lineStart }

    fun getBlock(expression: ScratchExpression): OutputBlock? = blocks[expression]

    fun addBlockToTheEnd(expression: ScratchExpression): OutputBlock = OutputBlock(expression).also {
        if (blocks.putIfAbsent(expression, it) != null) {
            error("There is already a cell for $expression!")
        }
    }

    fun clear() {
        blocks.clear()
    }

    inner class OutputBlock(val sourceExpression: ScratchExpression) {
        private val outputs: MutableList<ScratchOutput> = mutableListOf()

        var lineStart: Int = computeCellLineStart(sourceExpression)
            private set

        val lineEnd: Int get() = lineStart + countNewLines(outputs)
        val height: Int get() = lineEnd - lineStart + 1

        private var foldRegion: FoldRegion? = null

        fun addOutput(output: ScratchOutput) {
            val beforeAdding = lineEnd
            outputs.add(output)

            val currentOutputStartLine = if (outputs.size == 1) lineStart else beforeAdding + 1
            runWriteAction {
                executeCommand {
                    targetDocument.insertStringAtLine(currentOutputStartLine, output.text)
                }
            }

            blocks.tailMap(sourceExpression).values.forEach {
                it.recalculatePosition()
                it.updateFolding()
            }
        }

        private fun recalculatePosition() {
            lineStart = computeCellLineStart(sourceExpression)
        }

        private fun updateFolding() {
            foldingModel.runBatchFoldingOperation {
                foldRegion?.let(foldingModel::removeFoldRegion)

                if (height <= sourceExpression.height) return@runBatchFoldingOperation

                val firstFoldedLine = lineStart + (sourceExpression.height - 1)
                val placeholderLine = "${targetDocument.getLineContent(firstFoldedLine)}..."

                foldRegion = foldingModel.addFoldRegion(
                    targetDocument.getLineStartOffset(firstFoldedLine),
                    targetDocument.getLineEndOffset(lineEnd),
                    placeholderLine
                )

                foldRegion?.isExpanded = isLastCell && isOutputSmall
            }
        }

        private val isLastCell: Boolean get() = false // blocks.higherEntry(sourceExpression) == null
        private val isOutputSmall: Boolean get() = true
    }

    private fun computeCellLineStart(scratchExpression: ScratchExpression): Int {
        val previous = blocks.lowerEntry(scratchExpression)?.value ?: return scratchExpression.lineStart

        val distanceBetweenSources = scratchExpression.lineStart - previous.sourceExpression.lineEnd
        val differenceBetweenSourceAndOutputHeight = previous.sourceExpression.height - previous.height
        val compensation = max(differenceBetweenSourceAndOutputHeight, 0)
        return previous.lineEnd + compensation + distanceBetweenSources
    }
}

private fun countNewLines(list: List<ScratchOutput>) = list.sumBy { StringUtil.countNewLines(it.text) } + max(list.size - 1, 0)

private fun Document.getLineContent(lineNumber: Int) =
    DiffUtil.getLinesContent(this, lineNumber, lineNumber + 1).toString()

fun Document.insertStringAtLine(lineNumber: Int, text: String) {
    while (DiffUtil.getLineCount(this) <= lineNumber) {
        insertString(textLength, "\n")
    }

    insertString(getLineStartOffset(lineNumber), text)
}
