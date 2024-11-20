// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class NotificationsList(
    @JsonProperty("schema")
    val schema: Schema,
    @JsonProperty("notifications")
    val notifications: List<NotificationData>?,
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
    val authx: List<AuthxType>?,
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

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT
)
@JsonSubTypes(
    JsonSubTypes.Type(value = NotificationExpression.ComparisonCondition::class, name = "=="),
    JsonSubTypes.Type(value = NotificationExpression.NotEqualsCondition::class, name = "!="),
    JsonSubTypes.Type(value = NotificationExpression.GreaterThanCondition::class, name = ">"),
    JsonSubTypes.Type(value = NotificationExpression.GreaterThanOrEqualsCondition::class, name = ">="),
    JsonSubTypes.Type(value = NotificationExpression.LessThanCondition::class, name = "<"),
    JsonSubTypes.Type(value = NotificationExpression.LessThanOrEqualsCondition::class, name = "<="),
    JsonSubTypes.Type(value = NotificationExpression.AnyOfCondition::class, name = "anyOf"),
    JsonSubTypes.Type(value = NotificationExpression.NotCondition::class, name = "not"),
    JsonSubTypes.Type(value = NotificationExpression.OrCondition::class, name = "or"),
    JsonSubTypes.Type(value = NotificationExpression.AndCondition::class, name = "and"),
    JsonSubTypes.Type(value = NotificationExpression.NoneOfCondition::class, name = "noneOf")
)
sealed class NotificationExpression {
    @JsonDeserialize(using = NotConditionDeserializer::class)
    data class NotCondition(
        val expectedValue: NotificationExpression,
    ) : NotificationExpression()

    @JsonDeserialize(using = OrConditionDeserializer::class)
    data class OrCondition(
        val expectedValueList: List<NotificationExpression>,
    ) : NotificationExpression()

    @JsonDeserialize(using = AndConditionDeserializer::class)
    data class AndCondition(
        val expectedValueList: List<NotificationExpression>,
    ) : NotificationExpression()

    @JsonDeserialize(using = ComplexConditionDeserializer::class)
    data class ComplexCondition(
        val expectedValueList: List<NotificationExpression>,
    ) : NotificationExpression()

    // General class for comparison operators
    @JsonDeserialize(using = OperationConditionDeserializer::class)
    data class OperationCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = ComplexOperationConditionDeserializer::class)
    data class ComplexOperationCondition(
        val value: List<String>,
    ) : NotificationExpression()

    @JsonDeserialize(using = ComparisonConditionDeserializer::class)
    data class ComparisonCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = NotEqualsConditionDeserializer::class)
    data class NotEqualsCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = GreaterThanConditionDeserializer::class)
    data class GreaterThanCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = GreaterThanOrEqualsConditionDeserializer::class)
    data class GreaterThanOrEqualsCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = LessThanConditionDeserializer::class)
    data class LessThanCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = LessThanOrEqualsConditionDeserializer::class)
    data class LessThanOrEqualsCondition(
        val value: String,
    ) : NotificationExpression()

    @JsonDeserialize(using = AnyOfConditionDeserializer::class)
    data class AnyOfCondition(
        val value: List<String>,
    ) : NotificationExpression()

    @JsonDeserialize(using = NoneOfConditionDeserializer::class)
    data class NoneOfCondition(
        val value: List<String>,
    ) : NotificationExpression()
}

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
