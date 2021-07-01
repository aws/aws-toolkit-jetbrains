// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.text.SemVer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.deserializeState
import software.aws.toolkits.jetbrains.utils.serializeState
import java.nio.file.Path

class ExecutableSettingsTest {
    @Rule
    @JvmField
    val application = ApplicationRule()

    @Test
    fun `state can be loaded`() {
        val settings = ExecutableSettings.getInstance()

        deserializeState(
            """
                <executables>
                    <option name="value">
                        <map>
                            <entry key="testExecutable">
                                <value>
                                    <ExecutableState path="/some/path" />
                                </value>
                            </entry>
                        </map>
                    </option>
                </executables>
            """,
            settings
        )

        assertThat(settings.getExecutablePath(TestExecutable())).isEqualTo("/some/path")
    }

    @Test
    fun `state can be saved`() {
        val settings = ExecutableSettings.getInstance()
        settings.setExecutablePath(TestExecutable(), "/some/path")

        assertThat(serializeState("executables", settings))
            .isEqualToIgnoringWhitespace(
                """
                    <executables>
                        <option name="value">
                            <map>
                                <entry key="testExecutable">
                                    <value>
                                        <ExecutableState path="/some/path" />
                                    </value>
                                </entry>
                            </map>
                        </option>
                    </executables>
                """.trimIndent()
            )
    }

    class TestExecutable : ExecutableType2<SemanticVersion> {
        override val displayName: String = "Test Executable"
        override val id: String = "testExecutable"

        override fun determineVersion(path: Path) = SemanticVersion(SemVer("1.2.3", 1, 2, 3))

        override fun supportedVersions(): List<VersionRange<SemanticVersion>> = listOf(
            VersionRange(
                SemanticVersion(SemVer("1.0.0", 1, 0, 0)),
                SemanticVersion(SemVer("2.0.0", 2, 0, 0))
            )
        )
    }
}
