/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.gradle.GradleKotlinBuildScriptModel
import org.jetbrains.kotlin.idea.core.script.ScriptsCompilationConfigurationUpdater
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.dependencies.ScriptDependencies

class KotlinGradleBuildScriptsDataService : AbstractProjectDataService<GradleSourceSetData, Void>() {
    override fun getTargetDataKey(): Key<GradleSourceSetData> = GradleSourceSetData.KEY

    override fun onSuccessImport(
        imported: MutableCollection<DataNode<GradleSourceSetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModelsProvider
    ) {
        super.onSuccessImport(imported, projectData, project, modelsProvider)

        val scripts = mutableMapOf<String, GradleKotlinBuildScriptModel>()
        for (nodeToImport in imported) {
            val mainModuleData = ExternalSystemApiUtil.findParent(
                nodeToImport,
                ProjectKeys.MODULE
            ) ?: continue

            mainModuleData.gradleKotlinBuildScripts?.forEach { buildScript ->
                scripts[buildScript.file] = buildScript
            }
        }

        scripts.values.forEach { buildScript ->
            val scriptFile = File(buildScript.file)
            val virtualFile = VfsUtil.findFile(scriptFile.toPath(), true)!!

            project.service<ScriptsCompilationConfigurationUpdater>().accept(
                virtualFile,
                ResultWithDiagnostics.Success(
                    ScriptCompilationConfigurationWrapper.FromLegacy(
                        VirtualFileScriptSource(virtualFile),
                        ScriptDependencies(
                            scripts = listOf(scriptFile),
                            classpath = (buildScript.classPath + extra).map { File(it) },
                            sources = buildScript.sourcePath.map { File(it) },
                            imports = buildScript.imports
                        ),
                        virtualFile.findScriptDefinition(project)
                    )
                )
            )
        }
    }

    companion object {
        val extra = listOf(
            "/Users/sergey.rostov/.gradle/wrapper/dists/gradle-4.10.3-bin/31t79e2qsceia4mkbojplrgx/gradle-4.10.3/lib/gradle-kotlin-dsl-1.0-rc-6.jar"
            ,"/Users/sergey.rostov/.gradle/wrapper/dists/gradle-4.10.3-bin/31t79e2qsceia4mkbojplrgx/gradle-4.10.3/lib/gradle-kotlin-dsl-provider-plugins-1.0-rc-6.jar"
            ,"/Users/sergey.rostov/.gradle/wrapper/dists/gradle-4.10.3-bin/31t79e2qsceia4mkbojplrgx/gradle-4.10.3/lib/gradle-kotlin-dsl-tooling-models-1.0-rc-6.jar"
            ,"/Users/sergey.rostov/.gradle/wrapper/dists/gradle-4.10.3-bin/31t79e2qsceia4mkbojplrgx/gradle-4.10.3/lib/gradle-kotlin-dsl-tooling-builders-1.0-rc-6.jar"
            ,"/Users/sergey.rostov/.gradle/wrapper/dists/gradle-4.10.3-bin/31t79e2qsceia4mkbojplrgx/gradle-4.10.3/lib/gradle-core-api-4.10.3.jar"
            ,"/Users/sergey.rostov/.gradle/wrapper/dists/gradle-4.10.3-bin/31t79e2qsceia4mkbojplrgx/gradle-4.10.3/lib/gradle-core-4.10.3.jar"
            ,"/Users/sergey.rostov/.gradle/caches/4.10.3/generated-gradle-jars/gradle-kotlin-dsl-extensions-1.0-rc-6-4.10.3.jar"
        )
    }
}