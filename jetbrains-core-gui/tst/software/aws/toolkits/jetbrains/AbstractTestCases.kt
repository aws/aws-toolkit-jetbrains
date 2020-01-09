// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.testGuiFramework.impl.GuiTestCase
import org.junit.Before
import java.nio.file.Paths

abstract class EmptyProjectTestCase(): GuiTestCase() {

    @Before
    fun openEmptyProject() {
        guiTestRule.importProject(Paths.get(System.getProperty("testDataPath"), "empty-project").toFile())
    }
}
