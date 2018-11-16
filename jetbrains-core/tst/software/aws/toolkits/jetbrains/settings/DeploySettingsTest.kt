// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addFileToModule
import software.aws.toolkits.jetbrains.utils.rules.addModule

class DeploySettingsTest {

    @Rule
    @JvmField
    val projectRule = HeavyJavaCodeInsightTestFixtureRule()

    @Test
    fun relativeSamPath() {
        val module = projectRule.fixture.addModule("foo")
        val file = projectRule.fixture.addFileToModule(module, "abc/def/foo.yaml", """foo""")

        assertThat(relativeSamPath(module, file.virtualFile)).isEqualTo("abc/def/foo.yaml")
    }

    @Test
    fun relativeSamPath_root() {
        val module = projectRule.fixture.addModule("foo")
        val file = projectRule.fixture.addFileToModule(module, "foo.yaml", """foo""")

        assertThat(relativeSamPath(module, file.virtualFile)).isEqualTo("foo.yaml")
    }

    @Test
    fun relativeSamPath_null() {
        val fooModule = projectRule.fixture.addModule("foo")
        val barModule = projectRule.fixture.addModule("bar")
        val file = projectRule.fixture.addFileToModule(fooModule, "foo.yaml", """foo""")

        assertThat(relativeSamPath(barModule, file.virtualFile)).isNull()
    }
}