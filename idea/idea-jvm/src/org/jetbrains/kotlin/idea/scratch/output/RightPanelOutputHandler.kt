/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.output

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.psi.UserDataProperty
import java.util.*
import kotlin.math.min

object RightPanelOutputHandler : ScratchOutputHandler {
    private const val maxLineLength = 120
    private const val maxInsertOffset = 60
    private const val minSpaceCount = 4

    override fun onStart(file: ScratchFile) {
        getToolwindowHandler().onStart(file)
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        if (output.text.isBlank()) return

        printToWindow(file, expression, output)

        if (output.type == ScratchOutputType.ERROR) {
            getToolwindowHandler().handle(file, expression, output)
        }
    }

    override fun error(file: ScratchFile, message: String) {
        getToolwindowHandler().error(file, message)
    }

    override fun onFinish(file: ScratchFile) {
        getToolwindowHandler().onFinish(file)
    }

    override fun clear(file: ScratchFile) {
        file.previewEditor.outputManager?.clear()
        file.previewEditor.outputManager = null

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
            val outputManager = file.previewEditor.outputManager ?: run {
                val previewOutputManager = PreviewOutputManager(file.previewEditor)
                file.previewEditor.outputManager = previewOutputManager
                previewOutputManager
            }

            outputManager.addOutput(expression, output)
//            for ((curr, value) in collected) {
//                for (out in value) {
//                    val linesDifference = curr.lineStart - previewEditorDocument.lineCount
//                    val prefix = if (linesDifference > 0) "\n".repeat(linesDifference + 1) else ""
//
//                    runWriteAction {
//                        executeCommand {
//                            val initialLength = previewEditorDocument.textLength
//                            previewEditorDocument.insertString(initialLength, prefix + out.text)
//                            file.previewEditor.foldingModel.runBatchFoldingOperation {
//                                file.previewEditor.foldingModel
//                                    .addFoldRegion(
//                                        previewEditorDocument.textLength - out.text.length,
//                                        previewEditorDocument.textLength,
//                                        out.text.take(3)
//                                    )
//                                    ?.apply { isExpanded = false }
//                            }
//                        }
//                    }
//                }
//            }
        })
    }
}

var Editor.outputManager: PreviewOutputManager? by UserDataProperty(Key.create("outputManager"))

class OutputBlock(val expression: ScratchExpression, val previewEditorOffset: Int) {
    var foldRegion: FoldRegion? = null
    private val output: MutableList<ScratchOutput> = mutableListOf()
}

class PreviewOutputManager(private val previewEditor: Editor) {
    val collected: NavigableMap<ScratchExpression, MutableList<ScratchOutput>> = TreeMap(Comparator.comparingInt { it.lineStart })
    val collected_: NavigableMap<ScratchExpression, OutputBlock> = TreeMap(Comparator.comparingInt { it.lineStart })

    // TODO-fedochet this method heavily expects that output is passed sequentially from top to bottom of the worksheet
    fun addOutput(expression: ScratchExpression, output: ScratchOutput) {
        val currentExpressionOutput = collected.getOrPut(expression) { mutableListOf() }
        val isFirstOutputForExpression = currentExpressionOutput.isEmpty()
        currentExpressionOutput.add(output)

        val previewEditorDocument = previewEditor.document
        val previousExpression: ScratchExpression? = collected.lowerKey(expression)

        val distanceFromLastPrintedOutput = if (isFirstOutputForExpression) {
            expression.lineStart - (previousExpression?.let { min(it.lineEnd, previewEditorDocument.lineCount - 1) } ?: 0)
        } else {
            0
        }

        val prefix = when {
            distanceFromLastPrintedOutput > 0 -> "\n".repeat(distanceFromLastPrintedOutput)
            isFirstOutputForExpression -> ""
            else -> "\n"
        }

        runWriteAction {
            executeCommand {
                previewEditorDocument.insertString(previewEditorDocument.textLength, prefix + output.text)
            }
        }

        val currentBlockCombined = currentExpressionOutput.joinToString(separator = "\n") { it.text }
        val maximumNumberOfLines = expression.lineEnd - expression.lineStart + 1
        val actualLines = currentBlockCombined.lines()

        if (actualLines.size > maximumNumberOfLines) {
            val foldedLines = actualLines.subList(maximumNumberOfLines - 1, actualLines.size)
            val placeholderLine = foldedLines.first()
            val foldedLinesLength = foldedLines.sumBy { it.length } + foldedLines.size - 1

            val foldingModel = previewEditor.foldingModel
            foldingModel.runBatchFoldingOperation {
                foldingModel.allFoldRegions.forEach {
                    if (it.startOffset >= previewEditorDocument.textLength - foldedLinesLength) {
                        foldingModel.removeFoldRegion(it)
                    }
                }

                val foldRegion = foldingModel.addFoldRegion(
                    previewEditorDocument.textLength - foldedLinesLength,
                    previewEditorDocument.textLength,
                    placeholderLine
                )

                foldRegion?.isExpanded = false
            }
        }
    }

    fun addOutput_(expression: ScratchExpression, output: ScratchOutput) {

    }

    fun clear() {
        collected.clear()
    }
}
