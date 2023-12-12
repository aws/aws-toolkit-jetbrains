// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model;

public class TransformRunnable implements Runnable {
        private Integer isComplete = null;

        public void exitCode(int i) {
            isComplete = i;
        }

        @Override
        public void run() {
            isComplete = 1;
        }

        public Integer isComplete() {
            return isComplete;
        }
}
