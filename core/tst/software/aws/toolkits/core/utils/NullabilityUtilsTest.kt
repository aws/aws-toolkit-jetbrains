// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Test

class NullabilityUtilsTest {
    @Test
    fun nullRunsFunction() {
        var run = false
        null.onNull {
            run = true
        }

        assertThat(run).isTrue()
    }

    @Test
    fun nonNullDoesNotRun() {
        val result = "hello".onNull {
            fail("Should not run")
        }
        assertThat(result).isEqualTo("hello")
    }
}
