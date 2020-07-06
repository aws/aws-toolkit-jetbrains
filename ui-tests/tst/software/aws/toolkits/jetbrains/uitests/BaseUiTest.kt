// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

import java.nio.file.Path
import java.nio.file.Paths

abstract class BaseUiTest {
    val testDataPath: Path = Paths.get(System.getProperty("testDataPath"))
}
