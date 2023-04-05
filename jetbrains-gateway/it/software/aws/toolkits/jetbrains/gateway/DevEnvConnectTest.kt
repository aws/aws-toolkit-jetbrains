// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.gateway

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.remoteDev.downloader.JetBrainsClientDownloaderConfigurationProvider
import com.intellij.remoteDev.downloader.TestJetBrainsClientDownloaderConfigurationProvider
import com.intellij.remoteDev.hostStatus.UnattendedHostStatus
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.NetUtils
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.rd.util.lifetime.isNotAlive
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.codecatalyst.CodeCatalystClient
import software.amazon.awssdk.services.codecatalyst.model.DevEnvironmentStatus
import software.amazon.awssdk.services.codecatalyst.model.InstanceType
import software.aws.toolkits.core.utils.Waiters.waitUntil
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.MockClientManager
import software.aws.toolkits.jetbrains.core.credentials.DiskSsoSessionConnection
import software.aws.toolkits.jetbrains.core.tools.MockToolManagerRule
import software.aws.toolkits.jetbrains.gateway.connection.StdOutResult
import software.aws.toolkits.jetbrains.gateway.connection.ThinClientTrackerService
import software.aws.toolkits.jetbrains.gateway.connection.caws.CawsCommandExecutor
import software.aws.toolkits.jetbrains.gateway.connection.resultFromStdOut
import software.aws.toolkits.jetbrains.utils.FrameworkTestUtils
import software.aws.toolkits.jetbrains.utils.extensions.DevEnvironmentExtension
import software.aws.toolkits.jetbrains.utils.extensions.DisposerAfterAllExtension
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KFunction
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Disabled(value = "needs real credentials in CI")
class DevEnvConnectTest : AfterAllCallback {
    companion object {
        @JvmStatic
        @RegisterExtension
        val environmentExtension = DevEnvironmentExtension {
            it.spaceName("")
            it.projectName("")
            it.ides({ ide ->
                ide.name("IntelliJ")
                ide.runtime("public.ecr.aws/jetbrains/iu:release")
            })
            it.persistentStorage { storage ->
                storage.sizeInGiB(16)
            }
            it.instanceType(InstanceType.DEV_STANDARD1_MEDIUM)
            it.inactivityTimeoutMinutes(15)
            it.repositories(emptyList())
        }

        // disposer/app registered after devenv extension, so we can ensure that clients aren't shut down before devenv cleanup
        @JvmField
        @RegisterExtension
        val disposableExtension = DisposerAfterAllExtension()

        @JvmStatic
        @RegisterExtension
        val applicationExtension = ApplicationExtension()
    }

    private val client: CodeCatalystClient by lazy {
        val provider = DiskSsoSessionConnection("codecatalyst", "us-east-1")
        AwsClientManager.getInstance().getClient(provider.getConnectionSettings())
    }

    private val environment by lazy {
        environmentExtension.environment
    }

    private val ssmFactory by lazy {
        CawsCommandExecutor(
            client,
            environment.id,
            environment.spaceName,
            environment.projectName
        )
    }

    private val hostToken = System.getenv("CWM_HOST_STATUS_OVER_HTTP_TOKEN")

    private val localPort by lazy {
        NetUtils.findAvailableSocketPort()
    }

    private val lazyPortForward = lazy {
        ssmFactory.portForward(localPort, 63342)
    }

    private val endpoint by lazy {
        "http://localhost:$localPort/codeWithMe/unattendedHostStatus?token=$hostToken"
    }

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        FrameworkTestUtils.ensureBuiltInServerStarted()

        val disposable = disposableExtension.disposable
        serviceOrNull<ThinClientTrackerService>()
            ?: ApplicationManager
                .getApplication()
                .registerOrReplaceServiceInstance(ThinClientTrackerService::class.java, ThinClientTrackerService(), disposableExtension.disposable)

        MockClientManager.useRealImplementations(disposable)
        MockToolManagerRule.useRealTools(disposable)

        (service<JetBrainsClientDownloaderConfigurationProvider>() as TestJetBrainsClientDownloaderConfigurationProvider).apply {
            guestConfigFolder = tempDir.resolve("config")
            guestSystemFolder = tempDir.resolve("system")
            guestLogFolder = tempDir.resolve("log")
        }
    }

    private lateinit var connectionHandle: GatewayConnectionHandle
    @TestFactory
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `test connect to devenv`(): Iterator<DynamicTest> = sequence<DynamicTest> {
        // having issues running on 222, technically works on 223, but cleanup fails
        assumeTrue(ApplicationInfo.getInstance().build.baselineVersion >= 231)

        connectionHandle = runBlocking {
            CawsConnectionProvider().connect(
                mapOf(
                    CawsConnectionParameters.CAWS_SPACE to environment.spaceName,
                    CawsConnectionParameters.CAWS_PROJECT to environment.projectName,
                    CawsConnectionParameters.CAWS_ENV_ID to environment.id,
                ),
                ConnectionRequestor.Local
            )
        } ?: error("null connection handle")

        yieldAll(
            listOf(
                test(::`wait for environment ready`),
                test(::`poll for bootstrap script availability`),
                test(::`wait for backend start`),
                test(::`wait for backend connect`)
            )
        )
    }.iterator()

    fun `wait for environment ready`() = runBlocking {
        waitUntil(
            succeedOn = {
                it.status() == DevEnvironmentStatus.RUNNING
            },
            failOn = {
                it.status() == DevEnvironmentStatus.FAILED || it.status() == DevEnvironmentStatus.DELETED
            },
            maxDuration = Duration.ofMinutes(2),
            call = {
                client.getDevEnvironment {
                    it.spaceName(environment.spaceName)
                    it.projectName(environment.projectName)
                    it.id(environment.id)
                }
            }
        )
    }

    fun `poll for bootstrap script availability`() = runBlocking {
        // TODO scripts are in wrong location in unit test mode
        waitUntil(
            succeedOn = {
                it == StdOutResult.SUCCESS
            },
            maxDuration = Duration.ofMinutes(5),
            call = {
                // condition looks inverted because we want failure if script not found
                ssmFactory
                    .executeCommandNonInteractive("sh", "-c", "test -z \"\$(find /tmp -name \"start-ide.sh\" 2>/dev/null)\" && echo false || echo true")
                    .resultFromStdOut()
            }
        )
    }

    fun `wait for backend start`() = runBlocking {
        // make sure port forward is alive
        lazyPortForward.value

        waitUntil(
            succeedOn = {
                it != null
            },
            failOn = { connectionHandle.lifetime.isNotAlive },
            maxDuration = Duration.ofMinutes(5),
            call = {
                tryOrNull {
                    HttpRequests.request(endpoint).readString()
                }
            }
        )
    }

    fun `wait for backend connect`() = runBlocking {
        waitUntil(
            succeedOn = { status ->
                status.projects?.any { it.users.size > 1 } == true
            },
            failOn = { connectionHandle.lifetime.isNotAlive },
            maxDuration = Duration.ofMinutes(5),
            call = {
                UnattendedHostStatus.fromJson(HttpRequests.request(endpoint).readString())
            }
        )

        ThinClientTrackerService.getInstance().closeThinClient(environment.id)
    }

    override fun afterAll(context: ExtensionContext) {
        if (lazyPortForward.isInitialized()) {
            lazyPortForward.value.destroyProcess()
        }
    }

    private fun test(testRef: KFunction<*>) = DynamicTest.dynamicTest(testRef.name) { testRef.call() }
}
