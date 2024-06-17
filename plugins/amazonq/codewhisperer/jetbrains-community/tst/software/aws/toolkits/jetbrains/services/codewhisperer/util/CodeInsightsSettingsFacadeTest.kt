// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.spy
import kotlin.random.Random

@TestApplication
class CodeInsightsSettingsFacadeTest {
    private lateinit var settings: CodeInsightSettings
    private lateinit var sut: CodeInsightsSettingsFacade
    private lateinit var myDisposable: Disposable

    @BeforeEach
    fun setUp() {
        sut = CodeInsightsSettingsFacade()
        settings = spy { CodeInsightSettings() }
        myDisposable = Disposable {}
    }

    @Test
    fun `should revert when parent is disposed test 1`(@TestDisposable disposable: Disposable) {
        Disposer.register(disposable, myDisposable)
        println(settings.hashCode())

        ApplicationManager.getApplication().replaceService(
            CodeInsightSettings::class.java,
            settings,
            disposable
        )

        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue

        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = true
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue

        sut.disableCodeInsightUntil(myDisposable)

        val random = Random(0).nextInt(1, 10)
        repeat(random) {
            assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
            assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse
        }

        Disposer.dispose(myDisposable)

        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue
    }
}
