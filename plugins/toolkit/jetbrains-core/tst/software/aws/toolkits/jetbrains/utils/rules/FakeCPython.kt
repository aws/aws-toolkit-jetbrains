// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.rules

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.jetbrains.annotations.NotNull

internal class FakeCPython(private val languageLevel: LanguageLevel) : CPythonSdkFlavor<PyFlavorData.Empty>() {
    @NotNull
    override fun getName(): String = "FakeCPython"

    // 2026.3 removed PythonSdkFlavor.getVersionString(String); tests get the version string from PyTestSdkType
    // instead, so nothing relies on the flavor reporting it.
    override fun getLanguageLevel(sdk: Sdk) = languageLevel
}
