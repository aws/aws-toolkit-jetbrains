// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.local

import com.intellij.ui.CollectionComboBoxModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Test
import software.aws.toolkit.core.lambda.LambdaArchitecture

/**
 * Regression test for https://github.com/aws/aws-toolkit-jetbrains/issues/6363
 *
 * On IntelliJ Platform 2026.x, [CollectionComboBoxModel.replaceAll] internally calls
 * [java.util.AbstractList.clear] on the backing list. If the model was constructed
 * with an immutable list (e.g. the result of `Array.toList()`), this throws
 * [UnsupportedOperationException] and the entire local Lambda run configuration
 * editor fails to open.
 *
 * The fix is to construct [CollectionComboBoxModel] with a mutable list.
 */
class RawSettingsArchitectureModelTest {

    @Test
    fun architectureModelSupportsReplaceAllWithoutThrowing() {
        // Mirrors the construction in RawSettings.createUIComponents()
        val architectureModel = CollectionComboBoxModel(LambdaArchitecture.values().toMutableList())

        assertThatCode {
            architectureModel.replaceAll(mutableListOf(LambdaArchitecture.X86_64, LambdaArchitecture.ARM64))
        }.doesNotThrowAnyException()

        assertThat(architectureModel.items)
            .containsExactly(LambdaArchitecture.X86_64, LambdaArchitecture.ARM64)
    }

    @Test
    fun architectureModelSupportsReplaceAllWithSingleArchitecture() {
        val architectureModel = CollectionComboBoxModel(LambdaArchitecture.values().toMutableList())

        assertThatCode {
            architectureModel.replaceAll(mutableListOf(LambdaArchitecture.DEFAULT))
        }.doesNotThrowAnyException()

        assertThat(architectureModel.items).containsExactly(LambdaArchitecture.DEFAULT)
    }
}
