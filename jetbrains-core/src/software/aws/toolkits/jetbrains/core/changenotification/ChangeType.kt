// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.changenotification

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.ExtensionPointName
import software.aws.toolkits.resources.message

interface ChangeType {
    val id: String
    fun getNotificationValue(): String

    fun isNotificationSuppressed(previousNotificationValue: String?): Boolean
    fun isNotificationRequired(): Boolean

    fun getNotificationTitle(): String
    fun getNotificationMessage(): String

    companion object {
        val EP_NAME = ExtensionPointName<ChangeType>("aws.toolkit.changenotifier")

        internal fun changes(): List<ChangeType> = EP_NAME.extensions.toList()
    }
}

class JetBrainsMinimumVersionChange : ChangeType {
    override val id: String = "JetBrainsMinimumVersion_192"

    override fun getNotificationValue(): String = ApplicationInfo.getInstance().fullVersion

    override fun isNotificationSuppressed(previousNotificationValue: String?): Boolean {
        previousNotificationValue?.let {
            return previousNotificationValue == getNotificationValue()
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

    override fun getNotificationTitle(): String = message("change.notification.title.jetbrains.minimum.version.2019.2")

    override fun getNotificationMessage(): String = message("change.notification.message.jetbrains.minimum.version.2019.2")
}
