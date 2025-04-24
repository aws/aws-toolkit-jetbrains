// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

abstract class BaseTestWithSolution : com.jetbrains.rider.test.base.BaseTestWithSolution() {
    abstract fun solutionDirectoryName(): String

    override fun getSolutionDirectoryName() = solutionDirectoryName()
}
