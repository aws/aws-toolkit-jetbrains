// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.resources

import java.io.InputStream
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BundledResourcesTest(private val file: InputStream) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
            BundledResources.ENDPOINTS_FILE
        )
    }

    @Test
    fun fileExistsAndHasContent() {
        file.use {
            assertThat(it.read() > 0, equalTo(true))
        }
    }
}