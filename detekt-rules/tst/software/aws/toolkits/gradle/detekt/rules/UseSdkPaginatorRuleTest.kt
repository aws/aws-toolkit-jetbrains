// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.amazon.awssdk.services.lambda.LambdaClient
import java.io.File

class UseSdkPaginatorRuleTest {
    private val rule = UseSdkPaginatorRule()
    private val environment = createEnvironment(
        additionalRootPaths = listOf(
            File(LambdaClient::class.java.protectionDomain.codeSource.location.path)
        )
    ).env

    @Test
    fun `warns if paginatable-call is not used`() {
        assertThat(
            rule.compileAndLintWithContext(
                environment,
                """
import software.amazon.awssdk.services.lambda.LambdaClient

val client = object : LambdaClient {}
fun foo() {
    client.listAliases { }
}
                """.trimIndent()
            )
        ).singleElement()
            .matches {
                it.id == "UseSdkPaginator" && it.message.startsWith("Use the SDK paginator")
            }
    }

    @Test
    fun `does not warn on paginated call`() {
        assertThat(
            rule.compileAndLintWithContext(
                environment,
                """
import software.amazon.awssdk.services.lambda.LambdaClient

val client = object : LambdaClient {}
fun foo() {
    client.listAliasesPaginator { }
}
                """.trimIndent()
            )
        ).isEmpty()
    }

    @Test
    fun `does not warn if paginator is not available`() {
        assertThat(
            rule.compileAndLintWithContext(
                environment,
                """
import software.amazon.awssdk.services.lambda.LambdaClient

val client = object : LambdaClient {}
fun foo() {
    client.getAlias { }
}
                """.trimIndent()
            )
        ).isEmpty()
    }
}
