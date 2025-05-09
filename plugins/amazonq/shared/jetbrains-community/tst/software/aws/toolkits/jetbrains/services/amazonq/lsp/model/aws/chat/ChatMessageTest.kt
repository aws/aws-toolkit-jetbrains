// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.reflect.ClassPath
import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AutoCloseableSoftAssertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import software.aws.toolkits.jetbrains.utils.satisfiesKt
import java.util.stream.Stream
import kotlin.streams.asStream
import kotlin.test.Test

class ChatMessageTest {
    @Test
    fun `sanity check`() {
        val jackson = jacksonObjectMapper()
        assertThat(IconType.CODE_BLOCK).satisfiesKt {
            // language=JSON
            val expected = """"code-block""""
            assertThat(Gson().toJson(it)).isEqualTo(expected)
            assertThat(jackson.writeValueAsString(it)).isEqualTo(expected)

            assertThat(Gson().fromJson(expected, IconType::class.java)).isEqualTo(it)
            assertThat(jackson.readValue<IconType>(expected)).isEqualTo(it)
        }
    }

    @TestFactory
    fun `enum is compatible between Jackson and Gson`(): Stream<DynamicTest> = sequence {
        val jackson = jacksonObjectMapper()
        ClassPath.from(Thread.currentThread().contextClassLoader)
            .getTopLevelClassesRecursive("software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat")
            .filter { it.load().isEnum }
            .forEach { enumClass ->
                val clazz = enumClass.load()
                val enumValues = clazz.enumConstants as Array<Enum<*>>
                enumValues.forEach { enumValue ->
                    val enumFqn = enumClass.name
                    // jackson is more straight forward so assume that it is probably the correct repr
                    val jacksonJson = jackson.writeValueAsString(enumValue)

                    yield(
                        DynamicTest.dynamicTest("$enumFqn.${enumValue.name}") {
                            println("$enumFqn -> $jacksonJson")

                            AutoCloseableSoftAssertions().use { softly ->
                                val jacksonRead = jackson.readValue(jacksonJson, clazz)
                                softly.assertThat(jacksonRead)
                                    .describedAs { "Jackson roundtrip $enumFqn: expecting ${enumValue.name}" }
                                    .isEqualTo(enumValue)

                                val gsonRead = Gson().fromJson(jacksonJson, clazz)
                                softly.assertThat(gsonRead)
                                    .describedAs { "Gson deserialize $enumFqn: expecting ${enumValue.name}" }
                                    .isEqualTo(enumValue)

                                val gsonWrite = Gson().toJson(enumValue)
                                softly.assertThat(gsonWrite)
                                    .describedAs { "Gson serialize $enumFqn: expecting $jacksonJson" }
                                    .isEqualTo(jacksonJson)
                            }
                        }
                    )
                }
            }
    }.asStream()
}
