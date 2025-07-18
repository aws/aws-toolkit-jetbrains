// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.ModuleDependencyProvider.Companion.EP_NAME
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@ExtendWith(ApplicationExtension::class)
class DefaultModuleDependenciesServiceTest {
    private lateinit var project: Project
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockModuleManager: ModuleManager
    private lateinit var sut: DefaultModuleDependenciesService
    private lateinit var mockDependencyProvider: ModuleDependencyProvider

    @BeforeEach
    fun setUp() {
        project = mockk()
        mockModuleManager = mockk()
        mockDependencyProvider = mockk<ModuleDependencyProvider>()
        mockLanguageServer = mockk()

        every { mockLanguageServer.didChangeDependencyPaths(any()) } returns Unit

        // Mock message bus
        val messageBus = mockk<MessageBus>()
        every { project.messageBus } returns messageBus
        val mockConnection = mockk<MessageBusConnection>()
        every { messageBus.connect(any<Disposable>()) } returns mockConnection
        every { mockConnection.subscribe(any(), any()) } just runs

        // Mock ModuleManager
        mockkStatic(ModuleManager::class)
        every { ModuleManager.getInstance(project) } returns mockModuleManager
        every { mockModuleManager.modules } returns Array(0) { mockk() }

        // Mock LSP service
        val mockLspService = mockk<AmazonQLspService>()
        every { project.getService(AmazonQLspService::class.java) } returns mockLspService
        every { project.serviceIfCreated<AmazonQLspService>() } returns mockLspService
        coEvery {
            mockLspService.executeIfRunning<CompletableFuture<ResponseMessage>>(any())
        } coAnswers {
            val func = firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLspService, mockLanguageServer)
        }

        // Mock extension point
        mockkObject(ModuleDependencyProvider.Companion)
        val epName = mockk<ExtensionPointName<ModuleDependencyProvider>>()
        every { EP_NAME } returns epName
        every { epName.forEachExtensionSafe(any()) } answers {
            val callback = firstArg<(ModuleDependencyProvider) -> Unit>()
            callback(mockDependencyProvider)
        }
    }

    @Test
    fun `test initial sync on construction`() = runTest {
        // Arrange
        val module = mockk<Module>()
        val params = DidChangeDependencyPathsParams(
            moduleName = "testModule",
            runtimeLanguage = "java",
            paths = listOf("/path/to/dependency.jar"),
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )

        every { mockModuleManager.modules } returns arrayOf(module)
        prepDependencyProvider(listOf(Pair(module, params)))

        sut = DefaultModuleDependenciesService(project, this)

        advanceUntilIdle()
        verify { mockLanguageServer.didChangeDependencyPaths(params) }
    }

    @Test
    fun `test rootsChanged with multiple modules`() = runTest {
        // Arrange
        val module1 = mockk<Module>()
        val module2 = mockk<Module>()
        val params1 = DidChangeDependencyPathsParams(
            moduleName = "module1",
            runtimeLanguage = "java",
            paths = listOf("/path/to/dependency1.jar"),
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
        val params2 = DidChangeDependencyPathsParams(
            moduleName = "module2",
            runtimeLanguage = "python",
            paths = listOf("/path/to/site-packages/package1"),
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )

        prepDependencyProvider(
            listOf(
                Pair(module1, params1),
                Pair(module2, params2)
            )
        )

        sut = DefaultModuleDependenciesService(project, this)

        advanceUntilIdle()
        verify { mockLanguageServer.didChangeDependencyPaths(params1) }
        verify { mockLanguageServer.didChangeDependencyPaths(params2) }
    }

    @Test
    fun `test rootsChanged withFileTypesChange`() = runTest {
        // Arrange
        val module = mockk<Module>()
        val params = DidChangeDependencyPathsParams(
            moduleName = "testModule",
            runtimeLanguage = "java",
            paths = listOf("/path/to/dependency.jar"),
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
        prepDependencyProvider(listOf(Pair(module, params)))
        val event = mockk<ModuleRootEvent>()
        every { event.isCausedByFileTypesChange } returns true

        sut = DefaultModuleDependenciesService(project, this)

        sut.rootsChanged(event)

        advanceUntilIdle()
        verify(exactly = 1) { mockLanguageServer.didChangeDependencyPaths(params) }
    }

    @Test
    fun `test rootsChanged after module changes`() = runTest {
        // Arrange
        val module = mockk<Module>()
        val params = DidChangeDependencyPathsParams(
            moduleName = "testModule",
            runtimeLanguage = "java",
            paths = listOf("/path/to/dependency.jar"),
            includePatterns = emptyList(),
            excludePatterns = emptyList()
        )
        val event = mockk<ModuleRootEvent>()

        every { mockModuleManager.modules } returns arrayOf(module)
        every { event.isCausedByFileTypesChange } returns false

        prepDependencyProvider(listOf(Pair(module, params)))

        sut = DefaultModuleDependenciesService(project, this)

        sut.rootsChanged(event)

        advanceUntilIdle()
        verify(exactly = 2) { mockLanguageServer.didChangeDependencyPaths(params) }
    }

    @Test
    fun `test deduplication of same moduleName and runtimeLanguage`() = runTest {
        // Arrange
        val module1 = mockk<Module>()
        val module2 = mockk<Module>()
        val params1 = DidChangeDependencyPathsParams(
            moduleName = "sameModule",
            runtimeLanguage = "java",
            paths = listOf("/path/to/dep1.jar"),
            includePatterns = listOf("*.java"),
            excludePatterns = listOf("test/**")
        )
        val params2 = DidChangeDependencyPathsParams(
            moduleName = "sameModule",
            runtimeLanguage = "java",
            paths = listOf("/path/to/dep2.jar"),
            includePatterns = listOf("*.class"),
            excludePatterns = listOf("build/**")
        )

        every { mockModuleManager.modules } returns arrayOf(module1, module2)
        every { mockDependencyProvider.isApplicable(any()) } returns true
        every { mockDependencyProvider.createParams(module1) } returns params1
        every { mockDependencyProvider.createParams(module2) } returns params2

        prepDependencyProvider(
            listOf(
                Pair(module1, params1),
                Pair(module2, params2)
            )
        )

        sut = DefaultModuleDependenciesService(project, this)

        advanceUntilIdle()

        // Verify only one call with merged paths
        verify(exactly = 1) {
            mockLanguageServer.didChangeDependencyPaths(
                match {
                    it.moduleName == "sameModule" &&
                        it.runtimeLanguage == "java" &&
                        it.paths.containsAll(listOf("/path/to/dep1.jar", "/path/to/dep2.jar")) &&
                        it.includePatterns.containsAll(listOf("*.java", "*.class")) &&
                        it.excludePatterns.containsAll(listOf("test/**", "build/**"))
                }
            )
        }
    }

    private fun prepDependencyProvider(moduleParamPairs: List<Pair<Module, DidChangeDependencyPathsParams>>) {
        every { mockModuleManager.modules } returns moduleParamPairs.map { it.first }.toTypedArray()

        every {
            EP_NAME.forEachExtensionSafe(any<Consumer<ModuleDependencyProvider>>())
        } answers {
            val consumer = firstArg<Consumer<ModuleDependencyProvider>>()
            moduleParamPairs.forEach { (module, params) ->
                every { mockDependencyProvider.isApplicable(module) } returns true
                every { mockDependencyProvider.createParams(module) } returns params
            }
            consumer.accept(mockDependencyProvider)
        }
    }
}
