// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class NotificationsList(
    val schema: Schema,
    val notifications: List<NotificationData>?,
)

data class Schema(
    val version: String,
)

data class NotificationData(
    val id: String,
    val schedule: NotificationSchedule,
    val severity: String,
    val condition: NotificationDisplayCondition?,
    val content: NotificationContentDescriptionLocale,
    val actions: List<NotificationFollowupActions>? = emptyList(),
)

data class NotificationSchedule(
    @JsonDeserialize(using = NotificationTypeDeserializer::class)
    val type: NotificationScheduleType,
) {
    constructor(type: String) : this(NotificationScheduleType.fromString(type))
}

enum class NotificationSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

enum class NotificationScheduleType {
    STARTUP,
    EMERGENCY,
    ;

    companion object {
        fun fromString(value: String): NotificationScheduleType =
            when (value.lowercase()) {
                "startup" -> STARTUP
                else -> EMERGENCY
            }
    }
}

data class NotificationContentDescriptionLocale(
    @JsonProperty("en-US")
    val locale: NotificationContentDescription,
)

data class NotificationContentDescription(
    val title: String,
    val description: String,
)

data class NotificationFollowupActions(
    val type: String,
    val content: NotificationFollowupActionsContent,
)

data class NotificationFollowupActionsContent(
    @JsonProperty("en-US")
    val locale: NotificationActionDescription,
)

data class NotificationActionDescription(
    val title: String,
    val url: String?,
)

data class NotificationDisplayCondition(
    val compute: ComputeType?,
    val os: SystemType?,
    val ide: SystemType?,
    val extension: List<ExtensionType>?,
    val authx: List<AuthxType>?,
)

data class ComputeType(
    val type: NotificationExpression?,
    val architecture: NotificationExpression?,
)

data class SystemType(
    val type: NotificationExpression?,
    val version: NotificationExpression?,
)

data class ExtensionType(
    val id: String?,
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
sealed interface NotificationExpression {
    @JsonDeserialize(using = NotConditionDeserializer::class)
    data class NotCondition(
        val expectedValue: NotificationExpression,
    ) : NotificationExpression

    @JsonDeserialize(using = OrConditionDeserializer::class)
    data class OrCondition(
        val expectedValueList: List<NotificationExpression>,
    ) : NotificationExpression

    @JsonDeserialize(using = AndConditionDeserializer::class)
    data class AndCondition(
        val expectedValueList: List<NotificationExpression>,
    ) : NotificationExpression

    @JsonDeserialize(using = ComplexConditionDeserializer::class)
    data class ComplexCondition(
        val expectedValueList: List<NotificationExpression>,
    ) : NotificationExpression

    // General class for comparison operators
    @JsonDeserialize(using = OperationConditionDeserializer::class)
    data class OperationCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = ComplexOperationConditionDeserializer::class)
    data class ComplexOperationCondition(
        val value: List<String>,
    ) : NotificationExpression

    @JsonDeserialize(using = ComparisonConditionDeserializer::class)
    data class ComparisonCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = NotEqualsConditionDeserializer::class)
    data class NotEqualsCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = GreaterThanConditionDeserializer::class)
    data class GreaterThanCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = GreaterThanOrEqualsConditionDeserializer::class)
    data class GreaterThanOrEqualsCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = LessThanConditionDeserializer::class)
    data class LessThanCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = LessThanOrEqualsConditionDeserializer::class)
    data class LessThanOrEqualsCondition(
        val value: String,
    ) : NotificationExpression

    @JsonDeserialize(using = AnyOfConditionDeserializer::class)
    data class AnyOfCondition(
        val value: List<String>,
    ) : NotificationExpression

    @JsonDeserialize(using = NoneOfConditionDeserializer::class)
    data class NoneOfCondition(
        val value: List<String>,
    ) : NotificationExpression
}

data class AuthxType(
    val feature: String,
    val type: NotificationExpression?,
    val region: NotificationExpression?,
    val connectionState: NotificationExpression?,
    val ssoScopes: NotificationExpression?,
)
