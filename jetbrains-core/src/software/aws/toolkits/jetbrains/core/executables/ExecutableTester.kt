// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.layout.panel
import com.intellij.util.text.SemVer
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JTextField
import kotlin.random.Random

// TODO: Delete this! It's just my testing file

var version: String = "1.2.0"
var path: String = ""
lateinit var versionText: JTextField

class ExecutableTester : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        DialogBuilder()
            .title("Test ExecutableManager")
            .centerPanel(
                panel {
                    row("Version:") {
                        versionText = textField(::version).component
                    }

                    row {
                        val path = textField(::path).component

                        button("Detect") {
                            path.text = ExecutableManager2.getInstance().getExecutable(GitExecutable())?.path.toString()
                        }
                    }

                    row {
                        val path = textField(::path).component

                        button("Is Valid") {
                            val executable = ExecutableManager2.getInstance().getExecutable(GitExecutable())
                            val compatability = ExecutableManager2.getInstance().validateCompatability(project = null, executable = executable)
                            path.text = compatability::class.toString()
                        }
                    }
                }
            )
            .show()
    }
}

class GitExecutable : ExecutableType2<SemanticVersion>, AutoResolvable {
    override val id: String = "git"
    override val displayName: String = "Git"

    override fun determineVersion(path: Path): SemanticVersion {
        Thread.sleep(Random.nextLong(5) * 1000)
        return SemanticVersion(SemVer.parseFromText(versionText.text)!!)
    }

    override fun resolve(): Path = Paths.get("/usr/bin/git")

    override fun supportedVersions(): List<VersionRange<SemanticVersion>> = listOf(
        VersionRange(SemanticVersion(SemVer.parseFromText("1.0.0")!!), SemanticVersion(SemVer.parseFromText("2.0.0")!!))
    )
}
