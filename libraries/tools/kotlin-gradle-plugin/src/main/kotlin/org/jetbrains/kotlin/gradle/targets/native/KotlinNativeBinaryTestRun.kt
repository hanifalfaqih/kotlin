/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native

import org.jetbrains.kotlin.gradle.plugin.KotlinTargetTestRun
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.testing.KotlinTestTaskTestRun

interface KotlinNativeBinaryTestRun : KotlinTargetTestRun {
    /**
     * Sets this test run to use the specified [executable].
     *
     * This overrides other test source selection options offered by `setTestSource*` functions.
     *
     * Throws [IllegalArgumentException] if the [executable] is not produced by the [target] of this test run.
     */
    fun setTestSource(executable: TestExecutable)
}

open class DefaultKotlinNativeTestRun(testRunName: String, target: KotlinNativeTarget) :
    KotlinTestTaskTestRun<KotlinNativeTest>(testRunName, target), KotlinNativeBinaryTestRun {

    override fun setTestSource(executable: TestExecutable) {
        require(executable.target === target) {
            "Expected a test binary of target ${target.name}, " +
                    "got the binary ${executable.name} of target ${executable.target.name}"
        }

        testTask.configure {
            it.executable(executable.linkTask) { executable.outputFile }
        }
    }
}