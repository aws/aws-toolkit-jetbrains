// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.inputStream
import java.io.InputStream
import java.nio.file.Paths

class NotificationFormatUtilsTest {
    lateinit var notificationJson: InputStream

    @Before
    fun setUp() {
        val file = javaClass.getResource("/exampleNotification2.json")?.let { Paths.get(it.toURI()).takeIf { f -> f.exists() } }
            ?: throw RuntimeException("Test file not found")
        notificationJson = file.inputStream()
    }

    @Test
    fun checkValidity() {
        assertDoesNotThrow {
            mapper.readValue<NotificationsList>(notificationJson)
        }
    }

    companion object {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
