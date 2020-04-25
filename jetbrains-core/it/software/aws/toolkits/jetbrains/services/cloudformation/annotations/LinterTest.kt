// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import java.io.File

class LinterTest {

    companion object {
        private var jsonTemplate = "/linterInput.json"
        var cloudFormationLints: List<CloudFormationLintAnnotation> = mutableListOf()

        @BeforeClass
        @JvmStatic
        fun setup() {
            val cloudFormationLinter = Linter()
            val templatePath = File(LinterTest::class.java.getResource(jsonTemplate).path).toString()
            val initialAnnotationResults = InitialAnnotationResults(templatePath)
            cloudFormationLints = cloudFormationLinter.execute(initialAnnotationResults)
        }
    }

    @Test
    fun getsRightNumberOfErrors() {
        assertThat(cloudFormationLints.size).isEqualTo(1)
    }
}
