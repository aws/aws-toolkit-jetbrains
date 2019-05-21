// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IOptProperty
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rider.projectView.actions.projectTemplating.RiderProjectTemplate
import com.jetbrains.rider.projectView.actions.projectTemplating.RiderProjectTemplateGenerator
import com.jetbrains.rider.projectView.actions.projectTemplating.RiderProjectTemplateProvider
import com.jetbrains.rider.projectView.actions.projectTemplating.RiderProjectTemplateState
import com.jetbrains.rider.projectView.actions.projectTemplating.impl.ProjectTemplateDialogContext
import com.jetbrains.rider.projectView.actions.projectTemplating.impl.ProjectTemplateTransferableModel
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.resources.message

class RiderSamProjectProvider : RiderProjectTemplateProvider {

    companion object {
        private val defaultNetCoreRuntime = Runtime.DOTNETCORE2_1
    }

    override val isReady = Property(true)

    override fun load(lifetime: Lifetime, context: ProjectTemplateDialogContext): IOptProperty<RiderProjectTemplateState> {
        val state = RiderProjectTemplateState(arrayListOf(), arrayListOf())

        state.new.add(RiderSamProject())
        return OptProperty(state)
    }

    private class RiderSamProject : RiderProjectTemplate {

        override val group = "AWS"
        override val icon = AwsIcons.Resources.SERVERLESS_APP
        override val name = message("sam.init.name")

        override fun createGenerator(context: ProjectTemplateDialogContext, transferableModel: ProjectTemplateTransferableModel): RiderProjectTemplateGenerator {
            val generator = SamProjectGenerator().apply {
                settings.runtime = getCurrentRuntime()
            }

            return RiderSamProjectGenerator(
                    samGenerator = generator,
                    context = context,
                    group = group,
                    categoryName = name,
                    model = transferableModel)
        }

        override fun getKeywords() = arrayOf(name)

        private fun getCurrentRuntime(): Runtime {

            val runtimeList = java.lang.Runtime.getRuntime()
                    .exec("dotnet --list-runtimes").inputStream.bufferedReader().readLines()

            val versionRegex = Regex("(\\d+.\\d+.\\d+)")
            val versions = runtimeList
                    .filter { it.startsWith("Microsoft.NETCore.App") }
                    .map { runtimeString ->
                        val match = versionRegex.find(runtimeString) ?: return@map null
                        match.groups[1]?.value ?: return@map null
                    }
                    .filterNotNull()

            val version = versions.sortedBy { it }.lastOrNull() ?: return defaultNetCoreRuntime

            return Runtime.fromValue("dotnetcore${version.split('.').take(2).joinToString(".")}") ?: defaultNetCoreRuntime
        }
    }
}
