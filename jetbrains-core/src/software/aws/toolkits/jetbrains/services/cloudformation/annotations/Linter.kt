// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.executables.CfnLintExecutable
import software.aws.toolkits.resources.message

class Linter {

    companion object {
        private val LOG = getLogger<Linter>()
        private val typeReference = TypeFactory.defaultInstance().constructCollectionType(
            MutableList::class.java, CloudFormationLintAnnotation::class.java
        )
        private val executable = CfnLintExecutable().resolve()
        private val objectMapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun execute(initialAnnotationResults: InitialAnnotationResults): List<CloudFormationLintAnnotation> =
            try {
                if (executable == null || ! initialAnnotationResults.isCloudFormationTemplate) {
                    emptyList()
                } else {
                    LOG.info { "Beginning execution of CloudFormation linter" }
                    val output = ExecUtil.execAndGetOutput(GeneralCommandLine(executable.toString(),
                        "--template",
                        initialAnnotationResults.pathToTemplate,
                        "--format", "json"))
                    // https://github.com/aws-cloudformation/cfn-python-lint/blob/052bf770eab4b8ff270f193b7491d9dfcf34ba54/src/cfnlint/core.py#L54
                    if (output.exitCode >= 0) {
                        getErrorAnnotations(output.stdout)
                    } else {
                        throw IllegalStateException("Failed to run CloudFormation linter: ${output.stderr}")
                    }
                }
            } catch (e: Exception) {
                LOG.error(e) { message("cloudformation.linter.general_error") + e.message }
                emptyList()
            }

        fun getErrorAnnotations(linterOutputJson: String): List<CloudFormationLintAnnotation> =
            if (linterOutputJson.isEmpty()) {
                emptyList()
            } else {
                objectMapper.readValue(linterOutputJson, typeReference)
            }
    }
}
