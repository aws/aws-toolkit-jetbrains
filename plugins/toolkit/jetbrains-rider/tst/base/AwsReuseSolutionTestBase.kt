// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

import com.jetbrains.rider.projectView.solutionDirectory
import com.jetbrains.rider.test.debugger.XDebuggerTestHelper
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
/**
 * Base test class that uses the same solution per test class.
 * Solution re-open logic takes time. We can avoid this by using the same solution instance per every test in a class
 */
abstract class AwsReuseSolutionTestBase : BaseTestWithSolution() {
    override val waitForCaches: Boolean get() = false
    override val persistCaches: Boolean get() = false
    override val restoreNuGetPackages: Boolean get() = false

    // 15 is a magic number (it's the return statement since they are all the same), but the only
    // example of it used that I could find it is used that way:
    // https://github.com/JetBrains/fsharp-support/blob/93ab17493a34a0bc0fd4c70b11adde02f81455c4/rider-fsharp/src/test/kotlin/debugger/AsyncDebuggerTest.kt#L6
    // Unlike our other projects we do not have a document to work with, so there might not be a nice way to do it.
    fun setBreakpoint(line: Int = 15) {
        // Same as com.jetbrains.rider.test.scriptingApi.toggleBreakpoint, but with the correct base directory
        XDebuggerTestHelper.toggleBreakpoint(project, getVirtualFileFromPath("src/HelloWorld/Function.cs", project.solutionDirectory), line - 1)
    }

    override val backendLoadedTimeout = backendStartTimeout
}
