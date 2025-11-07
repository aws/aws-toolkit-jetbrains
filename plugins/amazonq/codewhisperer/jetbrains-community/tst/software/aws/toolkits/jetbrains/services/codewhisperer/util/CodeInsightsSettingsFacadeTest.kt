// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class CodeInsightsSettingsFacadeTest : HeavyPlatformTestCase() {
    private lateinit var settings: CodeInsightSettings
    private lateinit var sut: CodeInsightsSettingsFacade

    override fun setUp() {
        super.setUp()
        sut = spy(CodeInsightsSettingsFacade())
        settings = spy { CodeInsightSettings() }

        ApplicationManager.getApplication().replaceService(
            CodeInsightSettings::class.java,
            settings,
            testRootDisposable
        )
    }

    fun testDisableCodeInsightUntilShouldRevertWhenParentIsDisposed() {
        val myFakePopup = Disposable {}
        Disposer.register(testRootDisposable, myFakePopup)

        // assume users' enable the following two codeinsight functionalities
        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = true
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue

        // codewhisperer disable them while popup is shown
        sut.disableCodeInsightUntil(myFakePopup)

        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse
        assertThat(sut.pendingRevertCounts).isEqualTo(2)

        // popup is closed and disposed
        Disposer.dispose(myFakePopup)

        // revert changes made by codewhisperer
        verify(sut, times(2)).revertAll()
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue
    }

    fun testRevertAllShouldRevertBackAllChangesMadeByCodewhisperer() {
        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = true
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue

        sut.disableCodeInsightUntil(testRootDisposable)

        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse

        assertThat(sut.pendingRevertCounts).isEqualTo(2)

        sut.revertAll()
        assertThat(sut.pendingRevertCounts).isEqualTo(0)
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue
    }

    fun testDisableCodeInsightUntilShouldAlwaysFlushPendingRevertsBeforeMakingNextChanges() {
        val myFakePopup = Disposable {}
        Disposer.register(testRootDisposable, myFakePopup)

        val myAnotherFakePopup = Disposable {}
        Disposer.register(testRootDisposable, myAnotherFakePopup)

        // assume users' enable the following two codeinsight functionalities
        settings.TAB_EXITS_BRACKETS_AND_QUOTES = true
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = true
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue

        // codewhisperer disable them while popup_1 is shown
        sut.disableCodeInsightUntil(myFakePopup)
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse
        assertThat(sut.pendingRevertCounts).isEqualTo(2)
        verify(sut, times(1)).revertAll()

        // unexpected issue happens and popup_1 is not disposed correctly and popup_2 is created
        sut.disableCodeInsightUntil(myAnotherFakePopup)
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isFalse
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isFalse
        // should still be 2 because previous ones should be reverted before preceding next changes
        assertThat(sut.pendingRevertCounts).isEqualTo(2)
        verify(sut, times(1 + 1)).revertAll()

        Disposer.dispose(myAnotherFakePopup)

        assertThat(sut.pendingRevertCounts).isEqualTo(0)
        verify(sut, times(1 + 1 + 1)).revertAll()
        assertThat(settings.TAB_EXITS_BRACKETS_AND_QUOTES).isTrue
        assertThat(settings.AUTO_POPUP_COMPLETION_LOOKUP).isTrue
    }

    fun testDisposeShouldCallRevertAllToRevertAllChangesMadeByCodeWhisperer() {
        sut.dispose()
        verify(sut).revertAll()
    }
}
