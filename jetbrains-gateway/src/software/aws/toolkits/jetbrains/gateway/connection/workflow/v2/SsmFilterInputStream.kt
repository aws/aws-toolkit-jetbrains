// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.gateway.connection.workflow.v2

import java.io.FilterInputStream
import java.io.InputStream

// hacky remove trailing newline from SSM output
class SsmFilterInputStream(source: InputStream) : FilterInputStream(source) {
    private var lastByte = -1
    override fun read(): Int {
        if (lastByte == -1) {
            return super.read().also {
                lastByte = it
            }
        }

        val nextByte = super.read()
        if (nextByte == -1 && lastByte == '\n'.code) {
            return -1
        }

        return lastByte.also {
            lastByte = nextByte
        }
    }
}
