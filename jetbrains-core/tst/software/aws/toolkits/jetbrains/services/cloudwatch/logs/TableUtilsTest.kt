// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.TimeFormatConversion

class TableUtilsTest {
    private val sampleTime: Long = 1621173813000

    @Test
    fun `convert epoch time to string date time with seconds included`() {
        val showSeconds = true
        val correctTime = "2021-05-16 07:03:33.000"
        val time = TimeFormatConversion.convertEpochTimeToStringDateTime(sampleTime, showSeconds)
        assertThat(time).isEqualTo(correctTime)
    }

    @Test
    fun `convert epoch time to string date time with seconds excluded`() {
        val showSeconds = false
        val correctTime = "5/16/21, 7:03 AM"
        val time = TimeFormatConversion.convertEpochTimeToStringDateTime(sampleTime, showSeconds)
        assertThat(time).isEqualTo(correctTime)
    }
}
