// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.parser

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import software.aws.toolkits.core.lambda.BasicXmlValidator
import java.io.FileInputStream

class BasicXmlValidatorTest {

    // private val xmlParse = XmlParser()
    @Test
    fun isXmlParsing() {
        val fileText = FileInputStream("./tst-resources/xmlSampleSuccess.xml")
        assertTrue(BasicXmlValidator.canBeParsed(fileText))
    }
    @Test
    fun isXmlParseFail() {
        val fileText = FileInputStream("./tst-resources/xmlSampleFailure.xml")
        assertFalse(BasicXmlValidator.canBeParsed(fileText))
    }
}
