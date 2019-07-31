/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationTestRun
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetTestRun
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinJsSubTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.testing.KotlinTestTaskTestRun
import org.jetbrains.kotlin.gradle.testing.requireCompilationOfTarget

open class KotlinJsSubtargetTestRun(testRunName: String, subtarget: KotlinJsSubTarget) :
    KotlinTestTaskTestRun<KotlinJsTest>(testRunName, subtarget.target),
    KotlinCompilationTestRun<KotlinJsCompilation> {

    override fun setTestSource(compilation: KotlinJsCompilation) {
        requireCompilationOfTarget(compilation, target)

        testTask.configure {
            it.compilation = compilation
        }
    }
}

open class KotlinJsReportAggregatingTestRun(
    testRunName: String,
    override val target: KotlinJsTarget
) : KotlinReportAggregatingTestRun<KotlinJsSubtargetTestRun>(testRunName),
    KotlinTargetTestRun,
    KotlinCompilationTestRun<KotlinJsCompilation> {

    override fun setTestSource(compilation: KotlinJsCompilation) {
        configureEachTestRun {
            setTestSource(compilation)
        }
    }

    override fun configureEachTestRun(configure: KotlinJsSubtargetTestRun.() -> Unit) {
        fun KotlinJsSubTarget.getChildTestRun() = testRuns.maybeCreate(testRunName)

        val doConfigureInSubtargets: KotlinJsSubTarget.() -> Unit = {
            configure(getChildTestRun())
        }

        target.whenBrowserConfigured { doConfigureInSubtargets(this as KotlinJsSubTarget) }
        target.whenNodejsConfigured { doConfigureInSubtargets(this as KotlinJsSubTarget) }
    }
}