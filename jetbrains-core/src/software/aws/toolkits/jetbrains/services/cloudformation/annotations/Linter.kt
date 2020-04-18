// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import java.io.BufferedReader
import java.io.InputStreamReader

class Linter {

    companion object {
        private val LOG = getLogger<Linter>()
    }

    fun execute(initialAnnotationResults: InitialAnnotationResults): List<ErrorAnnotation> {
        var errors: List<ErrorAnnotation> = ArrayList()

        try {
            LOG.info { "Beginning execution of CloudFormation linter" }
            val executable: String = LinterExecutable.getExecutablePath()
            val command: MutableList<String> = mutableListOf()
            command.add(executable)
            command.add("--template")
            command.add(initialAnnotationResults.pathToTemplate)
            command.add("--format")
            command.add("json")
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            val linterProcess = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(linterProcess.inputStream))
            val linterResultBuilder = StringBuilder()
            reader.forEachLine {
                linterResultBuilder.append(it)
            }
            errors = getErrorAnnotations(linterResultBuilder.toString())
            linterProcess.waitFor()
            LOG.info { "Finished execution of CloudFormation linter with no errors" }
        } catch (e: Exception) {
            LOG.error(e) { "Error: " + e.message }
        }
        return errors
    }

    private fun getErrorAnnotations(input: String): List<ErrorAnnotation> {
        val mapper = ObjectMapper()
        val typeReference = TypeFactory.defaultInstance().constructCollectionType(
                MutableList::class.java, ErrorAnnotation::class.java
            )

        return mapper.readValue(input, typeReference)
    }
}
