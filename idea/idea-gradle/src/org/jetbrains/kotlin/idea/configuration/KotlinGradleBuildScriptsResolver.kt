/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.util.Key
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.idea.util.NotNullableCopyableDataNodeUserDataProperty
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

var DataNode<out ModuleData>.gradleKotlinBuildScripts
        by NotNullableCopyableDataNodeUserDataProperty(Key.create<List<GradleKotlinBuildScriptModel>>("GRADLE_KOTLIN_BUILD_SCRIPTS"), emptyList())

class KotlinGradleBuildScriptsResolver : AbstractProjectResolverExtension() {
    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinGradleBuildScriptsModelBuilder::class.java)
    }

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(GradleKotlinBuildScriptsModel::class.java)
    }

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        super.populateModuleExtraModels(gradleModule, ideModule)

        resolverCtx.getExtraProject(gradleModule, GradleKotlinBuildScriptsModel::class.java)?.let { gradleModel ->
            ideModule.gradleKotlinBuildScripts = gradleModel.scripts.map {
                // copy object to avoid memory leak (`it` - is java proxy object)
                GradleKotlinBuildScriptModelImpl(it.file, it.classPath, it.sourcePath, it.imports)
            }
        }
    }
}