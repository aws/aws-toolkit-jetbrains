package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials

data class UpdateCredentialsPayload(
    val data: String,
    val encrypted: String,
)

data class UpdateCredentialsPayloadData(
    val data: BearerCredentials
)

data class BearerCredentials(
    val token: String
)
