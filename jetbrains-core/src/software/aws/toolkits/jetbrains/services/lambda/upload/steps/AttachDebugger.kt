// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload.steps

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.resolveDebuggerSupport
import software.aws.toolkits.jetbrains.services.lambda.upload.steps.GetPorts.Companion.DEBUG_PORTS
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.MessageEmitter
import software.aws.toolkits.jetbrains.utils.execution.steps.Step
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext

class AttachDebugger(val environment: ExecutionEnvironment, val state: SamRunningState) : Step() {
    override val stepName = ""
    override val hidden = true

    private val edtContext = getCoroutineUiContext()

    override fun execute(context: Context, messageEmitter: MessageEmitter, ignoreCancellation: Boolean) {
        val debugPorts = context.getRequiredAttribute(DEBUG_PORTS)

        state.settings.resolveDebuggerSupport().createDebugProcessAsync(environment, state, state.settings.debugHost, debugPorts)
            .onSuccess {
                runBlocking(edtContext) {
                    val debugManager = XDebuggerManager.getInstance(environment.project)
                    // Requires EDT on some paths, so always requires to be run on EDT
                    debugManager.startSessionAndShowTab(environment.runProfile.name, environment.contentToReuse, it)
                }
            }
    }
}
