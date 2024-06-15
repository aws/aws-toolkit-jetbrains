// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

@TestApplication
class CodeInsightsSettingsFacadeTest {
    private val settings = CodeInsightSettings.getInstance()
    private lateinit var sut: CodeInsightsSettingsFacade
    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUp() {
        sut = CodeInsightsSettingsFacade()
        disposable = Disposable { }
    }

    @Test
    fun t() {
        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue

        settings.TAB_EXITS_BRACKETS_AND_QUOTES = false
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
    }

    @Test
    fun `should revert when parent is disposed test 1`() {
        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue

        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = true
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue

        sut.disableCodeInsightUntil(disposable)

        val random = Random(0).nextInt(1,10)
        repeat(random) {
            assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
            assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse
        }

        Disposer.dispose(disposable)

        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue
    }
}
