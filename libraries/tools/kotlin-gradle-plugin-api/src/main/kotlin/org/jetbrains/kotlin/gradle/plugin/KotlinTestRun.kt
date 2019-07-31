/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import groovy.lang.Closure
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions

interface KotlinTestRun : Named {
    fun filter(configureFilter: TestFilter.() -> Unit)
    fun filter(configureFilter: Closure<*>) = filter { ConfigureUtil.configureSelf(configureFilter, this) }

    companion object {
        const val DEFAULT_TEST_RUN_NAME = "default"
    }
}

interface KotlinTargetTestRun : KotlinTestRun {
    val target: KotlinTarget
}

interface KotlinCompilationTestRun<in T : KotlinCompilationToRunnableFiles<*>> : KotlinTargetTestRun {
    /**
     * Select which compilation outputs should be treated as test classes.
     * The [KotlinCompilationToRunnableFiles.runtimeDependencyFiles] files of the [compilation] will be treated as runtime
     * dependencies, but not tests.
     *
     * This overrides other test source selection options offered by `setTestSource*` functions.
     *
     * Throws [IllegalAccessException] if called on a [compilation] that does not belong to the [target] of this test run.
     */
    fun setTestSource(compilation: T)
}

interface KotlinClasspathTestRun : KotlinTestRun {
    /**
     * Select the exact [classpath] to run the tests. Only the classes from [testClasses] will be treated as tests.
     *
     * This overrides other test source selection options offered by `setTestSource*` functions.
     */
    fun setTestSource(classpath: FileCollection, testClassesDirs: FileCollection)
}