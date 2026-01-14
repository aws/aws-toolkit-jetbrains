// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode

class OperationConditionDeserializer : JsonDeserializer<NotificationExpression.OperationCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.OperationCondition = when (parser.currentToken) {
        JsonToken.VALUE_STRING -> {
            // Handle direct string value
            NotificationExpression.OperationCondition(parser.valueAsString)
        }
        else -> throw JsonMappingException(parser, "Cannot deserialize OperatingCondition")
    }
}

class ComparisonConditionDeserializer : JsonDeserializer<NotificationExpression.ComparisonCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.ComparisonCondition {
        val op = OperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.ComparisonCondition(op.value)
    }
}

class NotEqualsConditionDeserializer : JsonDeserializer<NotificationExpression.NotEqualsCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.NotEqualsCondition {
        val op = OperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.NotEqualsCondition(op.value)
    }
}
class GreaterThanConditionDeserializer : JsonDeserializer<NotificationExpression.GreaterThanCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.GreaterThanCondition {
        val op = OperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.GreaterThanCondition(op.value)
    }
}
class GreaterThanOrEqualsConditionDeserializer : JsonDeserializer<NotificationExpression.GreaterThanOrEqualsCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.GreaterThanOrEqualsCondition {
        val op = OperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.GreaterThanOrEqualsCondition(op.value)
    }
}
class LessThanConditionDeserializer : JsonDeserializer<NotificationExpression.LessThanCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.LessThanCondition {
        val op = OperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.LessThanCondition(op.value)
    }
}
class LessThanOrEqualsConditionDeserializer : JsonDeserializer<NotificationExpression.LessThanOrEqualsCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.LessThanOrEqualsCondition {
        val op = OperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.LessThanOrEqualsCondition(op.value)
    }
}
class ComplexOperationConditionDeserializer : JsonDeserializer<NotificationExpression.ComplexOperationCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.ComplexOperationCondition {
        val node = parser.codec.readTree<JsonNode>(parser)
        if (!node.isArray) {
            throw JsonMappingException(parser, "anyOf/noneOf must contain an array of values")
        }
        val values = node.map { it.asText() }
        return NotificationExpression.ComplexOperationCondition(values)
    }
}
class AnyOfConditionDeserializer : JsonDeserializer<NotificationExpression.AnyOfCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.AnyOfCondition {
        val op = ComplexOperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.AnyOfCondition(op.value)
    }
}

class NoneOfConditionDeserializer : JsonDeserializer<NotificationExpression.NoneOfCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.NoneOfCondition {
        val op = ComplexOperationConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.NoneOfCondition(op.value)
    }
}

class ComplexConditionDeserializer : JsonDeserializer<NotificationExpression.ComplexCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.ComplexCondition {
        val node = parser.codec.readTree<JsonNode>(parser)
        if (!node.isArray) {
            throw JsonMappingException(parser, "or/and must contain an array of values")
        }
        return NotificationExpression.ComplexCondition(node.toNotificationExpressions(parser))
    }
}
class OrConditionDeserializer : JsonDeserializer<NotificationExpression.OrCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.OrCondition {
        val op = ComplexConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.OrCondition(op.expectedValueList)
    }
}

class AndConditionDeserializer : JsonDeserializer<NotificationExpression.AndCondition>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NotificationExpression.AndCondition {
        val op = ComplexConditionDeserializer().deserialize(parser, ctxt)
        return NotificationExpression.AndCondition(op.expectedValueList)
    }
}

class NotConditionDeserializer : JsonDeserializer<NotificationExpression.NotCondition>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): NotificationExpression.NotCondition {
        val node = p.codec.readTree<JsonNode>(p)
        val parser = node.traverse(p.codec)
        parser.nextToken()

        return NotificationExpression.NotCondition(parser.readValueAs(NotificationExpression::class.java))
    }
}

// Create a custom deserializer if needed
class NotificationTypeDeserializer : JsonDeserializer<NotificationScheduleType>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): NotificationScheduleType =
        NotificationScheduleType.fromString(p.valueAsString)
}

private fun JsonNode.toNotificationExpressions(p: JsonParser): List<NotificationExpression> = this.map { element ->
    val parser = element.traverse(p.codec)
    parser.nextToken()
    parser.readValueAs(NotificationExpression::class.java)
}
