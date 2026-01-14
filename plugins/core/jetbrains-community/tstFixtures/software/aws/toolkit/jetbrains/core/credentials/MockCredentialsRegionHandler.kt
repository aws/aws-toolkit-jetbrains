// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials

import software.aws.toolkit.core.credentials.CredentialIdentifier
import software.aws.toolkit.core.region.AwsRegion

class MockCredentialsRegionHandler : CredentialsRegionHandler {
    override fun determineSelectedRegion(identifier: CredentialIdentifier, selectedRegion: AwsRegion?): AwsRegion? = selectedRegion
}
