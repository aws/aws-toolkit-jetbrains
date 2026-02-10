// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.clients

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.codewhispererstreaming.CodeWhispererStreamingAsyncClient
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportIntent
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportResultArchiveRequest
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportResultArchiveResponseHandler
import software.amazon.awssdk.services.codewhispererstreaming.model.ValidationException
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ManagedSsoProfile
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.credentials.MockToolkitAuthManagerRule
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.services.amazonq.AmazonQTestBase
import java.util.concurrent.CompletableFuture

class AmazonQStreamingClientTest : AmazonQTestBase() {
    val mockClientManagerRule = MockClientManagerRule()
    private val mockCredentialRule = MockCredentialManagerRule()
    private val authManagerRule = MockToolkitAuthManagerRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, mockCredentialRule, mockClientManagerRule, disposableRule)

    private lateinit var streamingBearerClient: CodeWhispererStreamingAsyncClient
    private lateinit var ssoClient: SsoOidcClient

    private lateinit var amazonQStreamingClient: AmazonQStreamingClient
    private lateinit var connectionManager: ToolkitConnectionManager

    @Before
    override fun setup() {
        super.setup()

        // Allow Python paths for test environment (Python plugin scans for interpreters)
        if (SystemInfo.isWindows) {
            VfsRootAccess.allowRootAccess(disposableRule.disposable, "C:/Program Files")
        } else {
            VfsRootAccess.allowRootAccess(disposableRule.disposable, "/usr/bin", "/usr/local/bin")
        }

        amazonQStreamingClient = AmazonQStreamingClient.getInstance(projectRule.project)
        ssoClient = mockClientManagerRule.create()

        streamingBearerClient = mockClientManagerRule.create<CodeWhispererStreamingAsyncClient>().stub {
            on {
                exportResultArchive(any<ExportResultArchiveRequest>(), any<ExportResultArchiveResponseHandler>())
            } doReturn CompletableFuture.completedFuture(mock()) // void type can't be instantiated
        }

        val mockConnection = mock<AwsBearerTokenConnection>()
        whenever(mockConnection.getConnectionSettings()) doReturn mock<TokenConnectionSettings>()

        connectionManager = mock {
            on {
                activeConnectionForFeature(any())
            } doReturn authManagerRule.createConnection(ManagedSsoProfile("us-east-1", aString(), listOf("scopes"))) as AwsBearerTokenConnection
        }

        projectRule.project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposableRule.disposable)
    }

    @Test
    fun `check exportResultArchive`() = runTest {
        val requestCaptor = argumentCaptor<ExportResultArchiveRequest>()
        val handlerCaptor = argumentCaptor<ExportResultArchiveResponseHandler>()

        amazonQStreamingClient.exportResultArchive("test-id", ExportIntent.TRANSFORMATION, null, {}, {})
        argumentCaptor<ExportResultArchiveRequest, ExportResultArchiveResponseHandler>().apply {
            verify(streamingBearerClient).exportResultArchive(requestCaptor.capture(), handlerCaptor.capture())
        }
    }

    @Test
    fun `verify retry on ValidationException`(): Unit = runBlocking {
        var attemptCount = 0
        streamingBearerClient = mockClientManagerRule.create<CodeWhispererStreamingAsyncClient>().stub {
            on {
                exportResultArchive(any<ExportResultArchiveRequest>(), any<ExportResultArchiveResponseHandler>())
            } doAnswer {
                attemptCount++
                if (attemptCount <= 2) {
                    CompletableFuture.runAsync {
                        throw VALIDATION_EXCEPTION
                    }
                } else {
                    CompletableFuture.completedFuture(mock())
                }
            }
        }

        amazonQStreamingClient.exportResultArchive("test-id", ExportIntent.TRANSFORMATION, null, {}, {})

        assertThat(attemptCount).isEqualTo(3)
    }

    @Test
    fun `verify retry gives up after max attempts`(): Unit = runBlocking {
        var attemptCount = 0
        streamingBearerClient = mockClientManagerRule.create<CodeWhispererStreamingAsyncClient>().stub {
            on {
                exportResultArchive(any<ExportResultArchiveRequest>(), any<ExportResultArchiveResponseHandler>())
            } doAnswer {
                attemptCount++
                CompletableFuture.runAsync {
                    throw VALIDATION_EXCEPTION
                }
            }
        }

        val thrown = catchCoroutineException {
            amazonQStreamingClient.exportResultArchive("test-id", ExportIntent.TRANSFORMATION, null, {}, {})
        }

        assertThat(attemptCount).isEqualTo(4)
        assertThat(thrown)
            .isInstanceOf(ValidationException::class.java)
            .hasMessage("Resource validation failed")
    }

    @Test
    fun `verify no retry on non-retryable exception`(): Unit = runBlocking {
        var attemptCount = 0

        streamingBearerClient = mockClientManagerRule.create<CodeWhispererStreamingAsyncClient>().stub {
            on {
                exportResultArchive(any<ExportResultArchiveRequest>(), any<ExportResultArchiveResponseHandler>())
            } doAnswer {
                attemptCount++
                CompletableFuture.runAsync {
                    throw IllegalArgumentException("Non-retryable error")
                }
            }
        }

        val thrown = catchCoroutineException {
            amazonQStreamingClient.exportResultArchive("test-id", ExportIntent.TRANSFORMATION, null, {}, {})
        }

        assertThat(attemptCount).isEqualTo(1)
        assertThat(thrown)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Non-retryable error")
    }

    @Test
    fun `verify backoff timing between retries`(): Unit = runBlocking {
        var lastAttemptTime = 0L
        var minBackoffObserved = Long.MAX_VALUE
        var maxBackoffObserved = 0L

        streamingBearerClient = mockClientManagerRule.create<CodeWhispererStreamingAsyncClient>().stub {
            on {
                exportResultArchive(any<ExportResultArchiveRequest>(), any<ExportResultArchiveResponseHandler>())
            } doAnswer {
                val currentTime = System.currentTimeMillis()
                if (lastAttemptTime > 0) {
                    val backoffTime = currentTime - lastAttemptTime
                    minBackoffObserved = minOf(minBackoffObserved, backoffTime)
                    maxBackoffObserved = maxOf(maxBackoffObserved, backoffTime)
                }
                lastAttemptTime = currentTime

                CompletableFuture.runAsync {
                    throw VALIDATION_EXCEPTION
                }
            }
        }

        val thrown = catchCoroutineException {
            amazonQStreamingClient.exportResultArchive("test-id", ExportIntent.TRANSFORMATION, null, {}, {})
        }

        assertThat(thrown)
            .isInstanceOf(ValidationException::class.java)
            .hasMessage("Resource validation failed")
        assertThat(minBackoffObserved).isGreaterThanOrEqualTo(100)
        assertThat(maxBackoffObserved).isLessThanOrEqualTo(10000)
    }

    @Test
    fun `verify onError callback is called with final exception`(): Unit = runBlocking {
        var errorCaught: Exception? = null

        streamingBearerClient = mockClientManagerRule.create<CodeWhispererStreamingAsyncClient>().stub {
            on {
                exportResultArchive(any<ExportResultArchiveRequest>(), any<ExportResultArchiveResponseHandler>())
            } doAnswer {
                CompletableFuture.runAsync {
                    throw VALIDATION_EXCEPTION
                }
            }
        }

        val thrown = catchCoroutineException {
            amazonQStreamingClient.exportResultArchive(
                "test-id",
                ExportIntent.TRANSFORMATION,
                null,
                { errorCaught = it },
                {}
            )
        }

        assertThat(thrown)
            .isInstanceOf(ValidationException::class.java)
            .hasMessage("Resource validation failed")
        assertThat(errorCaught).isEqualTo(VALIDATION_EXCEPTION)
    }

    private suspend fun catchCoroutineException(block: suspend () -> Unit): Throwable {
        try {
            block()
            error("Expected exception was not thrown")
        } catch (e: Throwable) {
            return e
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun allowPythonPaths() {
            // Allow Python paths for test environment (Python plugin scans for interpreters)
            if (SystemInfo.isWindows) {
                VfsRootAccess.allowRootAccess(Disposer.newDisposable(), "C:/Program Files")
            } else {
                VfsRootAccess.allowRootAccess(Disposer.newDisposable(), "/usr/bin", "/usr/local/bin")
            }
        }

        private val VALIDATION_EXCEPTION = ValidationException.builder()
            .message("Resource validation failed")
            .build()
    }
}
