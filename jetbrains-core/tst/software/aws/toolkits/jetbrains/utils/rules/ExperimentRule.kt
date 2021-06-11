// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.rules

import com.intellij.openapi.application.Experiments
import com.intellij.testFramework.ApplicationRule
import org.junit.runner.Description

/**
 * Allows a test run to have an experiment enabled, and then restore previous state
 */
class ExperimentRule(private val experimentId: String, private val desiredEnabledState: Boolean = true) : ApplicationRule() {

    private var state: Boolean = false

    override fun before(description: Description) {
        super.before(description)
        state = Experiments.getInstance().isFeatureEnabled(experimentId)
        if (state != desiredEnabledState) {
            Experiments.getInstance().setFeatureEnabled(experimentId, desiredEnabledState)
        }
    }

    override fun after() {
        if (Experiments.getInstance().isFeatureEnabled(experimentId) != state) {
            Experiments.getInstance().setFeatureEnabled(experimentId, state)
        }
    }
}
