// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.workspace.context

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.services.amazonq.project.EncoderServer
import software.aws.toolkits.jetbrains.services.amazonq.project.IndexRequest
import software.aws.toolkits.jetbrains.services.amazonq.project.IndexUpdateMode
import software.aws.toolkits.jetbrains.services.amazonq.project.InlineBm25Chunk
import software.aws.toolkits.jetbrains.services.amazonq.project.LspMessage
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextProvider
import software.aws.toolkits.jetbrains.services.amazonq.project.QueryChatRequest
import software.aws.toolkits.jetbrains.services.amazonq.project.QueryInlineCompletionRequest
import software.aws.toolkits.jetbrains.services.amazonq.project.RelevantDocument
import software.aws.toolkits.jetbrains.services.amazonq.project.UpdateIndexRequest
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import java.net.ConnectException
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectContextProviderTest {
    @Rule
    @JvmField
    val projectRule: CodeInsightTestFixtureRule = JavaCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val disposableRule: DisposableRule = DisposableRule()

    @Rule
    @JvmField
    val wireMock: WireMockRule = createMockServer()

    private val project: Project
        get() = projectRule.project

    private lateinit var encoderServer: EncoderServer
    private lateinit var sut: ProjectContextProvider

    private val mapper = jacksonObjectMapper()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        encoderServer = spy(EncoderServer(project))
        encoderServer.stub { on { port } doReturn wireMock.port() }

        sut = ProjectContextProvider(project, encoderServer, TestScope(context = dispatcher))

        // initialization
        stubFor(any(urlPathEqualTo("/initialize")).willReturn(aResponse().withStatus(200).withResponseBody(Body("initialize response"))))

        // build index
        stubFor(any(urlPathEqualTo("/buildIndex")).willReturn(aResponse().withStatus(200).withResponseBody(Body("initialize response"))))

        // update index
        stubFor(any(urlPathEqualTo("/updateIndexV2")).willReturn(aResponse().withStatus(200).withResponseBody(Body("initialize response"))))

        // query
        stubFor(
            any(urlPathEqualTo("/query")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withResponseBody(Body(validQueryChatResponse))
            )
        )
        stubFor(
            any(urlPathEqualTo("/queryInlineProjectContext")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withResponseBody(
                        Body(validQueryInlineResponse)
                    )
            )
        )

        stubFor(
            any(urlPathEqualTo("/getUsage"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withResponseBody(Body(validGetUsageResponse))
                )
        )
    }

    @Test
    fun `Lsp endpoint correctness`() {
        assertThat(LspMessage.Initialize.endpoint).isEqualTo("initialize")
        assertThat(LspMessage.Index.endpoint).isEqualTo("buildIndex")
        assertThat(LspMessage.UpdateIndex.endpoint).isEqualTo("updateIndexV2")
        assertThat(LspMessage.QueryChat.endpoint).isEqualTo("query")
        assertThat(LspMessage.QueryInlineCompletion.endpoint).isEqualTo("queryInlineProjectContext")
        assertThat(LspMessage.GetUsageMetrics.endpoint).isEqualTo("getUsage")
    }

    @Test
    fun `index should send files within the project to lsp - vector index enabled`() {
        ApplicationManager.getApplication().replaceService(
            CodeWhispererSettings::class.java,
            mock { on { isProjectContextEnabled() } doReturn true },
            disposableRule.disposable
        )

        projectRule.fixture.addFileToProject("Foo.java", "foo")
        projectRule.fixture.addFileToProject("Bar.java", "bar")
        projectRule.fixture.addFileToProject("Baz.java", "baz")

        sut.index()

        val request = IndexRequest(listOf("/src/Foo.java", "/src/Bar.java", "/src/Baz.java"), "/src", "all", "")
        assertThat(request.filePaths).hasSize(3)
        assertThat(request.filePaths).satisfies({
            it.contains("/src/Foo.java") &&
                it.contains("/src/Baz.java") &&
                it.contains("/src/Bar.java")
        })
        assertThat(request.config).isEqualTo("all")

        wireMock.verify(
            1,
            postRequestedFor(urlPathEqualTo("/buildIndex"))
                .withHeader("Content-Type", equalTo("text/plain"))
            // comment it out because order matters and will cause json string different
//                .withRequestBody(equalTo(encryptedRequest))
        )
    }

    @Test
    fun `index should send files within the project to lsp - vector index disabled`() {
        ApplicationManager.getApplication().replaceService(
            CodeWhispererSettings::class.java,
            mock { on { isProjectContextEnabled() } doReturn false },
            disposableRule.disposable
        )

        projectRule.fixture.addFileToProject("Foo.java", "foo")
        projectRule.fixture.addFileToProject("Bar.java", "bar")
        projectRule.fixture.addFileToProject("Baz.java", "baz")

        sut.index()

        val request = IndexRequest(listOf("/src/Foo.java", "/src/Bar.java", "/src/Baz.java"), "/src", "default", "")
        assertThat(request.filePaths).hasSize(3)
        assertThat(request.filePaths).satisfies({
            it.contains("/src/Foo.java") &&
                it.contains("/src/Baz.java") &&
                it.contains("/src/Bar.java")
        })
        assertThat(request.config).isEqualTo("default")

        wireMock.verify(
            1,
            postRequestedFor(urlPathEqualTo("/buildIndex"))
                .withHeader("Content-Type", equalTo("text/plain"))
            // comment it out because order matters and will cause json string different
//                .withRequestBody(equalTo(encryptedRequest))
        )
    }

    @Test
    fun `updateIndex should send correct encrypted request to lsp`() {
        sut.updateIndex(listOf("foo.java"), IndexUpdateMode.UPDATE)
        val request = UpdateIndexRequest(listOf("foo.java"), IndexUpdateMode.UPDATE.command)
        val requestJson = mapper.writeValueAsString(request)

        assertThat(mapper.readTree(requestJson)).isEqualTo(mapper.readTree("""{ "filePaths": ["foo.java"], "mode": "update" }"""))

        val encryptedRequest = encoderServer.encrypt(requestJson)

        wireMock.verify(
            1,
            postRequestedFor(urlPathEqualTo("/updateIndexV2"))
                .withHeader("Content-Type", equalTo("text/plain"))
                .withRequestBody(equalTo(encryptedRequest))
        )
    }

    @Test
    fun `query should send correct encrypted request to lsp`() {
        sut.query("foo")

        val request = QueryChatRequest("foo")
        val requestJson = mapper.writeValueAsString(request)

        assertThat(mapper.readTree(requestJson)).isEqualTo(mapper.readTree("""{ "query": "foo" }"""))

        val encryptedRequest = encoderServer.encrypt(requestJson)

        wireMock.verify(
            1,
            postRequestedFor(urlPathEqualTo("/query"))
                .withHeader("Content-Type", equalTo("text/plain"))
                .withRequestBody(equalTo(encryptedRequest))
        )
    }

    @Test
    fun `queryInline should send correct encrypted request to lsp`() = runTest {
        sut = ProjectContextProvider(project, encoderServer, this)
        sut.queryInline("foo", "Foo.java")
        advanceUntilIdle()

        val request = QueryInlineCompletionRequest("foo", "Foo.java")
        val requestJson = mapper.writeValueAsString(request)

        assertThat(mapper.readTree(requestJson)).isEqualTo(mapper.readTree("""{ "query": "foo", "filePath": "Foo.java" }"""))

        val encryptedRequest = encoderServer.encrypt(requestJson)
        wireMock.verify(
            1,
            postRequestedFor(urlPathEqualTo("/queryInlineProjectContext"))
                .withHeader("Content-Type", equalTo("text/plain"))
                .withRequestBody(equalTo(encryptedRequest))
        )
    }

    @Test
    fun `query chat should return empty if result set non deserializable`() = runTest {
        stubFor(
            any(urlPathEqualTo("/query")).willReturn(
                aResponse().withStatus(200).withResponseBody(
                    Body(
                        """
                            [
                                "foo", "bar"
                            ]
                        """.trimIndent()
                    )
                )
            )
        )

        assertThrows<Exception> {
            sut.query("foo")
        }
    }

    @Test
    fun `query chat should return deserialized relevantDocument`() = runTest {
        val r = sut.query("foo")
        assertThat(r).hasSize(2)
        assertThat(r[0]).isEqualTo(
            RelevantDocument(
                "relativeFilePath1",
                "context1"
            )
        )
        assertThat(r[1]).isEqualTo(
            RelevantDocument(
                "relativeFilePath2",
                "context2"
            )
        )
    }

    @Test
    fun `query inline should throw if resultset not deserializable`() {
        assertThrows<Exception> {
            runTest {
                sut = ProjectContextProvider(project, encoderServer, this)
                stubFor(
                    any(urlPathEqualTo("/queryInlineProjectContext")).willReturn(
                        aResponse().withStatus(200).withResponseBody(
                            Body(
                                """
                            [
                                "foo", "bar"
                            ]
                                """.trimIndent()
                            )
                        )
                    )
                )

                assertThrows<Exception> {
                    sut.queryInline("foo", "filepath")
                    advanceUntilIdle()
                }
            }
        }
    }

    @Test
    fun `query inline should return deserialized bm25 chunks`() = runTest {
        sut = ProjectContextProvider(project, encoderServer, this)
        advanceUntilIdle()
        val r = sut.queryInline("foo", "filepath")
        assertThat(r).hasSize(3)
        assertThat(r[0]).isEqualTo(
            InlineBm25Chunk(
                "content1",
                "file1",
                0.1
            )
        )
        assertThat(r[1]).isEqualTo(
            InlineBm25Chunk(
                "content2",
                "file2",
                0.2
            )
        )
        assertThat(r[2]).isEqualTo(
            InlineBm25Chunk(
                "content3",
                "file3",
                0.3
            )
        )
    }

    @Test
    fun `get usage should return memory, cpu usage`() = runTest {
        val r = sut.getUsage()
        assertThat(r).isEqualTo(ProjectContextProvider.Usage(123, 456))
    }

    @Test
    fun `queryInline should throw if time elapsed is greater than 50ms`() = runTest {
        assertThrows<TimeoutCancellationException> {
            sut = ProjectContextProvider(project, encoderServer, this)
            stubFor(
                any(urlPathEqualTo("/queryInlineProjectContext")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withResponseBody(
                            Body(validQueryInlineResponse)
                        )
                        .withFixedDelay(51) // 10 sec
                )
            )

            // it won't throw if it's executed within TestDispatcher context
            withContext(getCoroutineBgContext()) {
                sut.queryInline("foo", "bar")
            }

            advanceUntilIdle()
        }
    }

    @Test
    fun `test index payload is encrypted`() = runTest {
        whenever(encoderServer.port).thenReturn(3000)
        try {
            sut.index()
        } catch (e: ConnectException) {
            // no-op
        }
        verify(encoderServer, times(1)).encrypt(any())
    }

    @Test
    fun `test query payload is encrypted`() = runTest {
        whenever(encoderServer.port).thenReturn(3000)
        try {
            sut.query("what does this project do")
        } catch (e: ConnectException) {
            // no-op
        }
        verify(encoderServer, times(1)).encrypt(any())
    }

    private fun createMockServer() = WireMockRule(wireMockConfig().dynamicPort())
}

// language=JSON
val validQueryInlineResponse = """
                            [
                                {
                                    "content": "content1",
                                    "filePath": "file1",
                                    "score": 0.1
                                },
                                {
                                    "content": "content2",
                                    "filePath": "file2",
                                    "score": 0.2
                                },
                                {
                                    "content": "content3",
                                    "filePath": "file3",
                                    "score": 0.3
                                }
                            ]    
""".trimIndent()

// language=JSON
val validQueryChatResponse = """
                            [
                                {
                                    "filePath": "file1",
                                    "content": "content1",
                                    "id": "id1",
                                    "index": "index1",
                                    "vec": [
                                        "vec_1-1",
                                        "vec_1-2",
                                        "vec_1-3"
                                    ],
                                    "context": "context1",
                                    "prev": "prev1",
                                    "next": "next1",
                                    "relativePath": "relativeFilePath1",
                                    "programmingLanguage": "language1"
                                },
                                {
                                    "filePath": "file2",
                                    "content": "content2",
                                    "id": "id2",
                                    "index": "index2",
                                    "vec": [
                                        "vec_2-1",
                                        "vec_2-2",
                                        "vec_2-3"
                                    ],
                                    "context": "context2",
                                    "prev": "prev2",
                                    "next": "next2",
                                    "relativePath": "relativeFilePath2",
                                    "programmingLanguage": "language2"
                                }
                            ]
""".trimIndent()

// language=JSON
val validGetUsageResponse = """
                                {
                                  "memoryUsage":123,
                                  "cpuUsage":456
                                } 
""".trimIndent()
