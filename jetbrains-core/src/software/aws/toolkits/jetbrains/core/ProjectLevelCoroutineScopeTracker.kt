// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import kotlin.reflect.KClass

class ProjectLevelCoroutineScopeTracker(
    @SuppressWarnings("UnusedProperty")
    private val project: Project
) : Disposable {
    private val scopes: MutableMap<String, ApplicationThreadPoolScope> = mutableMapOf()
    fun applicationThreadPoolScope(coroutineName: String): ApplicationThreadPoolScope =
        scopes.computeIfAbsent(coroutineName) { ApplicationThreadPoolScope(coroutineName, this) }

    override fun dispose() { }

    companion object {
        fun getInstance(project: Project) = ServiceManager.getService(project, ProjectLevelCoroutineScopeTracker::class.java)
    }
}

fun Project.applicationThreadPoolScope(coroutineName: String) = ProjectLevelCoroutineScopeTracker.getInstance(this).applicationThreadPoolScope(coroutineName)

fun Project.applicationThreadPoolScope(clazz: KClass<out Any>) = ProjectLevelCoroutineScopeTracker.getInstance(this).applicationThreadPoolScope(clazz.java.name)
