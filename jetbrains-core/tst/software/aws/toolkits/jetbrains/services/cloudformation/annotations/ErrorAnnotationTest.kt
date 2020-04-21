// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.lang.annotation.HighlightSeverity
import junit.framework.Assert.assertEquals
import org.junit.Test

class ErrorAnnotationTest {

    @Test
    fun parsesLinterResponse() {
        val linterOutput = "[\n" +
            "  {\n" +
            "    \"Filename\": \"linterInput.json\",\n" +
            "    \"Level\": \"Error\",\n" +
            "    \"Location\": {\n" +
            "      \"End\": {\n" +
            "        \"ColumnNumber\": 13,\n" +
            "        \"LineNumber\": 4\n" +
            "      },\n" +
            "      \"Path\": [\n" +
            "        \"Resources\",\n" +
            "        \"S3Bucket\",\n" +
            "        \"Type\"\n" +
            "      ],\n" +
            "      \"Start\": {\n" +
            "        \"ColumnNumber\": 7,\n" +
            "        \"LineNumber\": 4\n" +
            "      }\n" +
            "    },\n" +
            "    \"Message\": \"Invalid or unsupported Type undefined for resource S3Bucket in us-east-1\",\n" +
            "    \"Rule\": {\n" +
            "      \"Description\": \"Making sure the basic CloudFormation resources are properly configured\",\n" +
            "      \"Id\": \"E3001\",\n" +
            "      \"ShortDescription\": \"Basic CloudFormation Resource Check\",\n" +
            "      \"Source\": \"https://github.com/aws-cloudformation/cfn-python-lint\"\n" +
            "    }\n" +
            "  }\n" +
            "]\n"

        val linter = Linter()
        val errors = linter.getErrorAnnotations(linterOutput)

        val onlyError = errors[0]

        assertEquals(HighlightSeverity.ERROR, onlyError.severity)
        assertEquals(7, onlyError.location?.start?.columnNumber)
        assertEquals(4, onlyError.location?.start?.lineNumber)
        assertEquals(13, onlyError.location?.end?.columnNumber)
        assertEquals(4, onlyError.location?.end?.lineNumber)
        assertEquals("E3001", onlyError.linterRule?.id)
    }
}
