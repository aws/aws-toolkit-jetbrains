// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.testFramework.assertEqualsToFile
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformHilDownloadArtifact
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import kotlin.io.path.Path

class CodeTransformHilDownloadArtifactTest : CodeWhispererCodeModernizerTestBase(HeavyJavaCodeInsightTestFixtureRule()) {

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `Will extract downloadZip`() {
        val testZipFilePath = Path("software/aws/toolkits/jetbrains/services/codemodernizer/resources/files/hilDownloadResults/downloadResultsZip.zip")
        val hilDownloadArtifact = CodeTransformHilDownloadArtifact.create(testZipFilePath, tempFolder.root.toPath())

        // verify manifest file values
        assertEquals(hilDownloadArtifact.manifest.pomArtifactId, "lombok")
        assertEquals(hilDownloadArtifact.manifest.pomGroupId, "org.projectlombok")
        assertEquals(hilDownloadArtifact.manifest.sourcePomVersion, "0.11.4")
        assertEquals(hilDownloadArtifact.manifest.pomArtifactId, "lombok")

        // verify pom file
        val testDownloadPomFile = Path("software/aws/toolkits/jetbrains/services/codemodernizer/resources/files/hilDownloadResults/pom.xml")
        assertEqualsToFile("test", testDownloadPomFile.toFile(), hilDownloadArtifact.pomFile.readText().toString())
    }
}
