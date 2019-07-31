/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestFilter
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

interface KotlinTestRunWithTask<T : Task> : KotlinTestRun {
    val testTask: TaskProvider<T>
}

open class KotlinTestTaskTestRun<T : AbstractTestTask>(
    private val testRunName: String,
    override val target: KotlinTarget
) : KotlinTargetTestRun, KotlinTestRunWithTask<T> {

    override fun getName(): String =
        testRunName

    override lateinit var testTask: TaskProvider<T>
        internal set

    override fun filter(configureFilter: TestFilter.() -> Unit) {
        testTask.configure { task -> configureFilter(task.filter) }
    }
}

internal val KotlinTargetTestRun.testTaskName: String
    get() = lowerCamelCaseName(
        target.disambiguationClassifier,
        name.takeIf { it != KotlinTestRun.DEFAULT_TEST_RUN_NAME },
        AbstractKotlinTargetConfigurator.testTaskNameSuffix
    )

internal fun requireCompilationOfTarget(compilation: KotlinCompilation<*>, target: KotlinTarget) {
    require(compilation.target === target) {
        "Expected a compilation of target ${target.name}, " +
                "got the compilation ${compilation.name} of target ${compilation.target.name}"
    }
}