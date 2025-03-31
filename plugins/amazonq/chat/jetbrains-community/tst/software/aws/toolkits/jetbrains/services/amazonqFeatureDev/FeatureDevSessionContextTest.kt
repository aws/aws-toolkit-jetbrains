// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.testFramework.RuleChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.project.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.FeatureDevService
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addFileToModule
import java.util.zip.ZipFile

data class FileCase(val path: String, val content: String = "", val shouldInclude: Boolean = true)

class FeatureDevSessionContextTest : FeatureDevTestBase(HeavyJavaCodeInsightTestFixtureRule()) {
    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, disposableRule)
    private lateinit var featureDevSessionContext: FeatureDevSessionContext
    private lateinit var featureDevService: FeatureDevService

    @Before
    fun setUp() {
        featureDevService = mock()
        whenever(featureDevService.project).thenReturn(projectRule.project)
        featureDevSessionContext = FeatureDevSessionContext(featureDevService.project, 1024)
    }

    private fun fileCases(autoBuildEnabled: Boolean) = listOf(
        FileCase(path = ".gitignore"),
        FileCase(path = ".gradle/cached.jar", shouldInclude = false),
        FileCase(path = "src/MyClass.java"),
        FileCase(path = "gradlew"),
        FileCase(path = "gradlew.bat"),
        FileCase(path = "README.md"),
        FileCase(path = "settings.gradle"),
        FileCase(path = "build.gradle"),
        FileCase(path = "gradle/wrapper/gradle-wrapper.properties"),
        FileCase(path = "builder/GetTestBuilder.java"),
        FileCase(path = ".aws-sam/build/function1", shouldInclude = false),
        FileCase(path = ".gem/specs.rb", shouldInclude = false),
        FileCase(path = "archive.zip"),
        FileCase(path = "output.bin"),
        FileCase(path = "images/logo.png"),
        FileCase(path = "assets/header.jpg"),
        FileCase(path = "icons/menu.svg"),
        FileCase(path = "license.txt"),
        FileCase(path = "License.md"),
        FileCase(path = "node_modules/express", shouldInclude = false),
        FileCase(path = "build/outputs", shouldInclude = false),
        FileCase(path = "dist/bundle.js", shouldInclude = false),
        FileCase(path = "gradle/wrapper/gradle-wrapper.jar"),
        FileCase(path = "big-file.txt", content = "blob".repeat(1024 * 1024), shouldInclude = false),
        FileCase(path = "devfile.yaml", shouldInclude = autoBuildEnabled),
    )

    private fun checkZipProject(autoBuildEnabled: Boolean, fileCases: Iterable<FileCase>, onBeforeZip: (() -> Unit)? = null) {
        fileCases.forEach {
            projectRule.fixture.addFileToModule(module, it.path, it.content)
        }

        onBeforeZip?.invoke()

        val zipResult = featureDevSessionContext.getProjectZip(autoBuildEnabled)
        val zipPath = zipResult.payload.path

        val zippedFiles = mutableSetOf<String>()
        ZipFile(zipPath).use { zipFile ->
            for (entry in zipFile.entries()) {
                if (!entry.name.endsWith("/")) {
                    zippedFiles.add(entry.name)
                }
            }
        }

        // The input file paths are relative to the workspaceRoot, however the zip content is relative to the addressableRoot:
        val addressableRoot = featureDevSessionContext.addressableRoot.path
        val workspaceRoot = featureDevSessionContext.workspaceRoot.path
        val base = addressableRoot.removePrefix(workspaceRoot).removePrefix("/")
        fun addressablePathOf(path: String) = path.removePrefix(base).removePrefix("/")

        fileCases.forEach {
            if (it.shouldInclude) {
                assertThat(zippedFiles).contains(addressablePathOf(it.path))
            } else {
                assertThat(zippedFiles).doesNotContain(addressablePathOf(it.path))
            }
        }
    }

    @Test
    fun `test zip with autoBuild enabled`() {
        checkZipProject(autoBuildEnabled = true, fileCases(autoBuildEnabled = true))
    }

    @Test
    fun `test zip with autoBuild disabled`() {
        checkZipProject(autoBuildEnabled = false, fileCases(autoBuildEnabled = false))
    }

    @Test
    fun `test content is included when selection root is workspace root`() {
        val fileCases = listOf(
            FileCase(path = "file.txt", shouldInclude = true),
            FileCase(path = "project/file.txt", shouldInclude = true),
            FileCase(path = "deep/nested/file.txt", shouldInclude = true)
        )

        checkZipProject(autoBuildEnabled = false, fileCases = fileCases, onBeforeZip = {
            featureDevSessionContext.selectionRoot = featureDevSessionContext.workspaceRoot
        })
    }

    @Test
    fun `test content is included within selection root which is deeper than content root`() {
        val fileCases = listOf(FileCase(path = "project/module/deep/file.txt", shouldInclude = true))

        checkZipProject(autoBuildEnabled = false, fileCases = fileCases, onBeforeZip = {
            featureDevSessionContext.selectionRoot = featureDevSessionContext.workspaceRoot.findFileByRelativePath("project/module/deep")
                ?: error("Failed to find fixture")
        })
    }

    @Test
    fun `test content is excluded outside of selection root`() {
        val fileCases = listOf(
            FileCase(path = "project/module/file.txt", shouldInclude = true),
            FileCase(path = "project/outside/no.txt", shouldInclude = false),
        )

        checkZipProject(autoBuildEnabled = false, fileCases = fileCases, onBeforeZip = {
            featureDevSessionContext.selectionRoot = featureDevSessionContext.workspaceRoot.findFileByRelativePath("project/module")
                ?: error("Failed to find fixture")
        })
    }
}
