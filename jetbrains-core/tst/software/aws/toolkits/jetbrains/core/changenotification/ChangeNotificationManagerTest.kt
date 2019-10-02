// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.changenotification

import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ChangeNotificationManagerTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private lateinit var sut: DefaultChangeNotificationManager

    @Before
    fun setupTest() {
        sut = DefaultChangeNotificationManager()
    }

    @Test
    fun noticeDoesNotRequireNotification() {
        val notice = mock<ChangeType>()
        whenever(notice.isNotificationRequired()).thenReturn(false)

        val notices = sut.getRequiredNotices(listOf(notice), projectRule.project)

        assertThat(notices).isEmpty()
    }

    @Test
    fun noticeRequiresNotification() {
        val notice = createSampleNotice(true, false)

        val notices = sut.getRequiredNotices(listOf(notice), projectRule.project)

        assertThat(notices).hasSize(1)
        assertThat(notices).contains(notice)
    }

    @Test
    fun nonSerializedNoticeDoesRequiresNotification() {
        val notice = createSampleNotice(true, true)

        val notices = sut.getRequiredNotices(listOf(notice), projectRule.project)

        assertThat(notices).hasSize(1)
        assertThat(notices).contains(notice)
    }

    @Test
    fun suppressedNoticeDoesNotRequireNotification() {
        val notice = createSampleNotice(true, true)

        sut.loadState(ChangeNotificationStateList(listOf(ChangeNotificationState(notice.id, notice.getNotificationValue()))))
        val notices = sut.getRequiredNotices(listOf(notice), projectRule.project)

        assertThat(notices).isEmpty()
    }

    private fun createSampleNotice(requiresNotification: Boolean, isNotificationSuppressed: Boolean): ChangeType = object : ChangeType {
        override val id: String = "noticeId"
        override fun getNotificationValue(): String = "noticeValue"
        override fun isNotificationSuppressed(previousNotificationValue: String?): Boolean = isNotificationSuppressed
        override fun isNotificationRequired(): Boolean = requiresNotification
        override fun getNoticeContents(): NoticeContents = NoticeContents("title", "message")
    }
}
