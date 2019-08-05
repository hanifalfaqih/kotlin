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

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBSplitter
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.scratch.*
import org.jetbrains.kotlin.idea.scratch.output.outputManager
import org.jetbrains.kotlin.psi.UserDataProperty
import javax.swing.JComponent

class ScratchFileHook(val project: Project) : ProjectComponent {

    override fun projectOpened() {
        project.messageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, ScratchEditorListener())
    }

    override fun projectClosed() {
        getAllEditorsWithScratchPanel(project).forEach { (editor, _) -> editor.removeScratchPanel() }
    }

    private inner class ScratchEditorListener : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
            if (!isPluggable(file)) return

            val editor = getEditorWithoutScratchPanel(source, file) ?: return

            val previewEditor = configurePreviewEditor(project)
            val editorComponent = editor.editor.component

            editor.editor.pairedPreviewEditor = previewEditor

            Disposer.register(editor, Disposable { EditorFactory.getInstance().releaseEditor(previewEditor) })

            val parent = editorComponent.parent
            parent.remove(editorComponent)

            val jbSplitter = JBSplitter(/*vertical=*/false).apply {
                firstComponent = editorComponent
                secondComponent = previewEditor.component
            }

            parent.add(jbSplitter)

            val scrollSupport = configureTwoSideSyncScrollSupport(editor.editor, previewEditor)

            val listener = VisibleAreaListener { e ->
                scrollSupport.visibleAreaChanged(e)
            }

            editor.editor.scrollingModel.addVisibleAreaListener(listener)
            previewEditor.scrollingModel.addVisibleAreaListener(listener)

            ScratchTopPanel.createPanel(project, file, editor, previewEditor)

            ScratchFileAutoRunner.addListener(project, editor)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {}
    }

    private fun isPluggable(file: VirtualFile): Boolean {
        if (!file.isValid) return false
        if (!(file.isKotlinScratch || file.isKotlinWorksheet)) return false
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
        return ScratchFileLanguageProvider.get(psiFile.fileType) != null
    }
}

private fun configurePreviewEditor(project: Project): Editor {
    val factory = EditorFactory.getInstance()
    val previewEditor = factory.createViewer(factory.createDocument(""), project)

    previewEditor.settings.isLineMarkerAreaShown = false
    previewEditor.settings.isLineNumbersShown = false
    previewEditor.setBorder(null)

    val editorParent = previewEditor.contentComponent.parent
    if (editorParent is JComponent) {
        editorParent.putClientProperty(
            DataManager.CLIENT_PROPERTY_DATA_PROVIDER,
            DataProvider { dataId -> if (CommonDataKeys.HOST_EDITOR.`is`(dataId)) previewEditor else null }
        )
    }

    return previewEditor
}

private fun configureTwoSideSyncScrollSupport(
    mainEditor: Editor,
    previewEditor: Editor
): SyncScrollSupport.TwosideSyncScrollSupport {
    return SyncScrollSupport.TwosideSyncScrollSupport(listOf(mainEditor, previewEditor), object : BaseSyncScrollable() {
        override fun processHelper(helper: ScrollHelper) {
            if (!helper.process(0, 0)) return

            val collectedElements = previewEditor.outputManager?.collected ?: return

            for ((expr, output) in collectedElements) {
                if (!helper.process(expr.lineStart, output.lineStart)) return
                if (!helper.process(expr.lineEnd, output.lineEnd)) return
            }

            helper.process(mainEditor.document.lineCount, previewEditor.document.lineCount)
        }

        override fun isSyncScrollEnabled(): Boolean = true
    })
}

var Editor.pairedPreviewEditor: Editor? by UserDataProperty(Key.create("pairedPreviewEditor"))
    @TestOnly
    get
    private set
