/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirReference
import org.jetbrains.kotlin.fir.expressions.FirAssignment
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.transformSingle
import org.jetbrains.kotlin.fir.visitors.FirTransformer

abstract class FirAbstractAssignment(
    psi: PsiElement?,
    final override var rValue: FirExpression,
    final override val operation: FirOperation,
    safe: Boolean = false
) : FirAbstractQualifiedAccess(psi, safe), FirVariableAssignment {

    override var lValue: FirReference
        get() = calleeReference
        set(value) {
            calleeReference = value
        }

    override fun <D> transformRValue(transformer: FirTransformer<D>, data: D): FirAssignment {
        rValue = rValue.transformSingle(transformer, data)
        return this
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        rValue = rValue.transformSingle(transformer, data)
        return super<FirAbstractQualifiedAccess>.transformChildren(transformer, data)
    }
}