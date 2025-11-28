// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.completion

import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment

@TestEnvironment
@Solution("SamHelloWorldApp")
annotation class TestSamHelloWorldApp

@TestEnvironment
@Solution("SamMultipleHandlersApp")
annotation class TestSamMultipleHandlersApp
