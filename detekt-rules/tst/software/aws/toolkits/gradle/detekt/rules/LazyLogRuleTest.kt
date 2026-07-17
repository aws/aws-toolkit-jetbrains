// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import dev.detekt.api.Config
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class LazyLogRuleTest {
    private val rule = LazyLogRule(Config.empty)

    @Test
    fun lambdaIsUsedToLog(env: KotlinEnvironmentContainer) {
        assertThat(
            rule.lintWithContext(
                env,
                """
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("")
fun foo() {
    LOG.debug { "Hi" }
}
                """.trimIndent(),
            ),
        ).isEmpty()
    }

    @Test
    fun methodCallIsUsedToLog(env: KotlinEnvironmentContainer) {
        assertThat(
            rule.lintWithContext(
                env,
                """
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("")
fun foo() {
    LOG.debug("Hi")
}
                """.trimIndent(),
            ),
        ).singleElement()
            .matches {
                it.message == "Use the lambda version of LOG.debug instead"
            }
    }

    @Test
    fun lambdaIsUsedToLogButWithException(env: KotlinEnvironmentContainer) {
        assertThat(
            rule.lintWithContext(
                env,
                """
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("")
fun foo() {
    val e = RuntimeException()
    LOG.debug(e) {"Hi" }
}
                """.trimIndent(),
            ),
        ).isEmpty()
    }

    @Test
    fun methodCallIsUsedToLogInUiTests(env: KotlinEnvironmentContainer) {
        assertThat(
            rule.lintWithContext(
                env,
                """
package software.aws.toolkits.jetbrains.uitests.really.cool.test

import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("")
fun foo() {
    LOG.debug("Hi")
}
                """.trimIndent(),
            ),
        ).isEmpty()
    }
}
