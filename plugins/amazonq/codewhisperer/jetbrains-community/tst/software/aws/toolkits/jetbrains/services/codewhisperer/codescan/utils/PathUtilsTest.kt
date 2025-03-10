// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.PathUtils.getNormalizedRelativePath
import java.nio.file.Paths

class PathUtilsTest {
    @Test
    fun `test getNormalizedRelativePath()`() {
        assertThat(getNormalizedRelativePath("projectName", Paths.get("src/PackageName")))
            .isEqualTo("projectName/src/PackageName")
        assertThat(getNormalizedRelativePath("projectName", Paths.get("/src/./Package1/../Package2")))
            .isEqualTo("projectName/src/Package2")
    }
}
