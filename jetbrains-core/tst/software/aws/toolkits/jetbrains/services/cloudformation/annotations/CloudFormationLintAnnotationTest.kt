// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.lang.annotation.HighlightSeverity
import kotlin.test.assertEquals
import org.junit.Test

class CloudFormationLintAnnotationTest {

    @Test
    fun parsesLinterResponse() {
        val linterOutput = """
            [
              {
                "Filename": "linterInput.json",
                "Level": "Error",
                "Location": {
                  "End": {
                    "ColumnNumber": 13,
                    "LineNumber": 4
                  },
                  "Path": [
                    "Resources",
                    "S3Bucket",
                    "Type"
                  ],
                  "Start": {
                    "ColumnNumber": 7,
                    "LineNumber": 4
                  }
                },
                "Message": "Invalid or unsupported Type undefined for resource S3Bucket in us-east-1",
                "Rule": {
                  "Description": "Making sure the basic CloudFormation resources are properly configured",
                  "Id": "E3001",
                  "ShortDescription": "Basic CloudFormation Resource Check",
                  "Source": "https://github.com/aws-cloudformation/cfn-python-lint"
                }
              }
            ]
        """

        val linter = Linter()
        val errors = linter.getErrorAnnotations(linterOutput)
        assertEquals(1, errors.size)

        val onlyError = errors[0]

        assertEquals(HighlightSeverity.ERROR, onlyError.severity)
        assertEquals(7, onlyError.location?.start?.columnNumber)
        assertEquals(4, onlyError.location?.start?.lineNumber)
        assertEquals(13, onlyError.location?.end?.columnNumber)
        assertEquals(4, onlyError.location?.end?.lineNumber)
        assertEquals("E3001", onlyError.linterRule?.id)
    }

    @Test
    fun emptyResponseFromLinter() {
        val linter = Linter()
        val errors = linter.getErrorAnnotations("")
        assertEquals(0, errors.size)
    }
}
