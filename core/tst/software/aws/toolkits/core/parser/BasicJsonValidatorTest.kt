// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.parser

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import software.aws.toolkits.core.region.BasicJsonValidator
import java.io.FileInputStream

class BasicJsonValidatorTest {
    @Test
    fun isJsonParse() {
        val fileText = FileInputStream("./tst-resources/jsonSampleSuccess.json")
        assertTrue(BasicJsonValidator.canBeParsed(fileText))
    }
    @Test
    fun isJsonParseFail() {
        val fileText = FileInputStream("./tst-resources/jsonSampleFailure.json")
        assertFalse(BasicJsonValidator.canBeParsed(fileText))
    }
}
