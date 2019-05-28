// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import java.io.ByteArrayInputStream
import org.jdom.Element
import org.jdom.input.SAXBuilder

fun String.toElement(): Element {
    val stream = ByteArrayInputStream(this.toByteArray())
    val builder = SAXBuilder()
    return builder.build(stream).rootElement
}
