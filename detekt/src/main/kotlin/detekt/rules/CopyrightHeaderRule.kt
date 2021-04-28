// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

class CopyrightHeaderRule : Rule() {
    private val header =
        """^// Copyright \d{4} Amazon.com, Inc. or its affiliates. All Rights Reserved.\n// SPDX-License-Identifier: Apache-2.0\n""".toRegex()

    override val issue = Issue("copyright-header", Severity.Style, "Check if the file has the correct header", Debt.FIVE_MINS)

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        if (!header.containsMatchIn(file.text)) {
            report(
                CodeSmell(
                    issue, Entity.atPackageOrFirstDecl(file), message = "Missing or incorrect file header"

                )
            )
        }
        file.text
        super.visitKtFile(file)
    }
}

