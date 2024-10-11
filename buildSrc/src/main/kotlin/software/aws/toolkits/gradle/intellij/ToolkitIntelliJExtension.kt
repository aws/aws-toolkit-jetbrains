// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.intellij

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.getByType

abstract class ToolkitIntelliJExtension(private val providers: ProviderFactory) {
    abstract val ideFlavor: Property<IdeFlavor>

    fun ideProfile() = IdeVersions.ideProfile(providers)

    fun version(): Provider<String> = productProfile().flatMap { profile ->
        providers.provider { profile.sdkVersion }
    }

    fun productProfile(): Provider<out ProductProfile> = ideFlavor.flatMap { flavor ->
        when (flavor) {
            IdeFlavor.AI,
            IdeFlavor.IC,
            IdeFlavor.PC,
                -> ideProfile().map { it.community }

            IdeFlavor.DB,
            IdeFlavor.CL,
            IdeFlavor.GO,
            IdeFlavor.IU,
            IdeFlavor.PS,
            IdeFlavor.PY,
            IdeFlavor.RM,
            IdeFlavor.RR,
            IdeFlavor.WS,
                -> ideProfile().map { it.ultimate }

            IdeFlavor.RD -> ideProfile().map { it.rider }
            IdeFlavor.GW -> ideProfile().map { it.gateway!! }
        }
    }
}

val Project.toolkitIntelliJ
    get() = extensions.getByType<ToolkitIntelliJExtension>()
