// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.execution.steps

/**
 * This is the hidden step that is the root of the tree of [Step] in the workflow. The children [topLevelSteps] are ran sequentially.
 */
open class StepWorkflow(private vararg val topLevelSteps: Step) : Step() {
    override val stepName = "StepWorkflow"
    override val hidden = true

    override fun execute(context: Context, messageEmitter: MessageEmitter, ignoreCancellation: Boolean) {
        topLevelSteps.forEach {
            it.run(context, messageEmitter)
        }
    }
}
