// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.ExecutableType
import software.aws.toolkits.jetbrains.core.executables.Validatable
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ExecutableBackedCacheResourceTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Before
    fun setUp() {
        ExtensionTestUtil.maskExtensions(ExecutableType.EP_NAME, listOf(MockExecutable), disposableRule.disposable)
    }

    @After
    fun testDown() {
        MockCredentialsManager.getInstance().reset()
    }

    @Test
    fun testExecutableIsNotInstalledCausesException() {
        createMockExecutable("invalidBinary")

        assertThatThrownBy {
            executeCacheResource {}
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun executablePathIsSet() {
        val path = createMockExecutable("validBinary")

        executeCacheResource {
            assertThat(this.exePath).isEqualTo(path.toAbsolutePath().toString())
        }
    }

    @Test
    fun regionIsSet() {
        createMockExecutable("validBinary")

        executeCacheResource {
            assertThat(this.environment).containsKey("AWS_REGION")
        }
    }

    @Test
    fun credentialsAreSet() {
        createMockExecutable("validBinary")

        executeCacheResource {
            assertThat(this.environment).containsKey("AWS_ACCESS_KEY_ID").containsKey("AWS_SECRET_ACCESS_KEY")
        }
    }

    @Test
    fun resultsAreReturned() {
        createMockExecutable("validBinary")

        assertThat(executeCacheResource { "Hello" }).isEqualTo("Hello")
    }

    private fun createMockExecutable(name: String): Path {
        val path = tempFolder.newFile(name).toPath()

        ExecutableManager.getInstance().setExecutablePath(MockExecutable, path).toCompletableFuture()
            .get(1, TimeUnit.SECONDS)

        return path
    }

    private fun <T> executeCacheResource(assertionBlock: GeneralCommandLine.() -> T): T {
        val cacheResource = ExecutableBackedCacheResource(MockExecutable::class, "mock", null) {
            assertionBlock.invoke(this)
        }

        return cacheResource.fetch(MockRegionProvider.getInstance().defaultRegion(), mockCredentials())
    }

    private fun mockCredentials(): ToolkitCredentialsProvider {
        val credentialsManager = MockCredentialsManager.getInstance()
        return credentialsManager.getAwsCredentialProvider(credentialsManager.addCredentials("Cred2"), MockRegionProvider.getInstance().defaultRegion())
    }

    private object MockExecutable : ExecutableType<String>, Validatable {
        override val id: String = "Mock"
        override val displayName: String = "Mock Executable"
        override fun version(path: Path): String = "1.0"

        override fun validate(path: Path) {
            check(path.fileName.toString() == "validBinary")
        }
    }
}
