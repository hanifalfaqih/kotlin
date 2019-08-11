/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.FileEditorManagerTestCase
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.codeInsight.AbstractLineMarkersTest
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesManager
import org.jetbrains.kotlin.idea.scratch.AbstractScratchRunActionTest.Companion.configureOptions
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TagsTestDataUtil
import java.io.File

abstract class AbstractScratchLineMarkersTest: FileEditorManagerTestCase() {
    fun doScratchTest(path: String) {
        val fileText = FileUtil.loadFile(File(path))

        val scratchFile = ScratchRootType.getInstance().createScratchFile(
            project,
            "scratch.kts",
            KotlinLanguage.INSTANCE,
            fileText,
            ScratchFileService.Option.create_if_missing
        ) ?: error("Couldn't create scratch file")

        myFixture.openFileInEditor(scratchFile)

        ScriptDependenciesManager.updateScriptDependenciesSynchronously(scratchFile, project)

        val (_, scratchPanel) = getEditorWithScratchPanel(FileEditorManager.getInstance(project), myFixture.file.virtualFile)
            ?: error("Couldn't find scratch panel")

        configureOptions(scratchPanel, fileText, null)

        val project = myFixture.project
        val document = myFixture.editor.document

        val data = ExpectedHighlightingData(document, false, false, false, myFixture.file)
        data.init()

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val markers = doAndCheckHighlighting(document, data, File(path))

        AbstractLineMarkersTest.assertNavigationElements(myFixture.project, myFixture.file as KtFile, markers)
    }

    override fun tearDown() {
        super.tearDown()

        ScratchFileService.getInstance().scratchesMapping.mappings.forEach { file, _ ->
            runWriteAction { file.delete(this) }
        }
    }

    fun doAndCheckHighlighting(
        documentToAnalyze: Document, expectedHighlighting: ExpectedHighlightingData, expectedFile: File
    ): List<LineMarkerInfo<*>> {
        myFixture.doHighlighting()

        val markers = DaemonCodeAnalyzerImpl.getLineMarkers(documentToAnalyze, myFixture.project)

        try {
            expectedHighlighting.checkLineMarkers(markers, documentToAnalyze.text)

            // This is a workaround for sad bug in ExpectedHighlightingData:
            // the latter doesn't throw assertion error when some line markers are expected, but none are present.
            if (FileUtil.loadFile(expectedFile).contains("<lineMarker") && markers.isEmpty()) {
                throw AssertionError("Some line markers are expected, but nothing is present at all")
            }
        } catch (error: AssertionError) {
            try {
                val actualTextWithTestData = TagsTestDataUtil.insertInfoTags(markers, true, myFixture.file.text)
                KotlinTestUtils.assertEqualsToFile(expectedFile, actualTextWithTestData)
            } catch (failure: FileComparisonFailure) {
                throw FileComparisonFailure(
                    error.message + "\n" + failure.message,
                    failure.expected,
                    failure.actual,
                    failure.filePath
                )
            }
        }
        return markers
    }

}