// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

import com.intellij.ide.starter.ci.CIServer
import java.nio.file.Path

object TestCIServer : CIServer {
    override val isBuildRunningOnCI: Boolean = System.getenv("CI").toBoolean() == true
    override val buildNumber: String = ""
    override val branchName: String = ""
    override val buildParams: Map<String, String> = mapOf()

    override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    }

    override fun reportTestFailure(testName: String, message: String, details: String, linkToLogs: String?) {
        println("test: $testName")
        println("message: $message")
        println("details: $details")
        error(message)
    }

    override fun ignoreTestFailure(testName: String, message: String) {
    }

    override fun isTestFailureShouldBeIgnored(message: String) = false
}
