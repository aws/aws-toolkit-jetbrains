// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse
import software.amazon.awssdk.services.sts.model.StsException
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.MockClientManagerExtension
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderExtension
import software.aws.toolkits.jetbrains.core.webview.BearerLoginHandler

@ExtendWith(MockKExtension::class)
class LoginUtilsTest {
    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @JvmField
    @RegisterExtension
    val mockRegionProvider = MockRegionProviderExtension()

    @JvmField
    @RegisterExtension
    val mockClientManager = MockClientManagerExtension()

    private var testUrl = aString()

    private lateinit var sut: Login
    private lateinit var configFacade: ConfigFilesFacade

    private val project: Project
        get() = projectExtension.project

    @BeforeEach
    fun setUp() {
        testUrl = aString()
        configFacade = mockk<ConfigFilesFacade>(relaxed = true)
    }

    @Test
    fun `awsId login successfully should run handler onSuccess`() {
        mockkStatic(::loginSso)
        val scopes = listOf(aString())
        val mockReturned = mockk<AwsBearerTokenConnection>()
        every { loginSso(project, SONO_URL, SONO_REGION, scopes, any()) } returns mockReturned

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.BuilderId(
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            }
        )

        val actual = sut.login(project)
        verify {
            loginSso(
                project,
                SONO_URL,
                SONO_REGION,
                scopes,
                any()
            )
        }
        assertThat(actual).isEqualTo(mockReturned)
        assertThat(cntOfSuccess).isEqualTo(1)
        assertThat(cntOfError).isEqualTo(0)
    }

    @Test
    fun `awsId login fail should run handler onError and return null`() {
        mockkStatic(::loginSso)
        val scopes = listOf(aString())
        every { loginSso(project, SONO_URL, SONO_REGION, scopes, any()) } throws InvalidGrantException.create("test", Exception())

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.BuilderId(
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            }
        )

        val actual = sut.login(project)
        verify {
            loginSso(
                project,
                SONO_URL,
                SONO_REGION,
                scopes,
                any()
            )
        }
        assertThat(actual).isEqualTo(null)
        assertThat(cntOfSuccess).isEqualTo(0)
        assertThat(cntOfError).isEqualTo(1)
    }

    @Test
    fun `idc login succeed should run handler onSuccess`(@TestDisposable disposable: Disposable) {
        mockkStatic(::authAndUpdateConfig)
        val url = "https://fooBarBaz.awsapps.com/start"
        val scopes = listOf(aString())
        val region = mockRegionProvider.defaultRegion()

        val mockReturned = mockk<AwsBearerTokenConnection>()
        every { authAndUpdateConfig(project, any(), any(), any(), any()) } returns mockReturned

        val connectionManager = mockk<ToolkitConnectionManager>(relaxed = true)
        project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposable)

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.IdC(
            url,
            region,
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            },
            configFacade
        )

        val actual = sut.login(project)
        verify {
            authAndUpdateConfig(
                project,
                UserConfigSsoSessionProfile("fooBarBaz", region.id, url, scopes),
                configFacade,
                any(),
                any()
            )

            connectionManager.switchConnection(mockReturned)
        }
        assertThat(actual).isEqualTo(mockReturned)
        assertThat(cntOfSuccess).isEqualTo(1)
        assertThat(cntOfError).isEqualTo(0)
    }

    @Test
    fun `idc login fail should run handler onError and return null`(@TestDisposable disposable: Disposable) {
        mockkStatic(::authAndUpdateConfig)
        val url = "https://fooBarBaz.awsapps.com/start"
        val scopes = listOf(aString())
        val region = mockRegionProvider.defaultRegion()

        every { authAndUpdateConfig(project, any(), any(), any(), any()) } throws InvalidGrantException.create("test", Exception())

        val connectionManager = mockk<ToolkitConnectionManager>(relaxed = true)
        project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposable)

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.IdC(
            url,
            region,
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            },
            configFacade
        )

        val actual = sut.login(project)
        verify {
            authAndUpdateConfig(
                project,
                UserConfigSsoSessionProfile("fooBarBaz", region.id, url, scopes),
                configFacade,
                any(),
                any()
            )
        }

        verify(inverse = true) {
            connectionManager.switchConnection(any())
        }

        assertThat(actual).isEqualTo(null)
        assertThat(cntOfSuccess).isEqualTo(0)
        assertThat(cntOfError).isEqualTo(1)
    }

    @Test
    fun `idc login fails with config facade error should run handler onError and return null`(@TestDisposable disposable: Disposable) {
        mockkStatic(::authAndUpdateConfig)
        val url = "https://fooBarBaz.awsapps.com/start"
        val scopes = listOf(aString())
        val region = mockRegionProvider.defaultRegion()

        val mockReturned = mockk<AwsBearerTokenConnection>()
        every { configFacade.readSsoSessions() } throws Exception()

        val connectionManager = mockk<ToolkitConnectionManager>(relaxed = true)
        project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposable)

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.IdC(
            url,
            region,
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            },
            configFacade
        )

        val actual = sut.login(project)
        verify(inverse = true) {
            authAndUpdateConfig(
                project,
                UserConfigSsoSessionProfile("fooBarBaz", region.id, url, scopes),
                configFacade,
                any(),
                any()
            )
        }

        verify(inverse = true) {
            connectionManager.switchConnection(any())
        }

        assertThat(actual).isEqualTo(null)
        assertThat(cntOfSuccess).isEqualTo(0)
        assertThat(cntOfError).isEqualTo(1)
    }

    @Test
    fun `iam login should validate sts identity and write to config file`(@TestDisposable disposable: Disposable) {
        every { configFacade.readAllProfiles() } returns emptyMap()

        val client = mockClientManager.create<StsClient>()
        client.stub {
            on { callerIdentity } doReturn mock<GetCallerIdentityResponse>()
        }

        val profileName = aString()
        val accessKey = aString()
        val secretKey = aString()

        sut = Login.LongLivedIAM(
            profileName,
            accessKey,
            secretKey,
            configFacade
        )

        val actual = runInEdtAndGet { sut.login(project) }

        val profile = Profile.builder()
            .name(profileName)
            .properties(
                mapOf(
                    "aws_access_key_id" to accessKey,
                    "aws_secret_access_key" to secretKey,
                )
            )
            .build()

        org.mockito.kotlin.verify(client, times(2)).callerIdentity

        verify {
            configFacade.appendProfileToCredentials(eq(profile))
        }

        assertThat(actual).isNotNull
    }

    @Test
    fun `iam login fail with config facade error and should not write to config file`(@TestDisposable disposable: Disposable) {
        every { configFacade.readAllProfiles() } throws Exception()

        val client = mockClientManager.create<StsClient>()

        val profileName = aString()
        val accessKey = aString()
        val secretKey = aString()

        sut = Login.LongLivedIAM(
            profileName,
            accessKey,
            secretKey,
            configFacade
        )

        val actual = runInEdtAndGet {
            sut.login(project)
        }

        org.mockito.kotlin.verifyNoInteractions(client)

        verify(inverse = true) {
            configFacade.appendProfileToCredentials(any())
        }

        assertThat(actual).isNull()
    }

    @Test
    fun `iam login fail with sts error and should not write to config file`(@TestDisposable disposable: Disposable) {
        every { configFacade.readAllProfiles() } returns emptyMap()

        val client = mockClientManager.create<StsClient>()
        client.stub {
            on { callerIdentity } doThrow StsException.create("", Exception())
        }

        val profileName = aString()
        val accessKey = aString()
        val secretKey = aString()

        sut = Login.LongLivedIAM(
            profileName,
            accessKey,
            secretKey,
            configFacade
        )

        val actual = runInEdtAndGet {
            sut.login(project)
        }

        org.mockito.kotlin.verify(client, times(2)).callerIdentity

        verify(inverse = true) {
            configFacade.appendProfileToCredentials(any())
        }

        assertThat(actual).isNull()
    }
}
