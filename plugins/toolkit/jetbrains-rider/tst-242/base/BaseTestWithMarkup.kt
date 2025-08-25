// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

abstract class BaseTestWithMarkup : com.jetbrains.rider.test.base.BaseTestWithMarkup() {
    abstract fun solutionDirectoryName(): String

    override fun getSolutionDirectoryName() = solutionDirectoryName()
}
