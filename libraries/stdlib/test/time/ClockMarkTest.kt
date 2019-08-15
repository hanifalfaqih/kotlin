/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
@file:UseExperimental(ExperimentalTime::class)
package test.time

import kotlin.test.*
import kotlin.time.*

class ClockMarkTest {

    @Test
    fun adjustment() {
        val clock = TestClock()

        val mark = clock.markNow()
        val markFuture1 = mark + 1.milliseconds
        val markFuture2 = mark - (-1).milliseconds

        val markPast1 = mark - 1.milliseconds
        val markPast2 = markFuture1 + (-2).milliseconds

        clock += 500_000.nanoseconds

        val elapsed = mark.elapsedNow()
        val elapsedFromFuture = elapsed - 1.milliseconds
        val elapsedFromPast = elapsed + 1.milliseconds

        assertEquals(0.5.milliseconds, elapsed)
        assertEquals(elapsedFromFuture, markFuture1.elapsedNow())
        assertEquals(elapsedFromFuture, markFuture2.elapsedNow())

        assertEquals(elapsedFromPast, markPast1.elapsedNow())
        assertEquals(elapsedFromPast, markPast2.elapsedNow())
    }
}