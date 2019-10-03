// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notification

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.extensions.ExtensionPointName
import software.aws.toolkits.resources.message

interface NoticeType {
    val id: String

    // The value persisted to represent that this notice has been suppressed
    fun getSuppressNotificationValue(): String

    // Indicates whether or not a suppressed notice should remain suppressed
    fun isNotificationSuppressed(previousSuppressNotificationValue: String?): Boolean

    fun isNotificationRequired(): Boolean

    // Notification Title/Message
    fun getNoticeContents(): NoticeContents

    companion object {
        val EP_NAME = ExtensionPointName<NoticeType>("aws.toolkit.notice")

        internal fun notices(): List<NoticeType> = EP_NAME.extensions.toList()
    }
}

class JetBrainsMinimumVersionChange : NoticeType {
    override val id: String = "JetBrainsMinimumVersion_192"
    private val noticeContents = NoticeContents(
        message("notice.title.jetbrains.minimum.version.2019.2"),
        message("notice.message.jetbrains.minimum.version.2019.2", ApplicationNamesInfo.getInstance().fullProductName)
    )

    override fun getSuppressNotificationValue(): String = ApplicationInfo.getInstance().fullVersion

    override fun isNotificationSuppressed(previousSuppressNotificationValue: String?): Boolean {
        previousSuppressNotificationValue?.let {
            return previousSuppressNotificationValue == getSuppressNotificationValue()
        }
        return false
    }

    override fun isNotificationRequired(): Boolean {
        val appInfo = ApplicationInfo.getInstance()
        val majorVersion = appInfo.majorVersion.toIntOrNull()
        val minorVersion = appInfo.minorVersion.toIntOrNull()

        majorVersion?.let {
            minorVersion?.let {
                return majorVersion < 2019 || (majorVersion == 2019 && minorVersion < 2)
            }
        }

        return true
    }

    override fun getNoticeContents(): NoticeContents = noticeContents
}

data class NoticeContents(var title: String, var message: String)
