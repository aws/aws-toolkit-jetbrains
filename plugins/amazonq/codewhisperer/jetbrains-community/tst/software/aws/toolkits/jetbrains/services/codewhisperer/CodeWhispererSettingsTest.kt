// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.xmlb.XmlSerializer
import io.mockk.junit4.MockKRule
import org.assertj.core.api.Assertions.assertThat
import org.jdom.output.XMLOutputter
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.core.ToolWindowHeadlessManagerImpl
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreActionState
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.status.CodeWhispererStatusBarWidgetFactory
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceToolWindowFactory
import software.aws.toolkits.jetbrains.settings.CodeWhispererConfiguration
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.amazon.q.jetbrains.utils.xmlElement
import kotlin.test.fail

class CodeWhispererSettingsTest : CodeWhispererTestBase() {

    private lateinit var toolWindowHeadlessManager: ToolWindowHeadlessManagerImpl

    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    override fun setUp() {
        super.setUp()
        // Create a mock ToolWindowManager with working implementation of setAvailable() and isAvailable()
        toolWindowHeadlessManager = object : ToolWindowHeadlessManagerImpl(projectRule.project) {
            private val myToolWindows: MutableMap<String, ToolWindow> = HashMap()
            override fun doRegisterToolWindow(id: String): ToolWindow {
                val toolWindow = object : MockToolWindow(projectRule.project) {
                    private var isAvailable = false
                    override fun setAvailable(value: Boolean) {
                        isAvailable = value
                    }

                    override fun isAvailable() = isAvailable
                }
                myToolWindows[id] = toolWindow
                return toolWindow
            }

            override fun getToolWindow(id: String?): ToolWindow? {
                return myToolWindows[id]
            }
        }
        projectRule.project.replaceService(ToolWindowManager::class.java, toolWindowHeadlessManager, disposableRule.disposable)

        ToolWindowManager.getInstance(projectRule.project).registerToolWindow(
            RegisterToolWindowTask(
                id = ProblemsView.ID
            )
        )
        ToolWindowManager.getInstance(projectRule.project).registerToolWindow(
            RegisterToolWindowTask(
                id = CodeWhispererCodeReferenceToolWindowFactory.id
            )
        )
        projectRule.project.service<StatusBarWidgetsManager>().updateWidget(CodeWhispererStatusBarWidgetFactory::class.java)
    }

    @Test
    fun `when isCodeWhispererEnabled is false, user not able to trigger CodeWhisperer manually`() {
        whenever(stateManager.checkActiveCodeWhispererConnectionType(projectRule.project)).thenReturn(CodeWhispererLoginType.Logout)
        assertThat(isCodeWhispererEnabled(projectRule.project)).isFalse
        invokeCodeWhispererService()
        verify(codewhispererService, never()).showRecommendationsInPopup(any(), any(), any())
    }

    @Test
    fun `test disable auto trigger should make user not able to trigger CodeWhisperer automatically`() {
        stateManager.setAutoEnabled(false)
        assertThat(stateManager.isAutoEnabled()).isFalse
        runInEdtAndWait {
            projectRule.fixture.type(':')
            verify(codewhispererService, never()).showRecommendationsInPopup(any(), any(), any())
        }
    }

    @Test
    fun `test CodeWhisperer components should have correct states on initialization with no persistent states`() {
        mockCodeWhispererEnabledStatus(false)
        stateManager.loadState(CodeWhispererExploreActionState())
        CodeWhispererSettings.getInstance().loadState(CodeWhispererConfiguration())

        ProblemsView.getToolWindow(projectRule.project) ?: fail("Problems window not found")
        val codeReferenceWindow = ToolWindowManager.getInstance(projectRule.project).getToolWindow(
            CodeWhispererCodeReferenceToolWindowFactory.id
        ) ?: fail("Code Reference Log window not found")
        val statusBarWidgetFactory = projectRule.project.service<StatusBarWidgetsManager>().getWidgetFactories().firstOrNull {
            it.id == CodeWhispererStatusBarWidgetFactory.ID
        } ?: fail("CodeWhisperer status bar widget not found")

        runInEdtAndWait {
            assertThat(codeReferenceWindow.isAvailable).isFalse
            assertThat(statusBarWidgetFactory.isAvailable(projectRule.project)).isTrue
            assertThat(settingsManager.isIncludeCodeWithReference()).isFalse
        }
    }

    // TODO: update this to be enable on enabling CodeWhisperer
    @Ignore
    @Test
    fun `test accept CodeWhisperer TOS will show CodeWhisperer UI components, and vice-versa`() {
        val problemsWindow = ProblemsView.getToolWindow(projectRule.project) ?: fail("Problems window not found")
        val codeReferenceWindow = ToolWindowManager.getInstance(projectRule.project).getToolWindow(
            CodeWhispererCodeReferenceToolWindowFactory.id
        ) ?: fail("Code Reference Log window not found")
        val statusBarWidgetFactory = projectRule.project.service<StatusBarWidgetsManager>().getWidgetFactories().firstOrNull {
            it.id == CodeWhispererStatusBarWidgetFactory.ID
        } ?: fail("CodeWhisperer status bar widget not found")
        val originalIsIncludeCodeWithReference = settingsManager.isIncludeCodeWithReference()

        runInEdtAndWait {
            assertThat(problemsWindow.contentManager.contentCount).isEqualTo(0)
            assertThat(codeReferenceWindow.isAvailable).isFalse
            assertThat(statusBarWidgetFactory.isAvailable(projectRule.project)).isFalse
            assertThat(settingsManager.isIncludeCodeWithReference()).isEqualTo(originalIsIncludeCodeWithReference)
        }

        runInEdtAndWait {
            assertThat(problemsWindow.contentManager.contentCount).isEqualTo(1)
            assertThat(codeReferenceWindow.isAvailable).isTrue
            assertThat(statusBarWidgetFactory.isAvailable(projectRule.project)).isTrue
            assertThat(settingsManager.isIncludeCodeWithReference()).isEqualTo(true)
        }
    }

    @Test
    fun `serialize settings to ensure backwards compatibility`() {
        val element = xmlElement(
            """
            <component name="CodeWhispererSettings">
  </component>
            """.trimIndent()
        )

        val settings = CodeWhispererSettings.getInstance()
        settings.toggleAutoBuildFeature("project1", true)

        XmlSerializer.serializeInto(settings.state, element)

        val actual = XMLOutputter().outputString(element)
        val expected = "<component name=\"CodeWhispererSettings\">\n" +
            "<option name=\"autoBuildSetting\">" +
            "<map><entry key=\"project1\" value=\"true\" />" +
            "</map>" +
            "</option>" +
            "<option name=\"value\">" +
            "<map><entry key=\"IsIncludeCodeWithReference\" value=\"true\" />" +
            "</map>" +
            "</option>" +
            "</component>"

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `deserialize empty settings to ensure backwards compatibility`() {
        val element = xmlElement(
            """
                <component name="CodeWhispererSettings">
                </component>
                """
        )
        val actual = XmlSerializer.deserialize(element, CodeWhispererConfiguration::class.java)
        assertThat(actual.autoBuildSetting).hasSize(0)
    }

    @Test
    fun `deserialize existing settings to ensure backwards compatibility`() {
        val element = xmlElement(
            """
                <component name="CodeWhispererSettings">
                    <option name="autoBuildSetting">
                        <map>
                            <entry key="project1" value="true" />
                        </map>
                    </option>
                    <option>
                        <map>
                            <entry key="IsIncludeCodeWithReference" value="true" />
                        </map>
                    </option>
                </component>
            """.trimIndent()
        )
        val actual = XmlSerializer.deserialize(element, CodeWhispererConfiguration::class.java)
        assertThat(actual.autoBuildSetting).hasSize(1)
        assertThat(actual.autoBuildSetting["project1"]).isTrue()
    }

    @Test
    fun `context thread count is returned in range`() {
        val sut = CodeWhispererSettings.getInstance()

        mapOf(
            1 to 1,
            0 to 0,
            -1 to 0,
            123 to 50,
            50 to 50,
            51 to 50,
        ).forEach { s, expected ->
            sut.setProjectContextIndexThreadCount(s)
            assertThat(sut.getProjectContextIndexThreadCount()).isEqualTo(expected)
        }
    }

    @Test
    fun `context index size is returned in range`() {
        val sut = CodeWhispererSettings.getInstance()

        mapOf(
            1 to 1,
            0 to 1,
            -1 to 1,
            123 to 123,
            2047 to 2047,
            4096 to 4096,
            4097 to 4096,
        ).forEach { s, expected ->
            sut.setProjectContextIndexMaxSize(s)
            assertThat(sut.getProjectContextIndexMaxSize()).isEqualTo(expected)
        }
    }
}

class CodeWhispererSettingUnitTest {
    private lateinit var sut: CodeWhispererSettings

    @Before
    fun setUp() {
        sut = CodeWhispererSettings()
        sut.loadState(CodeWhispererConfiguration())
    }

    @Test
    fun `projectContext is disabled by default`() {
        assertThat(sut.isProjectContextEnabled()).isFalse
    }

    @Test
    fun `toggleProjectContext should set the value correct`() {
        assertThat(sut.isProjectContextEnabled()).isFalse

        sut.toggleProjectContextEnabled(true)
        assertThat(sut.isProjectContextEnabled()).isTrue

        sut.toggleProjectContextEnabled(false)
        assertThat(sut.isProjectContextEnabled()).isFalse
    }

    @Test
    fun `toggleProjectContext should only set once on users behalf if passive is true`() {
        assertThat(sut.isProjectContextEnabled()).isFalse

        sut.toggleProjectContextEnabled(true, passive = true)
        assertThat(sut.isProjectContextEnabled()).isTrue

        sut.toggleProjectContextEnabled(false, passive = true)
        assertThat(sut.isProjectContextEnabled()).isTrue

        sut.toggleProjectContextEnabled(false, passive = false)
        assertThat(sut.isProjectContextEnabled()).isFalse
    }
}
