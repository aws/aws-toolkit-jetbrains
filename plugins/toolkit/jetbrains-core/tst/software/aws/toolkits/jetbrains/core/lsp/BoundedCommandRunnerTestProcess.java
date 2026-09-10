// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp;

final class BoundedCommandRunnerTestProcess {
    private BoundedCommandRunnerTestProcess() {}

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "success" -> System.out.println("PROCESS_OK");
            case "sleep" -> Thread.sleep(30_000L);
            case "large-output" -> {
                System.out.print("OUTPUT_START");
                for (int i = 0; i < 128 * 1024; i++) {
                    System.out.print('x');
                }
                System.out.print("OUTPUT_END");
            }
            default -> throw new IllegalArgumentException("Unknown test mode");
        }
    }
}
