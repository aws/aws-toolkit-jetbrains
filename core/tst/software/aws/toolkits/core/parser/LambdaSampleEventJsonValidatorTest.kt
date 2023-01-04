// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.core.lambda.LambdaSampleEventJsonValidator
class LambdaSampleEventJsonValidatorTest {
    @Test
    fun isLambdaSampleEventJsonFileParsingSuccessful() {
        LambdaSampleEventJsonValidatorTest::class.java.getResourceAsStream("/sampleLambdaEvent.json").use {
            assertThat(LambdaSampleEventJsonValidator.canBeParsed(it)).isTrue
        }
    }
    @Test
    fun isLambdaSampleEventJsonFileParsingFailure() {
        LambdaSampleEventJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleFailure.json").use {
            assertThat(LambdaSampleEventJsonValidator.canBeParsed(it)).isFalse
        }
    }
}
