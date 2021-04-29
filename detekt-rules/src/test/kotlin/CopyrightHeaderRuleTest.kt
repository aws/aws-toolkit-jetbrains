// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.gradle.detekt.rules.CopyrightHeaderRule

class CopyrightHeaderRuleTest {
    private val rule = CopyrightHeaderRule()

    @Test
    fun noHeaderPresent() {
        assertThat(
            rule.lint(
                """
        import a.b.c
                """.trimIndent()
            )
        )
            .hasOnlyOneElementSatisfying {
                it.id == "CopyrightHeader" && it.message == "Missing or incorrect file header"
            }
    }

    @Test
    fun headerPresent() {
        assertThat(
            rule.lint(
                """
        // Copyright 1970 Amazon.com, Inc. or its affiliates. All Rights Reserved.
        // SPDX-License-Identifier: Apache-2.0

        import a.b.c
                """.trimIndent()
            )
        ).isEmpty()
    }
}

