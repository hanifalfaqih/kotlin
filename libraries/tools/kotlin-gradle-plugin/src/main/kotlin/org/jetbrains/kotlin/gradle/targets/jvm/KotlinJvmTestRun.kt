/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.jvm

import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.testing.KotlinTestTaskTestRun
import org.jetbrains.kotlin.gradle.testing.requireCompilationOfTarget
import java.util.concurrent.Callable

open class KotlinJvmTestRun(testRunName: String, target: KotlinTarget) :
    KotlinTestTaskTestRun<KotlinJvmTest>(testRunName, target),
    KotlinCompilationTestRun<KotlinJvmCompilation>,
    KotlinClasspathTestRun {

    override fun setTestSource(classpath: FileCollection, testClassesDirs: FileCollection) {
        testTask.configure {
            it.classpath = classpath
            it.testClassesDirs = testClassesDirs
        }
    }

    /**
     * Select which compilation outputs should be treated as the classpath and test classes.
     *
     * The [KotlinCompilationToRunnableFiles.runtimeDependencyFiles] files of the [classpathCompilations] will be treated as the runtime
     * classpath, but not tests, merged into a single classpath in the specified order.
     *
     * The [KotlinCompilationOutput.allOutputs] of the [KotlinCompilationToRunnableFiles.output] taken from the [testClassesCompilations]
     * will be treated as test classes.
     *
     * This overrides other test source selection options offered by `setTestSource*` functions.
     *
     * Throws [IllegalAccessException] if any of [classpathCompilations] and [testClassesCompilations] don't belong to the
     * [target] of this test run.
     */
    fun setTestSource(
        classpathCompilations: Iterable<KotlinJvmCompilation>,
        testClassesCompilations: Iterable<KotlinJvmCompilation>
    ) {
        classpathCompilations.forEach { requireCompilationOfTarget(it, target) }
        testClassesCompilations.forEach { requireCompilationOfTarget(it, target) }

        val classpath = target.project.files(classpathCompilations.map { it.runtimeDependencyFiles + it.output.allOutputs })
        val testClassesDirs = target.project.files(classpathCompilations.map { it.output.classesDirs })

        setTestSource(classpath, testClassesDirs)
    }

    override fun setTestSource(compilation: KotlinJvmCompilation) {
        requireCompilationOfTarget(compilation, target)

        val project = target.project

        setTestSource(
            project.files(Callable { compilation.runtimeDependencyFiles + compilation.output.classesDirs }),
            compilation.output.classesDirs
        )
    }
}