// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

public class ErrorPath {

    // The below multiply method is private so science should throw an error from the backend as UTG supports only public methods for test generation.
    private static double multiply(double num1, double num2) {
        return num1 * num2;
    }
}
