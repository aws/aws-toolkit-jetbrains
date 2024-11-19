// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationProgressUpdate
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformType
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getBillingText
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.getTableMapping
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.parseBuildFile
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.pollTransformationStatusAndPlan
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.refreshToken
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.validateSctMetadata
import software.aws.toolkits.jetbrains.utils.rules.addFileToModule
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempFile

class CodeWhispererCodeModernizerUtilsTest : CodeWhispererCodeModernizerTestBase() {
    @Before
    override fun setup() {
        super.setup()
    }

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
    fun `refresh on access denied`() {
        val mockAccessDeniedException = Mockito.mock(AccessDeniedException::class.java)

        mockkStatic(::refreshToken)
        every { refreshToken(any()) } just runs

        Mockito.doThrow(
            mockAccessDeniedException
        ).doReturn(
            exampleGetCodeMigrationResponse,
            exampleGetCodeMigrationResponse.replace(TransformationStatus.STARTED),
            exampleGetCodeMigrationResponse.replace(TransformationStatus.COMPLETED), // Should stop before this point
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
        io.mockk.verify { refreshToken(any()) }
    }

    @Test
    fun `refresh on invalid grant`() {
        val mockInvalidGrantException = Mockito.mock(InvalidGrantException::class.java)

        mockkStatic(::refreshToken)
        every { refreshToken(any()) } just runs

        Mockito.doThrow(
            mockInvalidGrantException
        ).doReturn(
            exampleGetCodeMigrationResponse,
            exampleGetCodeMigrationResponse.replace(TransformationStatus.STARTED),
            exampleGetCodeMigrationResponse.replace(TransformationStatus.COMPLETED), // Should stop before this point
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
        io.mockk.verify { refreshToken(any()) }
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
        val expected = mapOf("0" to jobStats, "1" to depChanges, "2" to apiChanges, "-1" to fileChanges)
        assertThat(expected).isEqualTo(actual)
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
