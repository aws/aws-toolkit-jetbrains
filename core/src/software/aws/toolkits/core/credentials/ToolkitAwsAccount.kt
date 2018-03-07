package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider

/**
 * The Toolkit level AWS account.
 */
abstract class ToolkitAwsAccount protected constructor(
        /**
         * The internal immutable ID for this AWS account
         */
        val id: String,
        /**
         * The aws account name which can be updated by the end user through the Toolkit
         */
        var name: String,
        /**
         * The AWS account provider which manages all the account instances
         */
        val provider: ToolkitAwsAccountProvider
) {
    companion object {
        const val ACCOUNT_ID = "id"
        const val ACCOUNT_NAME = "name"
        const val ACCOUNT_PROVIDER_ID = "provider"
    }
    /**
     * Persist this AWS account to a [Map]. Subclass must override it if it has more properties to persist.
     * @see ToolkitAwsAccountProvider.justLoadToolkitAwsAccount
     */
    fun persistToMap(): Map<String, String> =
            mapOf(
                    ACCOUNT_ID to id,
                    ACCOUNT_NAME to name,
                    ACCOUNT_PROVIDER_ID to provider.id
            )

    /**
     * Return the underlying AWS Credentials Provider.
     */
    abstract fun getAwsCredentialsProvider(): AwsCredentialsProvider
}