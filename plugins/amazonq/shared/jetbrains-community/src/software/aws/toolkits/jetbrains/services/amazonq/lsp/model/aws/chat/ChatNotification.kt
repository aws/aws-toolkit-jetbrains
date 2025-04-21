package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

interface ChatNotification<T> {
    val command: String
    val params: T
}
