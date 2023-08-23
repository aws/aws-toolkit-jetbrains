// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.environments.AgentConnectionBasedEnvironmentContentsView
import com.jetbrains.toolbox.gateway.environments.ManualEnvironmentContentsView
import org.apache.sshd.client.SshClient
import java.util.concurrent.CompletableFuture

class CoCaDevEnvRemoteEnvironmentContentsView : AgentConnectionBasedEnvironmentContentsView {
    override fun getAgentConnection(): CompletableFuture<AgentConnectionBasedEnvironmentContentsView.AgentConnection> {
//        val client = SshClient.setUpDefaultClient()
//        client.start()
//        val session = client.connect("richali", "", 22).verify().session
//        val channel = session.createExecChannel("docker run --entrypoint java -it --mount type=bind,source=tbcli-2.1.0.16315.jar,target=/tbe.jar,readonly public.ecr.aws/amazoncorretto/amazoncorretto:17-al2023 -jar /tbe.jar agent")
//        channel.isUsePty = true
//        channel.setupSensibleDefaultPty()
//        channel.open().verify()
        val process = Runtime.getRuntime().exec("java -jar ~/Downloads/tbcli-2.1.0.16315.jar agent")

        return CompletableFuture.completedFuture(AgentConnectionBasedEnvironmentContentsView.AgentConnection(process.inputStream, process.outputStream))
    }
}
