// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr

import com.intellij.docker.agent.DockerAgentApplication
import com.intellij.util.Base64
import software.amazon.awssdk.services.ecr.model.AuthorizationData
import software.aws.toolkits.jetbrains.services.ecr.actions.LocalImage
import software.aws.toolkits.jetbrains.services.ecr.resources.Repository

data class EcrLogin(
    val username: String,
    val password: String
)

data class EcrPushRequest(
    val localImageId: String,
    val remoteRepo: Repository,
    val remoteTag: String
)

private const val NO_TAG_TAG = "<none>:<none>"
internal fun Array<DockerAgentApplication>.toLocalImageList(): List<LocalImage> =
    this.flatMap { image ->
        image.imageRepoTags?.map { localTag ->
            val tag = if (localTag == NO_TAG_TAG) null else localTag
            LocalImage(image.imageId, tag)
        } ?: listOf(LocalImage(image.imageId, null))
    }.toList()

fun AuthorizationData.getDockerLogin(): EcrLogin {
    val auth = Base64.decode(this.authorizationToken()).toString(Charsets.UTF_8).split(':', limit = 2)

    return EcrLogin(
        auth.first(),
        auth.last()
    )
}
