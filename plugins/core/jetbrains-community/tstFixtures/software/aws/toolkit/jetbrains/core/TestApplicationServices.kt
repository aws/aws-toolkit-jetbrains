// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerOrReplaceServiceInstance

/**
 * Returns the application-level test service for [INTERFACE], registering [create] as its implementation if the
 * platform did not already provide one.
 *
 * On <= 2026.1 the `testServiceImplementation` entries in plugin.xml are auto-instantiated in the test sandbox, so the
 * lookup returns that instance unchanged (no behavior change on the currently-passing profiles). On 2026.2 the test
 * sandbox no longer auto-registers those entries, so the lookup returns null; we then register [create] app-scoped,
 * matching the app-scoped lifetime the plugin.xml registration used to have. Per-test state is still reset by the
 * Mock*Rule/Extension owners.
 */
inline fun <reified INTERFACE : Any> getOrRegisterApplicationService(create: () -> INTERFACE): INTERFACE {
    val app = ApplicationManager.getApplication()
    // nullable lookup: instantiates the plugin.xml impl if registered (<=261), else returns null (262)
    app.getService(INTERFACE::class.java)?.let { return it }
    return create().also { app.registerOrReplaceServiceInstance(INTERFACE::class.java, it, app) }
}

/**
 * Project-scoped counterpart to [getOrRegisterApplicationService], for services declared as `projectService` in
 * plugin.xml (e.g. AwsConnectionManager). Same <=261 vs 262 behavior, scoped to [project]'s lifetime.
 */
inline fun <reified INTERFACE : Any> getOrRegisterProjectService(project: Project, create: () -> INTERFACE): INTERFACE {
    project.getService(INTERFACE::class.java)?.let { return it }
    return create().also { project.registerOrReplaceServiceInstance(INTERFACE::class.java, it, project) }
}
