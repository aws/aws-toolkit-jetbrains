// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

enum class CfnSection(val id: String) {
    Resources("Resources"),
    Parameters("Parameters"),
    Mappings("Mappings"),
    Metadata("Metadata"),
    Globals("Globals"),
    Conditions("Conditions"),
    Outputs("Outputs"),
    FormatVersion("AWSTemplateFormatVersion"),
    Transform("Transform"),
    Description("Description");

    companion object {
        private val SECTION_NAMES: Map<String, CfnSection> by lazy {
            values().associateBy { it.id }
        }

        fun fromName(name: String) = SECTION_NAMES[name]
    }
}
