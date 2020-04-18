// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import java.io.File
import java.io.IOException

class ErrorAnnotatorTest {

    private var yamlTemplate = "/linterInput.yaml"
    var errorAnnotator: ErrorAnnotator = ErrorAnnotator()
    var psiFile: PsiFile = Mockito.mock(PsiFile::class.java)
    var annotationHolder: AnnotationHolder = Mockito.mock(AnnotationHolder::class.java)

    @Before
    @Throws(IOException::class)
    fun setup() {
        val inputStream = ErrorAnnotatorTest::class.java.getResourceAsStream(yamlTemplate)
        val templatePath = File(ErrorAnnotatorTest::class.java.getResource(yamlTemplate).path).toString()
        val virtualFileMock = Mockito.mock(VirtualFile::class.java)
        val annotationMock = Mockito.mock(Annotation::class.java)

        whenever(psiFile.virtualFile).thenReturn(virtualFileMock)
        whenever(virtualFileMock.path).thenReturn(templatePath)
        whenever(virtualFileMock.inputStream).thenReturn(inputStream)
        whenever(annotationHolder.createAnnotation(any(), any(), any()))
            .thenReturn(annotationMock)
    }

    @Test
    fun testDoAnnotate() {
        val initialAnnotationResults = errorAnnotator.collectInformation(psiFile)
        assertThat(initialAnnotationResults).isNotNull
        val errors = errorAnnotator.doAnnotate(initialAnnotationResults)
        assertThat(errors).isNotNull
        assertThat(errors.size).isEqualTo(1)
    }
}
