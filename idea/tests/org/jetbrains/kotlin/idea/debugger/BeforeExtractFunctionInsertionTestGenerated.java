/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("idea/testData/debugger/insertBeforeExtractFunction")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class BeforeExtractFunctionInsertionTestGenerated extends AbstractBeforeExtractFunctionInsertionTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
    }

    public void testAllFilesPresentInInsertBeforeExtractFunction() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/debugger/insertBeforeExtractFunction"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("emptyImportDirective.kt")
    public void testEmptyImportDirective() throws Exception {
        runTest("idea/testData/debugger/insertBeforeExtractFunction/emptyImportDirective.kt");
    }

    @TestMetadata("emptyImportDirective2.kt")
    public void testEmptyImportDirective2() throws Exception {
        runTest("idea/testData/debugger/insertBeforeExtractFunction/emptyImportDirective2.kt");
    }

    @TestMetadata("emptyPackageDirective.kt")
    public void testEmptyPackageDirective() throws Exception {
        runTest("idea/testData/debugger/insertBeforeExtractFunction/emptyPackageDirective.kt");
    }

    @TestMetadata("emptyPackageDirective2.kt")
    public void testEmptyPackageDirective2() throws Exception {
        runTest("idea/testData/debugger/insertBeforeExtractFunction/emptyPackageDirective2.kt");
    }

    @TestMetadata("manyImports.kt")
    public void testManyImports() throws Exception {
        runTest("idea/testData/debugger/insertBeforeExtractFunction/manyImports.kt");
    }
}