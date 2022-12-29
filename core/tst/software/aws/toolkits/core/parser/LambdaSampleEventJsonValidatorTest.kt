// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.parser

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import software.aws.toolkits.core.lambda.LambdaSampleEventJsonValidator
class LambdaSampleEventJsonValidatorTest {
    @Test
    fun isJsonParse() {
        LambdaSampleEventJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleSuccess.json").use {
            assertTrue(LambdaSampleEventJsonValidator.canBeParsed(it))
        }
    }
    @Test
    fun isJsonParseFail() {
        LambdaSampleEventJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleFailure.json").use {
            assertFalse(LambdaSampleEventJsonValidator.canBeParsed(it))
        }
    }
}
