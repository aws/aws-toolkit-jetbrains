// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials

import software.amazon.q.core.credentials.CredentialIdentifier
import software.amazon.q.core.region.AwsRegion

class MockCredentialsRegionHandler : CredentialsRegionHandler {
    override fun determineSelectedRegion(identifier: CredentialIdentifier, selectedRegion: AwsRegion?): AwsRegion? = selectedRegion
}
