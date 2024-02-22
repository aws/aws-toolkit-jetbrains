// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.fasterxml.jackson.annotation.JsonProperty

class LinterRule {

    @JsonProperty(value = "Id")
    val id: String = ""
}
