// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.toolwindow

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.toolwindow.AbstractToolkitToolWindow.Companion.show
import java.util.UUID
import javax.swing.JLabel

class ToolkitToolWindowTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private lateinit var jbToolWindowManager: ToolWindowManager

    @Before
    fun setUp() {
        jbToolWindowManager = ToolWindowManager.getInstance(projectRule.project)
    }

    @Test
    fun canAddAToolWindow() {
        val testToolWindow = aToolkitToolWindow()

        val sut = AbstractToolkitToolWindow.getOrCreateToolWindow(projectRule.project, testToolWindow)

        sut.addTab("World", JLabel().also { it.text = "Hello" })

        val label = (jbToolWindowManager.getToolWindow(testToolWindow.id)?.contentManager?.getContent(0)?.component as? JLabel)

        assertThat(label?.text).isEqualTo("Hello")
    }

    @Test
    fun canRefreshAnExistingToolWindow() {
        val testToolWindow = aToolkitToolWindow()

        val sut = AbstractToolkitToolWindow.getOrCreateToolWindow(projectRule.project, testToolWindow)

        val component = JLabel().also { it.text = "Hello" }
        sut.addTab("Refreshable", component, refresh = { component.text = "World" })

        sut.find("Refreshable")?.show(refresh = true)

        assertThat(component.text).isEqualTo("World")
    }

    @Test
    fun canFindAPreviouslyAddedTab() {
        val testToolWindow = aToolkitToolWindow()

        val sut = AbstractToolkitToolWindow.getOrCreateToolWindow(projectRule.project, testToolWindow)
        val tab = sut.addTab("World", JLabel().also { it.text = "Hello" }, id = "myId")

        assertThat(sut.find("myId")).isSameAs(tab)
    }

    @Test
    fun onlyOneToolWindowCreatedPerType() {
        val testToolWindow = aToolkitToolWindow()

        val sut = AbstractToolkitToolWindow.getOrCreateToolWindow(projectRule.project, testToolWindow)

        assertThat(AbstractToolkitToolWindow.getOrCreateToolWindow(projectRule.project, testToolWindow)).isEqualTo(sut)
    }

    private fun aToolkitToolWindow() = RegisterToolWindowTask(UUID.randomUUID().toString())
}
