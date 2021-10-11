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
    fun `experiments can be enabled by system property`() {
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
    fun `only registered experiments can be enabled`() {
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
    fun `hidden experiments are not considered visible`() {
        val regular = DummyExperiment()
        val hidden = DummyExperiment(hidden = true)

        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(regular, hidden), disposableRule.disposable)

        assertThat(ToolkitExperimentManager.visibileExperiments()).containsOnly(regular)
    }

    @Test
    fun `experiments can be enabled by default`() {
        val experiment = DummyExperiment(default = true)
        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(experiment), disposableRule.disposable)
        assertThat(experiment.isEnabled()).isTrue
    }

    @Test
    fun `experiments enabled by default can be disabled`() {
        val experiment = DummyExperiment(default = true)
        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(experiment), disposableRule.disposable)
        experiment.setState(false)
        assertThat(experiment.isEnabled()).isFalse
    }

    @Test
    fun `state only stored if it differs from default, allowing a previously released experiment to become enabled by default`() {
        val experiment = DummyExperiment()
        ExtensionTestUtil.maskExtensions(ToolkitExperimentManager.EP_NAME, listOf(experiment), disposableRule.disposable)
        experiment.setState(true)

        assertThat(ToolkitExperimentManager.getInstance().state).containsEntry(experiment.id, true)
        experiment.setState(false)
        assertThat(ToolkitExperimentManager.getInstance().state).doesNotContainKey(experiment.id)
    }

    @Test
    fun `experiments are considered equal based on id`() {
        val first = DummyExperiment()
        val second = DummyExperiment(id = first.id)

        assertThat(first === second).isFalse
        assertThat(first).isEqualTo(second)
    }
}

class DummyExperiment(id: String = aString(), hidden: Boolean = false, default: Boolean = false) :
    ToolkitExperiment(id, { "Dummy ($id)" }, { "Dummy Description" }, hidden, default)
