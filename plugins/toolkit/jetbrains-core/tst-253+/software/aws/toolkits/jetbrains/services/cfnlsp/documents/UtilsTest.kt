// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.documents

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class UtilsTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Test
    fun `getRelativePath returns relative path for valid file URI within project`() {
        // Create virtual files in IntelliJ's in-memory VFS (not physical files)
        // ProjectRule automatically cleans up the entire project VFS after each test
        val project = projectRule.project
        val srcDir = runWriteActionAndWait {
            VfsUtil.createDirectoryIfMissing(project.baseDir, "src")
        }
        val templateFile = runWriteActionAndWait {
            srcDir.createChildData(this, "template.yaml")
        }

        val uri = templateFile.url
        val result = RelativePathParser.getRelativePath(uri, project)

        assertThat(result).isEqualTo("src/template.yaml")
    }

    @Test
    fun `getRelativePath returns original URI for file outside project`() {
        val uri = "file:///completely/different/path/template.yaml"

        val result = RelativePathParser.getRelativePath(uri, projectRule.project)

        assertThat(result).isEqualTo(uri)
    }

    @Test
    fun `getRelativePath returns original URI for invalid URI`() {
        val invalidUri = "not-a-valid-uri"

        val result = RelativePathParser.getRelativePath(invalidUri, projectRule.project)

        assertThat(result).isEqualTo(invalidUri)
    }
}
