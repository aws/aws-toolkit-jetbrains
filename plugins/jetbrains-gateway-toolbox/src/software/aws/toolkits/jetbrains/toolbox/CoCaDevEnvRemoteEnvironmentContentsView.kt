// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.environments.AgentConnectionBasedEnvironmentContentsView
import com.jetbrains.toolbox.gateway.environments.ManualEnvironmentContentsView
import com.jetbrains.toolbox.gateway.environments.SshEnvironmentContentsView
import com.jetbrains.toolbox.gateway.ssh.SshConnectionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.sshd.client.SshClient
import java.util.concurrent.CompletableFuture

class CoCaDevEnvRemoteEnvironmentContentsView(private val coroutineScope: CoroutineScope) : SshEnvironmentContentsView {
    override fun getConnectionInfo(): CompletableFuture<SshConnectionInfo> {
        return CompletableFuture.completedFuture(object : SshConnectionInfo {
            override fun getHost(): String {
                return "codecatalyst-dev-env=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=webproject=7832c584-0701-4f19-a13b-fe3f6006d39f"
            }

            override fun getPort(): Int = 22

            override fun getUserName(): String? = null
        })
    }

//    override fun getAgentConnection(): CompletableFuture<AgentConnectionBasedEnvironmentContentsView.AgentConnection> {
////        val client = SshClient.setUpDefaultClient()
////        client.start()
////        val session = client.connect("richali", "", 22).verify().session
////        val channel = session.createExecChannel("docker run --entrypoint java -it --mount type=bind,source=tbcli-2.1.0.16315.jar,target=/tbe.jar,readonly public.ecr.aws/amazoncorretto/amazoncorretto:17-al2023 -jar /tbe.jar agent")
////        channel.isUsePty = true
////        channel.setupSensibleDefaultPty()
////        channel.open().verify()
//        val future = CompletableFuture<AgentConnectionBasedEnvironmentContentsView.AgentConnection>()
//        coroutineScope.launch {
//            val process = Runtime.getRuntime().exec("java -jar ~/Downloads/tbcli-2.2.0.19137.jar --structured-logging agent --direct")
//            future.complete(AgentConnectionBasedEnvironmentContentsView.AgentConnection(process.inputStream, process.outputStream) { CompletableFuture.completedFuture(process.destroyForcibly()) })
//        }
//
//        return future
//    }
}
