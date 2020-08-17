// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

fun SourceSetContainer.getOrCreate(sourceSet: String, block: SourceSet.() -> Unit) {
    try {
        getByName(sourceSet).block()
    } catch (e: UnknownDomainObjectException) {
        create(sourceSet).block()
    }
}
