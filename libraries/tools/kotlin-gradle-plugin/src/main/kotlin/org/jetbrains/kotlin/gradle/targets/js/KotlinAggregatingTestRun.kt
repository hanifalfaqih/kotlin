/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import groovy.lang.Closure
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetTestRun
import org.jetbrains.kotlin.gradle.plugin.KotlinTestRun
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinJsSubTarget
import org.jetbrains.kotlin.gradle.testing.KotlinTestRunWithTask
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport

interface KotlinAggregatingTestRun<T : KotlinTestRun> :
    KotlinTestRun {

    /**
     * Configures all of the test runs aggregated by this test run. If some of the test runs are not yet created up to this point,
     * [configure] will be called on them later, once they are created.
     */
    fun configureEachTestRun(configure: T.() -> Unit): Unit

    /**
     * Returns the aggregated test runs that are already configured up to this moment.
     * Some test runs may be missing from the results if they are not yet configured.
     */
    fun getConfiguredTestRuns(): Iterable<T> =
        mutableListOf<T>().apply {
            var isRunningEagerly = true // avoid modifying the returned list
            configureEachTestRun {
                if (isRunningEagerly) {
                    add(this)
                }
            }
            isRunningEagerly = false
        }

    fun configureEachTestRun(configureClosure: Closure<*>) = configureEachTestRun {
        ConfigureUtil.configureSelf(
            configureClosure,
            this
        )
    }

    override fun filter(configureFilter: TestFilter.() -> Unit) {
        configureEachTestRun { filter(configureFilter) }
    }

}

abstract class KotlinReportAggregatingTestRun<T : KotlinTestRun>(val testRunName: String) :
    KotlinAggregatingTestRun<T>, KotlinTestRunWithTask<KotlinTestReport> {

    override fun getName() = testRunName

    override lateinit var testTask: TaskProvider<KotlinTestReport>

    override fun filter(configureFilter: TestFilter.() -> Unit) = configureEachTestRun { filter(configureFilter) }
}
