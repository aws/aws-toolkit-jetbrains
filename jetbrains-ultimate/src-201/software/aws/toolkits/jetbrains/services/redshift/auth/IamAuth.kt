// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

val REDSHIFT_REGION_REGEX = """.*\..*\.(.+).redshift\.""".toRegex()
fun abc(url: String) = REDSHIFT_REGION_REGEX.find(url)?.groupValues?.get(1)
