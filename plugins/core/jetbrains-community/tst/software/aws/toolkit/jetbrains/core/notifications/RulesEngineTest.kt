// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RulesEngineTest {

    @Test
    fun `ComparisonCondition matches equal values`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.ComparisonCondition("test"), "test")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.ComparisonCondition("test"), "other")).isFalse()
    }

    @Test
    fun `NotEqualsCondition matches different values`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.NotEqualsCondition("test"), "other")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.NotEqualsCondition("test"), "test")).isFalse()
    }

    @Test
    fun `GreaterThanCondition compares strings lexicographically`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.GreaterThanCondition("2.0"), "3.0")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.GreaterThanCondition("2.0"), "1.0")).isFalse()
    }

    @Test
    fun `LessThanCondition compares strings lexicographically`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.LessThanCondition("3.74.0"), "3.101.0", true)).isFalse()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.LessThanCondition("2.0"), "3.0")).isFalse()
    }

    @Test
    fun `GreaterThanOrEqualsCondition includes equality`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.GreaterThanOrEqualsCondition("2.0"), "2.0")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.GreaterThanOrEqualsCondition("2.0"), "3.0")).isTrue()
    }

    @Test
    fun `LessThanOrEqualsCondition includes equality`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.LessThanOrEqualsCondition("2.0"), "2.0")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.LessThanOrEqualsCondition("2.0"), "1.0")).isTrue()
    }

    @Test
    fun `AnyOfCondition checks list membership`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.AnyOfCondition(listOf("a", "b")), "b")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.AnyOfCondition(listOf("a", "b")), "c")).isFalse()
    }

    @Test
    fun `NoneOfCondition checks list non-membership`() {
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.NoneOfCondition(listOf("a", "b")), "c")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(NotificationExpression.NoneOfCondition(listOf("a", "b")), "a")).isFalse()
    }

    @Test
    fun `NotCondition negates result`() {
        val expression = NotificationExpression.NotCondition(NotificationExpression.ComparisonCondition("test"))
        assertThat(RulesEngine.evaluateNotificationExpression(expression, "test")).isFalse()
        assertThat(RulesEngine.evaluateNotificationExpression(expression, "other")).isTrue()
    }

    @Test
    fun `OrCondition returns true if any condition matches`() {
        val expression = NotificationExpression.OrCondition(
            listOf(NotificationExpression.ComparisonCondition("a"), NotificationExpression.ComparisonCondition("b"))
        )
        assertThat(RulesEngine.evaluateNotificationExpression(expression, "a")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(expression, "c")).isFalse()
    }

    @Test
    fun `AndCondition returns true only if all conditions match`() {
        val expression = NotificationExpression.AndCondition(
            listOf(NotificationExpression.GreaterThanCondition("1.0"), NotificationExpression.LessThanCondition("3.0"))
        )
        assertThat(RulesEngine.evaluateNotificationExpression(expression, "2.0")).isTrue()
        assertThat(RulesEngine.evaluateNotificationExpression(expression, "4.0")).isFalse()
    }
}
