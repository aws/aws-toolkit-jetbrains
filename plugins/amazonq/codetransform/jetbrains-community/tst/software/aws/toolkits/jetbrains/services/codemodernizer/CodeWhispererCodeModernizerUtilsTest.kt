// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.testFramework.LightVirtualFile
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.YAMLFileType
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationDownloadArtifact
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationProgressUpdate
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStep
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerArtifact.Companion.MAPPER
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformType
import software.aws.toolkits.jetbrains.services.codemodernizer.model.PlanTable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.combineTableRows
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.createClientSideBuildUploadZip
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getBillingText
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getClientInstructionArtifactId
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getTableMapping
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isPlanComplete
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.parseBuildFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.pollTransformationStatusAndPlan
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.validateCustomVersionsFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.validateSctMetadata
import software.aws.toolkits.jetbrains.utils.notifyStickyWarn
import software.aws.toolkits.jetbrains.utils.rules.addFileToModule
import software.aws.toolkits.resources.message
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile

class CodeWhispererCodeModernizerUtilsTest : CodeWhispererCodeModernizerTestBase() {
    @Before
    override fun setup() {
        super.setup()
    }

    private val mockProject: Project = mock(Project::class.java)

    @Test
    fun `can poll for updates`() {
        Mockito.doReturn(
            exampleGetCodeMigrationResponse,
            exampleGetCodeMigrationResponse.replace(TransformationStatus.TRANSFORMING),
            exampleGetCodeMigrationResponse.replace(TransformationStatus.STARTED),
            exampleGetCodeMigrationResponse.replace(TransformationStatus.COMPLETED), // Should stop before this point
        )
            .whenever(clientAdaptorSpy).getCodeModernizationJob(any())
        Mockito.doReturn(exampleGetCodeMigrationPlanResponse)
            .whenever(clientAdaptorSpy).getCodeModernizationPlan(any())
        val mutableList = mutableListOf<TransformationStatus>()
        runBlocking {
            jobId.pollTransformationStatusAndPlan(
                CodeTransformType.LANGUAGE_UPGRADE,
                setOf(TransformationStatus.STARTED),
                setOf(TransformationStatus.FAILED),
                clientAdaptorSpy,
                0,
                0,
                AtomicBoolean(false),
                project
            ) { _, status, _ ->
                mutableList.add(status)
            }
        }
        val expected =
            listOf<TransformationStatus>(
                exampleGetCodeMigrationResponse.transformationJob().status(),
                TransformationStatus.TRANSFORMING,
                TransformationStatus.STARTED,
            )
        assertThat(expected).isEqualTo(mutableList)
    }

    @Test
    fun `show re-auth notification on access denied`() {
        val mockAccessDeniedException = Mockito.mock(AccessDeniedException::class.java)

        mockkStatic(::notifyStickyWarn)
        every { notifyStickyWarn(any(), any(), any(), any(), any()) } just runs

        Mockito.doThrow(
            mockAccessDeniedException
        ).doReturn(
            exampleGetCodeMigrationResponse,
            exampleGetCodeMigrationResponse.replace(TransformationStatus.STARTED),
            exampleGetCodeMigrationResponse.replace(TransformationStatus.COMPLETED),
        ).whenever(clientAdaptorSpy).getCodeModernizationJob(any())

        Mockito.doReturn(exampleGetCodeMigrationPlanResponse)
            .whenever(clientAdaptorSpy).getCodeModernizationPlan(any())

        val mutableList = mutableListOf<TransformationStatus>()
        runBlocking {
            jobId.pollTransformationStatusAndPlan(
                CodeTransformType.LANGUAGE_UPGRADE,
                setOf(TransformationStatus.STARTED),
                setOf(TransformationStatus.FAILED),
                clientAdaptorSpy,
                0,
                0,
                AtomicBoolean(false),
                project
            ) { _, status, _ ->
                mutableList.add(status)
            }
        }
        val expected =
            listOf<TransformationStatus>(
                exampleGetCodeMigrationResponse.transformationJob().status(),
                TransformationStatus.STARTED,
            )
        assertThat(expected).isEqualTo(mutableList)
        verify { notifyStickyWarn(message("codemodernizer.notification.warn.expired_credentials.title"), any(), any(), any(), any()) }
    }

    @Test
    fun `show re-auth notification on invalid grant exception`() {
        val mockInvalidGrantException = Mockito.mock(InvalidGrantException::class.java)

        mockkStatic(::notifyStickyWarn)
        every { notifyStickyWarn(any(), any(), any(), any(), any()) } just runs

        Mockito.doThrow(
            mockInvalidGrantException
        ).doReturn(
            exampleGetCodeMigrationResponse,
            exampleGetCodeMigrationResponse.replace(TransformationStatus.STARTED),
            exampleGetCodeMigrationResponse.replace(TransformationStatus.COMPLETED),
        ).whenever(clientAdaptorSpy).getCodeModernizationJob(any())

        Mockito.doReturn(exampleGetCodeMigrationPlanResponse)
            .whenever(clientAdaptorSpy).getCodeModernizationPlan(any())

        val mutableList = mutableListOf<TransformationStatus>()
        runBlocking {
            jobId.pollTransformationStatusAndPlan(
                CodeTransformType.LANGUAGE_UPGRADE,
                setOf(TransformationStatus.STARTED),
                setOf(TransformationStatus.FAILED),
                clientAdaptorSpy,
                0,
                0,
                AtomicBoolean(false),
                project
            ) { _, status, _ ->
                mutableList.add(status)
            }
        }
        val expected =
            listOf<TransformationStatus>(
                exampleGetCodeMigrationResponse.transformationJob().status(),
                TransformationStatus.STARTED,
            )
        assertThat(expected).isEqualTo(mutableList)
        verify { notifyStickyWarn(message("codemodernizer.notification.warn.expired_credentials.title"), any(), any(), any(), any()) }
    }

    @Test
    fun `stops polling when status transitions to failOn`() {
        Mockito.doReturn(
            exampleGetCodeMigrationResponse,
            exampleGetCodeMigrationResponse.replace(TransformationStatus.FAILED),
            *happyPathMigrationResponses.toTypedArray(), // These should never be passed through the client
        )
            .whenever(clientAdaptorSpy).getCodeModernizationJob(any())
        val mutableList = mutableListOf<TransformationStatus>()

        val result = runBlocking {
            jobId.pollTransformationStatusAndPlan(
                CodeTransformType.LANGUAGE_UPGRADE,
                setOf(TransformationStatus.COMPLETED),
                setOf(TransformationStatus.FAILED),
                clientAdaptorSpy,
                0,
                0,
                AtomicBoolean(false),
                project,
            ) { _, status, _ ->
                mutableList.add(status)
            }
        }
        assertThat(result.succeeded).isFalse()
        val expected = listOf<TransformationStatus>(
            exampleGetCodeMigrationResponse.transformationJob().status(),
            TransformationStatus.FAILED,
        )
        assertThat(expected).isEqualTo(mutableList)
        verify(clientAdaptorSpy, times(2)).getCodeModernizationJob(any())
    }

    @Test
    fun `getTableMapping on complete step 0 progressUpdates creates map correctly`() {
        val jobStats =
            """{"name":"Job statistics", "columnNames":["name","value"],"rows":[{"name":"Dependencies to be replaced","value":"5"},
                |{"name":"Deprecated code instances to be replaced","value":"10"}]}"""
                .trimMargin()
        val depChanges =
            """{"name":"Dependency changes", "columnNames":["dependencyName","action","currentVersion","targetVersion"],
                |"rows":[{"dependencyName":"org.springboot.com","action":"Update","currentVersion":"2.1","targetVersion":"2.4"}]}"""
                .trimMargin()
        val apiChanges =
            """{"name":"Deprecated API changes", "columnNames":["apiFullyQualifiedName","numChangedFiles"],
                |"rows":[{"apiFullyQualifiedName": "java.lang.bad()", "numChangedFiles": "3"}]}"""
                .trimMargin()
        val fileChanges =
            """{"name":"File changes", "columnNames":["relativePath","action"],"rows":[{"relativePath":"pom.xml","action":"Update"}, 
                |{"relativePath":"src/main/java/BloodbankApplication.java","action":"Update"}]}"""
                .trimMargin()
        val step0Update0 = TransformationProgressUpdate.builder().name("0").status("COMPLETED").description(jobStats).build()
        val step0Update1 = TransformationProgressUpdate.builder().name("1").status("COMPLETED").description(depChanges).build()
        val step0Update2 = TransformationProgressUpdate.builder().name("2").status("COMPLETED").description(apiChanges).build()
        val step0Update3 = TransformationProgressUpdate.builder().name("-1").status("COMPLETED").description(fileChanges).build()
        val actual = getTableMapping(listOf(step0Update0, step0Update1, step0Update2, step0Update3))
        val expected = mapOf("0" to listOf(jobStats), "1" to listOf(depChanges), "2" to listOf(apiChanges), "-1" to listOf(fileChanges))
        assertThat(expected).isEqualTo(actual)
    }

    @Test
    fun `combineTableRows combines multiple dependency tables correctly`() {
        val table1Json = """
        {"name":"Dependency changes", "columnNames":["dependencyName","action","currentVersion","targetVersion"],
        "rows":[{"dependencyName":"org.springframework.boot","action":"Update","currentVersion":"2.1","targetVersion":"2.4"}]}
        """.trimIndent()
        val table2Json = """
        {"name":"Dependency changes", "columnNames":["dependencyName","action","currentVersion","targetVersion"],
        "rows":[{"dependencyName":"junit","action":"Add","currentVersion":"","targetVersion":"4.13"}]}
        """.trimIndent()
        val tables = listOf(
            MAPPER.readValue<PlanTable>(table1Json),
            MAPPER.readValue<PlanTable>(table2Json)
        )
        val combinedTable = combineTableRows(tables)
        assertThat(combinedTable?.rows).hasSize(2)
        assertThat(combinedTable?.name).isEqualTo("Dependency changes")
        assertThat(combinedTable?.columns).hasSize(4)
    }

    @Test
    fun `isPlanComplete returns true when plan has progress update with name '1'`() {
        val plan = TransformationPlan.builder()
            .transformationSteps(
                listOf(
                    TransformationStep.builder()
                        .progressUpdates(
                            listOf(
                                TransformationProgressUpdate.builder()
                                    .name("1")
                                    .build()
                            )
                        )
                        .build()
                )
            )
            .build()

        // dependency upgrade
        val sessionContext = CodeModernizerSessionContext(
            project = mockProject,
            sourceJavaVersion = JavaSdkVersion.JDK_17,
            targetJavaVersion = JavaSdkVersion.JDK_17
        )

        val result = isPlanComplete(plan, sessionContext)
        assertThat(result).isTrue()
    }

    @Test
    fun `isPlanComplete returns false when plan has no progress update with name '1'`() {
        val plan = TransformationPlan.builder()
            .transformationSteps(
                listOf(
                    TransformationStep.builder()
                        .progressUpdates(
                            listOf(
                                TransformationProgressUpdate.builder()
                                    .name("not-1")
                                    .build()
                            )
                        )
                        .build()
                )
            )
            .build()

        // dependency upgrade
        val sessionContext = CodeModernizerSessionContext(
            project = mockProject,
            sourceJavaVersion = JavaSdkVersion.JDK_17,
            targetJavaVersion = JavaSdkVersion.JDK_17
        )

        val result = isPlanComplete(plan, sessionContext)
        assertThat(result).isFalse()
    }

    @Test
    fun `isPlanComplete returns true when doing a min JDK upgrade`() {
        val plan = TransformationPlan.builder()
            .transformationSteps(
                listOf(
                    TransformationStep.builder()
                        .progressUpdates(
                            listOf(
                                TransformationProgressUpdate.builder()
                                    .name("not-1")
                                    .build()
                            )
                        )
                        .build()
                )
            )
            .build()

        // min JDK upgrade
        val sessionContext = CodeModernizerSessionContext(
            project = mockProject,
            sourceJavaVersion = JavaSdkVersion.JDK_1_8,
            targetJavaVersion = JavaSdkVersion.JDK_17
        )

        val result = isPlanComplete(plan, sessionContext)
        assertThat(result).isTrue()
    }

    @Test
    fun `getClientInstructionArtifactId extracts artifact ID from transformation plan`() {
        val step1 = TransformationStep.builder()
            .name("name of step 1")
            .description("description of step 1")
            .build()
        val step2 = TransformationStep.builder()
            .name("name of step 2")
            .description("description of step 2")
            .build()
        val step3 = TransformationStep.builder()
            .name("name of step 3")
            .description("description of step 3")
            .progressUpdates(
                TransformationProgressUpdate.builder()
                    .name("Requesting client-side build")
                    .status("AWAITING_CLIENT_ACTION")
                    .downloadArtifacts(
                        TransformationDownloadArtifact.builder()
                            .downloadArtifactId("id-123")
                            .build()
                    )
                    .build()
            )
            .build()
        val plan = TransformationPlan.builder()
            .transformationSteps(listOf(step1, step2, step3))
            .build()

        val actual = getClientInstructionArtifactId(plan)
        assertThat(actual).isEqualTo("id-123")
    }

    @Test
    fun `getClientInstructionArtifactId returns null when no download artifacts present`() {
        val step1 = TransformationStep.builder()
            .name("name of step 1")
            .description("description of step 1")
            .build()
        val step2 = TransformationStep.builder()
            .name("name of step 2")
            .description("description of step 2")
            .progressUpdates(
                TransformationProgressUpdate.builder()
                    .name("NOT requesting client-side build")
                    .status("NOT awaiting_client_action")
                    .build()
            )
            .build()
        val plan = TransformationPlan.builder()
            .transformationSteps(listOf(step1, step2))
            .build()

        val actual = getClientInstructionArtifactId(plan)
        assertThat(actual).isNull()
    }

    @Test
    fun `createClientSideBuildUploadZip creates zip with manifest and build output`() {
        val exitCode = 0
        val stdout = "Build completed successfully"
        val zipFile = createClientSideBuildUploadZip(exitCode, stdout)
        ZipFile(zipFile).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
            assertThat(manifestEntry).isNotNull
            val manifestContent = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            assertThat(manifestContent).contains("\"capability\":\"CLIENT_SIDE_BUILD\"")
            assertThat(manifestContent).contains("\"exitCode\":0")
            assertThat(manifestContent).contains("\"commandLogFileName\":\"build-output.log\"")
            val logEntry = zip.getEntry("build-output.log")
            assertThat(logEntry).isNotNull
            val logContent = zip.getInputStream(logEntry).bufferedReader().use { it.readText() }
            assertThat(logContent).isEqualTo("Build completed successfully")
        }
        zipFile.delete()
    }

    @Test
    fun `parseBuildFile can detect absolute paths in build file`() {
        val module = projectRule.module
        val fileText = "<project><properties><path>system/name/here</path></properties></project>"
        val file = projectRule.fixture.addFileToModule(module, "pom.xml", fileText)
        val expectedWarning = "I detected 1 potential absolute file path(s) in your pom.xml file: **system/**. " +
            "Absolute file paths might cause issues when I build your code. Any errors will show up in the build log."
        assertThat(parseBuildFile(file.virtualFile)).isEqualTo(expectedWarning)
    }

    @Test
    fun `getBillingText on small project returns correct String`() {
        val expected = "<html><body style=\"line-height:2; font-family: Arial, sans-serif; font-size: 14;\"><br>" +
            "376 lines of code were submitted for transformation. If you reach the quota for lines of code included " +
            "in your subscription, you will be charged $0.003 for each additional line of code. You might be charged up " +
            "to $1.13 for this transformation. To avoid being charged, stop the transformation job before it completes. " +
            "For more information on pricing and quotas, see <a href=\"https://aws.amazon.com/q/developer/pricing/\">" +
            "Amazon Q Developer pricing</a>.</p>"
        val actual = getBillingText(376)
        assertThat(expected).isEqualTo(actual)
    }

    @Test
    fun `WHEN validateCustomVersionsFile on fully valid yaml file THEN passes validation`() {
        val sampleFileContents = """name: "dependency-upgrade"
description: "Custom dependency version management for Java migration from JDK 8/11/17 to JDK 17/21"
dependencyManagement:
  dependencies:
    - identifier: "com.example:library1"
        targetVersion: "2.1.0"
        versionProperty: "library1.version"
        originType: "FIRST_PARTY"
  plugins:
    - identifier: "com.example:plugin"
        targetVersion: "1.2.0"
        versionProperty: "plugin.version"
        """.trimIndent()

        val virtualFile = LightVirtualFile("test-valid.yaml", YAMLFileType.YML, sampleFileContents)
        val missingKey = validateCustomVersionsFile(virtualFile)
        assertThat(missingKey).isNull()
    }

    @Test
    fun `WHEN validateCustomVersionsFile on invalid yaml file THEN fails validation`() {
        val sampleFileContents = """name: "dependency-upgrade"
description: "Custom dependency version management for Java migration from JDK 8/11/17 to JDK 17/21"
invalidKey:
  dependencies:
    - identifier: "com.example:library1"
        targetVersion: "2.1.0"
        versionProperty: "library1.version"
        originType: "FIRST_PARTY"
  plugins:
    - identifier: "com.example:plugin"
        targetVersion: "1.2.0"
        versionProperty: "plugin.version"
        """.trimIndent()

        val virtualFile = LightVirtualFile("test-invalid.yaml", YAMLFileType.YML, sampleFileContents)
        val missingKey = validateCustomVersionsFile(virtualFile)
        assertThat(missingKey).isEqualTo("dependencyManagement")
    }

    @Test
    fun `WHEN validateCustomVersionsFile on non-yaml file THEN fails validation`() {
        val sampleFileContents = """name: "dependency-upgrade"
description: "Custom dependency version management for Java migration from JDK 8/11/17 to JDK 17/21"
dependencyManagement:
  dependencies:
    - identifier: "com.example:library1"
        targetVersion: "2.1.0"
        versionProperty: "library1.version"
        originType: "FIRST_PARTY"
  plugins:
    - identifier: "com.example:plugin"
        targetVersion: "1.2.0"
        versionProperty: "plugin.version"
        """.trimIndent()

        val virtualFile = LightVirtualFile("test-invalid-file-type.txt", sampleFileContents)
        val isValidFile = validateCustomVersionsFile(virtualFile)
        assertThat(isValidFile).isEqualTo(message("codemodernizer.chat.message.custom_dependency_upgrades_invalid_not_yaml"))
    }

    @Test
    fun `WHEN validateCustomVersionsFile on yaml file missing originType THEN fails validation`() {
        val sampleFileContents = """name: "dependency-upgrade"
description: "Custom dependency version management for Java migration from JDK 8/11/17 to JDK 17/21"
dependencyManagement:
  dependencies:
    - identifier: "com.example:library1"
        targetVersion: "2.1.0"
        versionProperty: "library1.version"
  plugins:
    - identifier: "com.example:plugin"
        targetVersion: "1.2.0"
        versionProperty: "plugin.version"
        """.trimIndent()

        val virtualFile = LightVirtualFile("sample.yaml", sampleFileContents)
        val missingKey = validateCustomVersionsFile(virtualFile)
        assertThat(missingKey).isEqualTo("originType")
    }

    @Test
    fun `WHEN validateMetadataFile on fully valid sct file THEN passes validation`() {
        val sampleFileContents = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tree>
        <instances>
            <ProjectModel>
            <entities>
                <sources>
                <DbServer vendor="oracle" name="sample.rds.amazonaws.com">
                </DbServer>
                </sources>
                <targets>
                <DbServer vendor="aurora_postgresql" name="sample.aurora.amazonaws.com" />
                </targets>
            </entities>
            <relations>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema1"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table1"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema2"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table2"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema3"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table3"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
            </relations>
            </ProjectModel>
        </instances>
        </tree>
        """.trimIndent()

        val tempFile = createTempFile("valid-sctFile", ".xml").toFile()
        tempFile.writeText(sampleFileContents)

        val isValidMetadata = validateSctMetadata(tempFile)
        assertThat(isValidMetadata.valid).isTrue()
        assertThat(isValidMetadata.errorReason).isEmpty()
        assertThat(isValidMetadata.sourceVendor).isEqualTo("ORACLE")
        assertThat(isValidMetadata.targetVendor).isEqualTo("AURORA_POSTGRESQL")
        assertThat(isValidMetadata.sourceServerName).isEqualTo("sample.rds.amazonaws.com")
        assertThat(isValidMetadata.schemaOptions.containsAll(setOf("SCHEMA1", "SCHEMA2", "SCHEMA3"))).isTrue()
    }

    @Test
    fun `WHEN validateMetadataFile on sct file with invalid source DB THEN fails validation`() {
        val sampleFileContents = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tree>
        <instances>
            <ProjectModel>
            <entities>
                <sources>
                <DbServer vendor="oracle-invalid" name="sample.rds.amazonaws.com">
                </DbServer>
                </sources>
                <targets>
                <DbServer vendor="aurora_postgresql" name="sample.aurora.amazonaws.com" />
                </targets>
            </entities>
            <relations>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema1"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table1"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema2"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table2"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema3"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table3"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
            </relations>
            </ProjectModel>
        </instances>
        </tree>
        """.trimIndent()

        val tempFile = createTempFile("invalid-sctFile1", ".xml").toFile()
        tempFile.writeText(sampleFileContents)

        val isValidMetadata = validateSctMetadata(tempFile)
        assertThat(isValidMetadata.valid).isFalse()
        assertThat(isValidMetadata.errorReason.contains("I can only convert SQL for migrations from an Oracle source database")).isTrue()
    }

    @Test
    fun `WHEN validateMetadataFile on sct file with invalid target DB THEN fails validation`() {
        val sampleFileContents = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tree>
        <instances>
            <ProjectModel>
            <entities>
                <sources>
                <DbServer vendor="oracle" name="sample.rds.amazonaws.com">
                </DbServer>
                </sources>
                <targets>
                <DbServer vendor="aurora_postgresql-invalid" name="sample.aurora.amazonaws.com" />
                </targets>
            </entities>
            <relations>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema1"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table1"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema2"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table2"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
                <server-node-location>
                <FullNameNodeInfoList>
                    <nameParts>
                    <FullNameNodeInfo typeNode="schema" nameNode="schema3"/>
                    <FullNameNodeInfo typeNode="table" nameNode="table3"/>
                    </nameParts>
                </FullNameNodeInfoList>
                </server-node-location>
            </relations>
            </ProjectModel>
        </instances>
        </tree>
        """.trimIndent()

        val tempFile = createTempFile("invalid-sctFile2", ".xml").toFile()
        tempFile.writeText(sampleFileContents)

        val isValidMetadata = validateSctMetadata(tempFile)
        assertThat(isValidMetadata.valid).isFalse()
        assertThat(
            isValidMetadata.errorReason.contains("I can only convert SQL for migrations to Aurora PostgreSQL or Amazon RDS for PostgreSQL target databases")
        ).isTrue()
    }
}
