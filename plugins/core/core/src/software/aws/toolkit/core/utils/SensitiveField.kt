// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.core.utils

import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses

@Target(AnnotationTarget.PROPERTY)
annotation class SensitiveField

fun redactedString(o: Any): String {
    val clazz = o::class
    if (!clazz.isData) {
        error("Only supports redacting data classes")
    }

    return buildString {
        append(clazz.simpleName)
        append("(")

        val properties = o::class.memberProperties
        properties.forEachIndexed { i, prop ->
            append(prop.name)
            append("=")

            // @Inherited does not work in Kotlin
            // https://youtrack.jetbrains.com/issue/KT-22265/Support-for-inherited-annotations
            if (
                prop.hasAnnotation<SensitiveField>() ||
                clazz.superclasses.flatMap { superClazz -> superClazz.members.filter { it.name == prop.name } }.any { it.hasAnnotation<SensitiveField>() }
            ) {
                if (prop.getter.call(o) == null) {
                    append("null")
                } else {
                    append("<redacted>")
                }
            } else {
                append(prop.getter.call(o))
            }

            if (i != properties.size - 1) {
                append(", ")
            }
        }

        append(")")
    }
}
