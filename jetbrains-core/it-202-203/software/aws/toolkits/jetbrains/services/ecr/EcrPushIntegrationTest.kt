// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.docker.DockerCloudConfiguration
import com.intellij.docker.remote.run.runtime.DockerAgentBuildImageConfig
import com.intellij.docker.remote.run.runtime.RemoteDockerApplicationRuntime
import com.intellij.docker.remote.run.runtime.RemoteDockerRuntime
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.Image
import software.amazon.awssdk.services.ecr.model.ImageIdentifier
import software.aws.toolkits.core.rules.EcrTemporaryRepositoryRule
import software.aws.toolkits.jetbrains.services.ecr.resources.Repository
import java.util.UUID

class EcrPushIntegrationTest {
    private val ecrClient = EcrClient.builder()
        .region(Region.US_WEST_2)
        .build()

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val folder = TemporaryFolder()

    @Rule
    @JvmField
    val ecrRule = EcrTemporaryRepositoryRule(ecrClient)

    private lateinit var remoteRepo: Repository

    @Before
    fun setUp() {
        remoteRepo = ecrRule.createRepository().toToolkitEcrRepository()!!
    }

    @Test
    fun testPushFromImage() {
        val remoteTag = UUID.randomUUID().toString()

        val dockerfile = folder.newFile()
        dockerfile.writeText(
            """
                # arbitrary base image with a shell
                FROM public.ecr.aws/lambda/provided:latest
                RUN touch $(date +%s)
            """.trimIndent()
        )

        val project = projectRule.project
        runBlocking {
            val serverInstance = EcrUtils.getDockerServerRuntimeInstance().runtimeInstance
            val ecrLogin = ecrClient.authorizationToken.authorizationData().first().getDockerLogin()
            val runtime = RemoteDockerApplicationRuntime.createWithPullImage(
                RemoteDockerRuntime.create(DockerCloudConfiguration.createDefault(), project),
                DockerAgentBuildImageConfig(System.currentTimeMillis().toString(), dockerfile, false)
            )
            // gross transform because we only have the short SHA right now
            val localImageId = runtime.agent.getImages(null).first { it.imageId.startsWith("sha256:${runtime.agentApplication.imageId}") }.imageId
            val pushRequest = ImageEcrPushRequest(
                dockerServerRuntime = serverInstance,
                localImageId = localImageId,
                remoteRepo = remoteRepo,
                remoteTag = remoteTag
            )

            EcrUtils.pushImage(projectRule.project, ecrLogin, pushRequest)

            assertThat(
                ecrClient.batchGetImage {
                    it.repositoryName(remoteRepo.repositoryName)
                    it.imageIds(ImageIdentifier.builder().imageTag(remoteTag).build())
                }.images()
            )
                .hasSize(1)
                .allSatisfy { image ->
                    assertDigestFromDockerManifest(image, localImageId)
                }
        }
    }

    @Test
    fun testPushFromDockerfile() {
        val remoteTag = UUID.randomUUID().toString()

        val dockerfile = folder.newFile()
        dockerfile.writeText(
            """
                # arbitrary base image with a shell
                FROM public.ecr.aws/lambda/provided:latest
                RUN touch $(date +%s)
            """.trimIndent()
        )

        val ecrLogin = ecrClient.authorizationToken.authorizationData().first().getDockerLogin()
        val config = EcrUtils.dockerRunConfigurationFromPath(projectRule.project, remoteTag, dockerfile.absolutePath)
        val pushRequest = DockerfileEcrPushRequest(
            config.configuration as DockerRunConfiguration,
            remoteRepo,
            remoteTag
        )
        runBlocking {
            EcrUtils.pushImage(projectRule.project, ecrLogin, pushRequest)

            // find our local image id
            val serverInstance = EcrUtils.getDockerServerRuntimeInstance().runtimeInstance
            val localImageId = serverInstance.agent.getImages(null).first { it.imageRepoTags.contains("${remoteRepo.repositoryUri}:$remoteTag") }.imageId

            assertThat(
                ecrClient.batchGetImage {
                    it.repositoryName(remoteRepo.repositoryName)
                    it.imageIds(ImageIdentifier.builder().imageTag(remoteTag).build())
                }.images()
            )
                .hasSize(1)
                .allSatisfy { image ->
                    assertDigestFromDockerManifest(image, localImageId)
                }
        }
    }

    private fun assertDigestFromDockerManifest(image: Image, imageId: String) {
        // inspect the manifest because the registry digest is not the same as the image id
        // https://github.com/docker/hub-feedback/issues/1925
        val node = objectMapper.readTree(image.imageManifest())
        assertThat(node.get("config").get("digest").asText()).isEqualTo(imageId)
    }

    companion object {
        val objectMapper = jacksonObjectMapper()
    }
}
