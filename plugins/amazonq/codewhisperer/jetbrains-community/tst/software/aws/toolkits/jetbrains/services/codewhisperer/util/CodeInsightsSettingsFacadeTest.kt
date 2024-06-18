// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.spy
import kotlin.random.Random

class CodeInsightsSettingsFacadeTest {
    private lateinit var settings: CodeInsightSettings
    private lateinit var sut: CodeInsightsSettingsFacade

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @BeforeEach
    fun setUp() {
        sut = CodeInsightsSettingsFacade()
        settings = spy { CodeInsightSettings() }
    }

    @Test
    fun `should revert when parent is disposed test 1`(@TestDisposable disposable: Disposable) {
        val myFakePopup = Disposable {}
        Disposer.register(disposable, myFakePopup)

        ApplicationManager.getApplication().replaceService(
            CodeInsightSettings::class.java,
            settings,
            disposable
        )

        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue

        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = true
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue

        sut.disableCodeInsightUntil(myFakePopup)

        val random = Random(0).nextInt(1, 10)
        repeat(random) {
            assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
            assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse
        }

        Disposer.dispose(myFakePopup)

        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue
    }
}
