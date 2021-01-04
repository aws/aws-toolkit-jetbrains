// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import javax.swing.Icon

/**
 * Lives in `icons` package due to that is how [com.intellij.openapi.util.IconLoader.getReflectiveIcon] works
 */
object AwsIcons {
    object Logos {
        @JvmField val AWS = load("/icons/logos/AWS.svg") // 13x13
        @JvmField val CLOUD_FORMATION_TOOL = load("/icons/logos/CloudFormationTool.svg") // 13x13
        @JvmField val EVENT_BRIDGE = load("/icons/logos/EventBridge.svg") // 13x13
    }

    object Misc {
        @JvmField val SMILE = load("/icons/misc/smile.svg") // 16x16
        @JvmField val SMILE_GREY = load("/icons/misc/smile_grey.svg") // 16x16
        @JvmField val FROWN = load("/icons/misc/frown.svg") // 16x16
    }

    object Resources {
        @JvmField val CLOUDFORMATION_STACK = load("/icons/resources/CloudFormationStack.svg") // 16x16
        object CloudWatch {
            @JvmField val LOGS = load("/icons/resources/cloudwatchlogs/CloudWatchLogs.svg") // 16x16
            @JvmField val LOGS_TOOL_WINDOW = load("/icons/resources/cloudwatchlogs/CloudWatchLogsToolWindow.svg") // 13x13
            @JvmField val LOG_GROUP = load("/icons/resources/cloudwatchlogs/CloudWatchLogsGroup.svg") // 16x16
        }
        @JvmField val ECR_REPOSITORY = load("/icons/resources/ECRRepository.svg") // 16x16
        @JvmField val LAMBDA_FUNCTION = load("/icons/resources/LambdaFunction.svg") // 16x16
        @JvmField val SCHEMA_REGISTRY = load("/icons/resources/SchemaRegistry.svg") // 16x16
        @JvmField val SCHEMA = load("/icons/resources/Schema.svg") // 16x16
        @JvmField val SERVERLESS_APP = load("/icons/resources/ServerlessApp.svg") // 16x16
        @JvmField val S3_BUCKET = load("/icons/resources/S3Bucket.svg") // 16x16
        @JvmField val REDSHIFT = load("/icons/resources/Redshift.svg") // 16x16
        object Ecs {
            @JvmField val ECS_CLUSTER = load("/icons/resources/ecs/EcsCluster.svg")
            @JvmField val ECS_SERVICE = load("/icons/resources/ecs/EcsService.svg")
            @JvmField val ECS_TASK_DEFINITION = load("/icons/resources/ecs/EcsTaskDefinition.svg")
        }
        object Rds {
            @JvmField val MYSQL = load("/icons/resources/rds/Mysql.svg") // 16x16
            @JvmField val POSTGRES = load("/icons/resources/rds/Postgres.svg") // 16x16
        }
        object Sqs {
            @JvmField val SQS_QUEUE = load("/icons/resources/sqs/SqsQueue.svg") // 16x16
            @JvmField val SQS_TOOL_WINDOW = load("/icons/resources/sqs/SqsToolWindow.svg") // 13x13
        }
    }

    object Actions {
        @JvmField val LAMBDA_FUNCTION_NEW: Icon = LayeredIcon.create(Resources.LAMBDA_FUNCTION, AllIcons.Actions.New)
        @JvmField val SCHEMA_VIEW: Icon = AllIcons.Actions.Preview
        @JvmField val SCHEMA_CODE_GEN: Icon = AllIcons.Actions.Download
        @JvmField val SCHEMA_SEARCH: Icon = AllIcons.Actions.Search
    }

    private fun load(path: String): Icon = IconLoader.getIcon(path, AwsIcons::class.java)
}
