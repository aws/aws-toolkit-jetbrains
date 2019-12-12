package software.aws.toolkits.jetbrains.core.credentials

import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion

data class ConnectionSettings(val credentials: ToolkitCredentialsProvider?, val region: AwsRegion?)
