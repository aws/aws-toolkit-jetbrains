// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import java.nio.file.Path

class Executable<out T : ExecutableType2<*>>(val type: T, val path: Path) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Executable<*>

        if (type.id != other.type.id) return false
        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.id.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}
