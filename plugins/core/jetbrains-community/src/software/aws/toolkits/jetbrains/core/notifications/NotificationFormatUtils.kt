// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.annotation.JsonProperty

data class NotificationsList(
    @JsonProperty("schema")
    val schema: Schema,
    @JsonProperty("notifications")
    val notifications: List<NotificationData>,
)

data class Schema(
    @JsonProperty("version")
    val version: String,
)

data class NotificationData(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("schedule")
    val schedule: NotificationSchedule,
    @JsonProperty("severity")
    val severity: String,
    @JsonProperty("condition")
    val condition: NotificationDisplayCondition?,
    @JsonProperty("content")
    val content: NotificationContentDescriptionLocale,
    @JsonProperty("actions")
    val actions: List<NotificationFollowupActions>? = emptyList(),
)

data class NotificationSchedule(
    @JsonProperty("type")
    val type: String,
)

enum class NotificationSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class NotificationContentDescriptionLocale(
    @JsonProperty("en-US")
    val locale: NotificationContentDescription,
)

data class NotificationContentDescription(
    @JsonProperty("title")
    val title: String,
    @JsonProperty("description")
    val description: String,
)

data class NotificationFollowupActions(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("content")
    val content: NotificationFollowupActionsContent,
)

data class NotificationFollowupActionsContent(
    @JsonProperty("en-US")
    val locale: NotificationActionDescription,
)

data class NotificationActionDescription(
    @JsonProperty("title")
    val title: String,
    @JsonProperty("url")
    val url: String?,
)

data class NotificationDisplayCondition(
    @JsonProperty("compute")
    val compute: ComputeType?,
    @JsonProperty("os")
    val os: SystemType?,
    @JsonProperty("ide")
    val ide: SystemType?,
    @JsonProperty("extension")
    val extension: List<ExtensionType>?,
    @JsonProperty("authx")
    val authx: List<AuthxType>,
)

data class ComputeType(
    @JsonProperty("type")
    val type: NotificationExpression?,
    @JsonProperty("architecture")
    val architecture: NotificationExpression?,
)

data class SystemType(
    @JsonProperty("type")
    val type: NotificationExpression?,
    @JsonProperty("version")
    val version: NotificationExpression?,
)

data class ExtensionType(
    @JsonProperty("id")
    val id: String?,
    @JsonProperty("version")
    val version: NotificationExpression?,
)

open class NotificationExpression

open class NotificationOperation : NotificationExpression()

data class NotCondition(
    @JsonProperty("not")
    val expectedValue: NotificationExpression,
) : NotificationExpression()

data class OrCondition(
    @JsonProperty("or")
    val expectedValueList: List<NotificationExpression>,
) : NotificationExpression()

data class AndCondition(
    @JsonProperty("and")
    val expectedValueList: List<NotificationExpression>,
) : NotificationExpression()

data class AuthxType(
    @JsonProperty("feature")
    val feature: String,
    @JsonProperty("type")
    val type: NotificationExpression?,
    @JsonProperty("region")
    val region: NotificationExpression?,
    @JsonProperty("connectionState")
    val connectionState: NotificationExpression?,
    @JsonProperty("ssoscopes")
    val ssoScopes: NotificationExpression?,
)

data class ComparisonCondition(
    @JsonProperty("==")
    val expectedValue: String,
) : NotificationOperation()

data class NotEqualsCondition(
    @JsonProperty("!=")
    val expectedValue: String,
) : NotificationOperation()

data class GreaterThanCondition(
    @JsonProperty(">")
    val expectedValue: String,
) : NotificationOperation()

data class GreaterThanOrEqualsCondition(
    @JsonProperty(">=")
    val expectedValue: String,
) : NotificationOperation()

data class LessThanCondition(
    @JsonProperty("<")
    val expectedValue: String,
) : NotificationOperation()

data class LessThanOrEqualsCondition(
    @JsonProperty("<=")
    val expectedValue: String,
) : NotificationOperation()

data class InCondition(
    @JsonProperty("anyOf")
    val expectedValueList: List<String>,
) : NotificationOperation()

data class NotInCondition(
    @JsonProperty("noneOf")
    val expectedValueList: List<String>,
) : NotificationOperation()
