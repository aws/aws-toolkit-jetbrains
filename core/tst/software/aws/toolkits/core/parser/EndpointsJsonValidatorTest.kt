// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.parser

import junit.framework.TestCase
import org.junit.Test
import software.aws.toolkits.core.region.EndpointsJsonValidator

class EndpointsJsonValidatorTest {
    @Test
    fun isJsonSuccess(){
        EndpointsJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleSuccess.json").use {
            TestCase.assertTrue(EndpointsJsonValidator.canBeParsed(it))
        }
    }
    @Test
    fun isJsonFail(){
        EndpointsJsonValidatorTest::class.java.getResourceAsStream("/jsonSampleFailure.json").use {
            TestCase.assertFalse(EndpointsJsonValidator.canBeParsed(it))
        }
    }

}
