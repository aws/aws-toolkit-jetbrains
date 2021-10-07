// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.experiments

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.rules.SystemPropertyHelper
import software.aws.toolkits.core.utils.test.aString

class ToolkitExperimentManagerTest {

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    @JvmField
    @Rule
    val applicationRule = ApplicationRule()

    @JvmField
    @Rule
    val systemPropertyHelper = SystemPropertyHelper()

    @Test
    fun experimentsCanBeEnabledBySystemProperty() {
        val experiment = DummyExperiment()

        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(experiment), disposableRule.disposable)

        assertThat(experiment.isEnabled()).isFalse

        System.setProperty("aws.experiment.${experiment.id}", "")
        assertThat(experiment.isEnabled()).isTrue

        System.setProperty("aws.experiment.${experiment.id}", "true")
        assertThat(experiment.isEnabled()).isTrue

        System.setProperty("aws.experiment.${experiment.id}", "false")
        assertThat(experiment.isEnabled()).isFalse
    }

    @Test
    fun onlyRegisteredExperimentsCanBeEnabled() {
        val registered = DummyExperiment()
        val notRegistred = DummyExperiment()

        val sut = ToolkitExperimentManager.getInstance()
        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(registered), disposableRule.disposable)

        sut.setState(registered, enabled = true)
        sut.setState(notRegistred, enabled = true)

        assertThat(registered.isEnabled()).isTrue
        assertThat(notRegistred.isEnabled()).isFalse
    }

    @Test
    fun hiddenExperimentsAreNotConsideredVisible() {
        val regular = DummyExperiment()
        val hidden = DummyExperiment(hidden = true)

        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(regular, hidden), disposableRule.disposable)

        assertThat(ToolkitExperimentManager.visibileExperiments()).containsOnly(regular)
    }

    @Test
    fun experimentsAreConsideredEqualBasedOnId() {
        val first = DummyExperiment()
        val second = DummyExperiment(id = first.id)

        assertThat(first === second).isFalse
        assertThat(first).isEqualTo(second)
    }
}

class DummyExperiment(id: String = aString(), hidden: Boolean = false) : ToolkitExperiment(id, { "Dummy ($id)" }, { "Dummy Description" }, hidden)
