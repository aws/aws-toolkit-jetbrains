// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.telemetry

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient
import software.amazon.awssdk.services.cognitoidentity.model.Credentials
import software.amazon.awssdk.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import software.amazon.awssdk.services.cognitoidentity.model.GetIdRequest
import software.amazon.awssdk.utils.cache.CachedSupplier
import software.amazon.awssdk.utils.cache.NonBlocking
import software.amazon.awssdk.utils.cache.RefreshResult
import java.time.temporal.ChronoUnit

/**
 * AWSCredentialsProvider implementation that uses the Amazon Cognito Identity
 * service to create temporary, short-lived sessions to use for authentication
 *
 * @constructor Creates a new AWSCredentialsProvider that uses credentials from a Cognito Identity pool.
 * @property identityPool The name of the pool to create users from
 * @property cognitoIdentityClient The Cognito client to use
 * @param cacheStorage A storage solution to cache an identity ID, disabled if null
 */
class AWSCognitoCredentialsProvider(
    private val identityPool: String,
    private val cognitoIdentityClient: CognitoIdentityClient,
    cacheStorage: CachedIdentityStorage? = null
) : AwsCredentialsProvider {

    private val identityIdProvider = AWSCognitoIdentityProvider(cognitoIdentityClient, identityPool, cacheStorage)
    private val cacheSupplier = CachedSupplier.builder(this::updateCognitoCredentials)
        .prefetchStrategy(NonBlocking("Cognito Identity Credential Refresh"))
        .build()

    override fun resolveCredentials(): AwsSessionCredentials = cacheSupplier.get()
    val credentials get() = resolveCredentials()

    private fun updateCognitoCredentials(): RefreshResult<AwsSessionCredentials> {
        val credentialsForIdentity = credentialsForIdentity()
        val sessionCredentials = AwsSessionCredentials.create(
            credentialsForIdentity.accessKeyId(),
            credentialsForIdentity.secretKey(),
            credentialsForIdentity.sessionToken()
        )
        val actualExpiration = credentialsForIdentity.expiration()

        return RefreshResult.builder(sessionCredentials)
            .staleTime(actualExpiration.minus(1, ChronoUnit.MINUTES))
            .prefetchTime(actualExpiration.minus(5, ChronoUnit.MINUTES))
            .build()
    }

    private fun credentialsForIdentity(): Credentials {
        val identityId = identityIdProvider.identityId
        val request = GetCredentialsForIdentityRequest.builder().identityId(identityId).build()
        return cognitoIdentityClient.getCredentialsForIdentity(request).credentials()
    }

    companion object {
        /**
         * Creates a Cognito Identity client with anonymous AWS credentials for use without a specific account
         */
        fun createAnonymousClient(region: Region): CognitoIdentityClient {
            val clientBuilder = CognitoIdentityClient.builder().apply {
                credentialsProvider(AnonymousCredentialsProvider.create())
                region(region)
            }
            return clientBuilder.build()
        }
    }
}

internal class AWSCognitoIdentityProvider(
    private val cognitoClient: CognitoIdentityClient,
    private val identityPoolId: String,
    private val cacheStorage: CachedIdentityStorage? = null
) {
    val identityId: String by lazy {
        loadFromCache() ?: createNewIdentity()
    }

    private fun loadFromCache(): String? = cacheStorage?.loadIdentity(identityPoolId)

    private fun createNewIdentity(): String {
        val request = GetIdRequest.builder().identityPoolId(identityPoolId).build()
        val newIdentityId = cognitoClient.getId(request).identityId()

        cacheStorage?.storeIdentity(identityPoolId, newIdentityId)

        return newIdentityId
    }
}

/**
 * Used to provide a way to cache an identity ID in order to prevent creating additional unneeded identities.
 */
interface CachedIdentityStorage {
    /**
     * Saves the identity ID to the backing storage.
     *
     * @param identityPoolId The pool the identity belongs to
     * @param identityId The generated ID
     */
    fun storeIdentity(identityPoolId: String, identityId: String)

    /**
     * Attempts to retrieve the identity ID from the backing storage. If no ID exists for the specified pool,
     * `null` should be returned in order to generate a new ID.
     *
     * @param identityPoolId The ID of the pool we are requested the ID for
     * @return The ID for the specified pool, else null
     */
    fun loadIdentity(identityPoolId: String): String?
}