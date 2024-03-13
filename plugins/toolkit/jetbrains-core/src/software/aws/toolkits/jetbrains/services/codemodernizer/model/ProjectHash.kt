// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils
import java.util.Base64

data class ProjectHash(val hash: String) {
    companion object {
        fun create(file: VirtualFile) = ProjectHash(
            Base64
                .getEncoder()
                .encodeToString(
                    DigestUtils.sha256(
                        file.toNioPath()
                            .toAbsolutePath()
                            .toString()
                    )
                )
        )
    }
}
