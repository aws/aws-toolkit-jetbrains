package software.aws.toolkits.jetbrains.ui.explorer

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.lambda.explorer.AwsExplorerLambdaRootNode
import software.aws.toolkits.jetbrains.s3.explorer.AwsExplorerS3RootNode

enum class AwsExplorerService(val serviceId: String) {
    S3("s3") {
        override fun buildServiceRootNode(project: Project): AwsExplorerS3RootNode {
            return AwsExplorerS3RootNode(project)
        }
    },
    LAMBDA("lambda") {
        override fun buildServiceRootNode(project: Project): AwsExplorerLambdaRootNode {
            return AwsExplorerLambdaRootNode(project)
        }
    },
    ;

    abstract fun buildServiceRootNode(project: Project): AbstractTreeNode<String>
}