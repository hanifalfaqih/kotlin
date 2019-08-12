/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction

class ListenableCheckboxAction(label: String) : CheckboxAction(label) {
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    var isVisible: Boolean = true

    var isSelected: Boolean = false
        set(value) {
            field = value
            listeners.forEach { it() }
        }

    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    override fun setSelected(e: AnActionEvent, newState: Boolean) {
        isSelected = newState
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = isVisible
    }

    fun addOnChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }
}