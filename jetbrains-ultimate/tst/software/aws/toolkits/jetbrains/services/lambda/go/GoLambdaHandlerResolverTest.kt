// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.goide.psi.GoFunctionDeclaration
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerResolver
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addGoLambdaHandler

class GoLambdaHandlerResolverTest {
    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    @Test
    fun `No return`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler() { 
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, "handler")
    }

    @Test
    fun `One return`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler() error { 
                        return nil
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, "handler")
    }

    @Test
    fun `No arguments two returns`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler() (bool, error) { 
                        return nil
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, "handler")
    }

    @Test
    fun `Invalid No arguments two returns, second one is not error`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler() (error, bool) { 
                        return nil
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, null)
    }

    @Test
    fun `Invalid three returns, second one is not error`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler() (error, error, error) { 
                        return nil
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, null)
    }

    @Test
    fun `One argument`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler(abc int) { 
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, "handler")
    }

    @Test
    fun `Two arguments`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler(a context.Context, abc int) { 
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, "handler")
    }

    @Test
    fun `Invalid two arguments first is not context`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler(a int, b int) { 
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, null)
    }

    @Test
    fun `Invalid three arguments`() {
        val handlerElement = projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler(a context.Context, b context.Context, c context.Context) { 
                    }
                    """.trimIndent()
        )

        assertDetermineHandler(handlerElement, null)
    }

    @Test
    fun `Two arguments Psi`() {
        projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler(a context.Context, abc int) { 
                    }
                    """.trimIndent()
        )

        assertFindPsiElements("handler", true)
    }

    @Test
    fun `Invalid three arguments Psi`() {
        projectRule.fixture.addGoLambdaHandler(
            handlerName = "handler",
            fileContent = """
                    package main
                    
                    func handler(a context.Context, b context.Context, c context.Context) { 
                    }
                    """.trimIndent()
        )

        assertFindPsiElements("handler", false)
    }

    private fun assertDetermineHandler(handlerElement: PsiElement, expectedHandlerFullName: String?) {
        val resolver = LambdaHandlerResolver.getInstance(RuntimeGroup.getById(BuiltInRuntimeGroups.Go))

        runInEdtAndWait {
            if (expectedHandlerFullName != null) {
                Assertions.assertThat(resolver.determineHandler(handlerElement)).isEqualTo(expectedHandlerFullName)
            } else {
                Assertions.assertThat(resolver.determineHandler(handlerElement)).isNull()
            }
        }
    }

    private fun assertFindPsiElements(handler: String, shouldBeFound: Boolean) {
        val resolver = LambdaHandlerResolver.getInstance(RuntimeGroup.getById(BuiltInRuntimeGroups.Go))
        runInEdtAndWait {
            val project = projectRule.fixture.project
            val lambdas = resolver.findPsiElements(project, handler, GlobalSearchScope.allScope(project))
            if (shouldBeFound) {
                Assertions.assertThat(lambdas).hasSize(1)
                Assertions.assertThat(lambdas[0]).isInstanceOf(GoFunctionDeclaration::class.java)
            } else {
                Assertions.assertThat(lambdas).isEmpty()
            }
        }
    }
}
