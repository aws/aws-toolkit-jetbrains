// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Path

class ExecutableTestingTask<T : ExecutableType2<V>, V : Version>(project: Project, title: String, private val type: T, private val path: Path) :
    Task.WithResult<V, RuntimeException>(project, title, true) {
    override fun compute(indicator: ProgressIndicator): V {
        val executableManager = ExecutableManager2.getInstance()
        val executable: Executable<ExecutableType2<V>> = executableManager.getExecutable(type, path)
        return executableManager.determineVersion(executable)
    }
}
