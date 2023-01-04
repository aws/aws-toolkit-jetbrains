// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.core.region.EndpointsJsonValidator

class EndpointsJsonValidatorTest {
    @Test
    fun isEndpointsJsonFileParsingSuccess() {
        EndpointsJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleSuccess.json").use {
            assertThat(EndpointsJsonValidator.canBeParsed(it)).isTrue
        }
    }
    @Test
    fun isEndpointsJsonFileParsingFailure() {
        EndpointsJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleFailure.json").use {
            assertThat(EndpointsJsonValidator.canBeParsed(it)).isFalse
        }
    }
}
