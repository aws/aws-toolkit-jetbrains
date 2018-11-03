// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ultimate.services.lambda.nodejs

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.debugger.wip.WipLocalVmConnection
import com.jetbrains.nodeJs.NodeChromeDebugProcess
import com.jetbrains.nodeJs.NodeJSFileFinder
import com.jetbrains.nodeJs.createNodeJsDebugProcess
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState
import java.net.InetSocketAddress

class NodeJSSamDebugSupport : SamDebugSupport {
    override fun createDebugProcess(environment: ExecutionEnvironment, state: SamRunningState, debugPort: Int): XDebugProcessStarter? =
            object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    val executionResult = state.execute(environment.executor, environment.runner)
                    // TODO proper file finder is needed for the debugger to open proper local files
                    val fileFinder = NodeJSFileFinder(session.project)
                    val socketAddress = InetSocketAddress("localhost", debugPort)
                    if (state.settings.runtime == Runtime.NODEJS8_10) {
                        // copy-pasted from JavaScriptDebugger to keep 2018.2 compatibility
                        val connection = WipLocalVmConnection()
                        val process = NodeChromeDebugProcess(session, fileFinder, connection, executionResult)

                        if (executionResult.processHandler == null || executionResult.processHandler.isStartNotified) {
                            connection.open(socketAddress)
                        } else {
                            executionResult.processHandler.addProcessListener(object : ProcessAdapter() {
                                override fun startNotified(event: ProcessEvent) {
                                    connection.open(socketAddress)
                                }
                            })
                        }
                        return process
                    }
                    return createNodeJsDebugProcess(
                            socketAddress,
                            session,
                            executionResult,
                            fileFinder
                    )
                }
            }
}