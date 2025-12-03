// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.changelog.tasks

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import software.aws.toolkits.gradle.changelog.ChangeType
import software.aws.toolkits.gradle.changelog.Entry
import software.aws.toolkits.gradle.changelog.MAPPER
import java.io.File
import java.util.Scanner
import java.util.UUID

open class NewChange : ChangeLogTask() {
    @get:Internal
    internal var defaultChangeType: ChangeType? = null

    @TaskAction
    fun create() {
        val pluginName = if (project.hasProperty("plugin")) {
            project.property("plugin") as? String?
        } else null

        val changeType = if (project.hasProperty("changeType")) {
            (project.property("changeType") as? String?)?.toUpperCase()?.let { ChangeType.valueOf(it) }
        } else defaultChangeType
        val description = if (project.hasProperty("description")) {
            project.property("description") as? String?
        } else null

        val input = Scanner(System.`in`)
        val file = when {
            pluginName != null && changeType != null && description != null -> createChange(pluginName, changeType, description)
            else -> promptForChange(input, pluginName, changeType)
        }
        git?.stage(file)
    }

    private fun promptForChange(input: Scanner, existingPlugin: String?, existingChangeType: ChangeType?): File {
        val pluginName = existingPlugin ?: promptForPlugin(input)
        val changeType = existingChangeType ?: promptForChangeType(input)

        logger.lifecycle("> Please enter a change description: ")
        val description = input.nextLine()

        return createChange(pluginName, changeType, description)
    }

    // Todo: delete the prompt for plugin after separation
    private fun promptForPlugin(input: Scanner): String {
        logger.lifecycle("""
> Which plugin is this change for?
1. amazonq
2. toolkit
> Please enter selection (1): """.trimIndent())

        return when (input.nextLine().trim()) {
            "2" -> "toolkit"
            else -> "amazonq"
        }
    }

    private fun promptForChangeType(input: Scanner): ChangeType {
        val changeList = ChangeType.values()
            .mapIndexed { index, changeType -> "${index + 1}. ${changeType.sectionTitle}" }
            .joinToString("\n")
        val newFeatureIndex = ChangeType.FEATURE.ordinal + 1
        logger.lifecycle("\n$changeList\n> Please enter change type ($newFeatureIndex): ")

        return input.nextLine().let {
            if (it.isNotBlank()) {
                ChangeType.values()[it.toInt() - 1]
            } else {
                ChangeType.FEATURE
            }
        }
    }

    private fun createChange(pluginName: String, changeType: ChangeType, description: String): File {
        return newFile(pluginName, changeType).apply {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(this, Entry(changeType, description))
        }
    }

    private fun newFile(pluginName: String, changeType: ChangeType) =
        changesDirectory.get().dir("$pluginName/next-release").file("${changeType.name.lowercase()}-${UUID.randomUUID()}.json").asFile.apply {
            parentFile?.mkdirs()
            createNewFile()
        }
}
