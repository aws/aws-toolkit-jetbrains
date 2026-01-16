// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codetest

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import software.amazon.q.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.amazon.q.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.amazon.q.jetbrains.utils.rules.addFileToModule
import software.amazon.q.jetbrains.utils.rules.addModule
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.sessionconfig.CodeTestSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

class CodeTestSessionConfigTest {
    private lateinit var testJava: VirtualFile
    private lateinit var utilsJava: VirtualFile
    private lateinit var readMeMd: VirtualFile
    private lateinit var utilsJs: VirtualFile
    private lateinit var helperPy: VirtualFile

    private var totalSize: Long = 0
    private var totalLines: Long = 0

    @Rule
    @JvmField
    val projectRule: CodeInsightTestFixtureRule = HeavyJavaCodeInsightTestFixtureRule()

    private lateinit var project: Project
    private lateinit var codeTestSessionConfig: CodeTestSessionConfig

    @Before
    fun setUp() {
        setupTestProject()
        project = projectRule.project
        codeTestSessionConfig = spy(CodeTestSessionConfig(testJava, project))
    }

    @Test
    fun `test createPayload`() {
        assertThat(project.modules.size).isEqualTo(2)
        val payload = codeTestSessionConfig.createPayload()
        assertThat(payload).isNotNull
        assertThat(payload.context.totalFiles).isEqualTo(5)

        assertThat(payload.context.scannedFiles.size).isEqualTo(5)
        assertThat(payload.context.scannedFiles).contains(
            testJava,
            utilsJava,
            readMeMd,
            utilsJs,
            helperPy
        )

        assertThat(payload.context.srcPayloadSize).isEqualTo(totalSize)
        assertThat(payload.context.totalLines).isEqualTo(totalLines)
        assertThat(payload.srcZip).isNotNull()

        val bufferedInputStream = BufferedInputStream(payload.srcZip.inputStream())
        val zis = ZipInputStream(bufferedInputStream)
        var filesInZip = 0
        while (zis.nextEntry != null) {
            filesInZip += 1
        }

        assertThat(filesInZip).isEqualTo(9)
    }

    @Test
    fun `getProjectPayloadMetadata()`() {
        val payloadMetadata = codeTestSessionConfig.getProjectPayloadMetadata()
        assertThat(payloadMetadata).isNotNull()
        val includedSourceFiles = payloadMetadata.sourceFiles
        val srcPayloadSize = payloadMetadata.payloadSize
        val totalLines = payloadMetadata.linesScanned
        val maxCountLanguage = payloadMetadata.language
        assertThat(includedSourceFiles.size).isEqualTo(5)
        assertThat(srcPayloadSize).isEqualTo(totalSize)
        assertThat(totalLines).isEqualTo(totalLines)
        assertThat(maxCountLanguage).isEqualTo(testJava.programmingLanguage().toTelemetryType())
    }

    @Test
    fun `selected file larger than payload limit throws exception`() {
        codeTestSessionConfig.stub {
            onGeneric { getPayloadLimitInBytes() }.thenReturn(10)
        }
        assertThrows<CodeTestException> {
            codeTestSessionConfig.createPayload()
        }
    }

    private fun setupTestProject() {
        val testModule = projectRule.fixture.addModule("testModule")
        val testModule2 = projectRule.fixture.addModule("testModule2")
        testJava = projectRule.fixture.addFileToModule(
            testModule,
            "/Test.java",
            """
            using Utils;
            using Helpers.Helper;

            int a = 1;
            int b = 2;

            int c = Utils.Add(a, b);
            int d = Helper.Subtract(a, b);
            int e = Utils.Fib(5);
            """.trimIndent()
        ).virtualFile
        totalSize += testJava.length
        totalLines += testJava.toNioPath().toFile().readLines().size

        utilsJava = projectRule.fixture.addFileToModule(
            testModule,
            "/Utils.java",
            """
            public class Utils {
                public static int add(int a, int b) {
                    return a + b;
                }

                public static int fib(int n) {
                    if (n <= 0) return 0;
                    if (n == 1 || n == 2) {
                        return 1;
                    }
                    return add(fib(n - 1), fib(n - 2));
                }
            }
            """.trimIndent()
        ).virtualFile
        totalSize += utilsJava.length
        totalLines += utilsJava.toNioPath().toFile().readLines().size

        projectRule.fixture.addFileToModule(
            testModule,
            "/Helpers/Helper.java",
            """
            public class Helper {
                public static int subtract(int a, int b) {
                    return a - b;
                }

                public static int multiply(int a, int b) {
                    return a * b;
                }

                public static int divide(int a, int b) {
                    return a / b;
                }

                public static void bubbleSort(int[] arr) {
                    int n = arr.length;
                    for (int i = 0; i < n - 1; i++) {
                        for (int j = 0; j < n - i - 1; j++) {
                            if (arr[j] > arr[j + 1]) {
                                // Swap arr[j] and arr[j + 1]
                                int temp = arr[j];
                                arr[j] = arr[j + 1];
                                arr[j + 1] = temp;
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        ).virtualFile

        utilsJs = projectRule.fixture.addFileToModule(
            testModule,
            "/utils.js",
            """
            function add(num1, num2) {
              return num1 + num2;
            }

            function bblSort(arr) {
                for(var i = 0; i < arr.length; i++) {
                    // Last i elements are already in place
                    for(var j = 0; j < ( arr.length - i -1 ); j++) {
                        // Checking if the item at present iteration
                        // is greater than the next iteration
                        if(arr[j] > arr[j+1]) {
                            // If the condition is true then swap them
                            var temp = arr[j]
                            arr[j] = arr[j + 1]
                            arr[j+1] = temp
                        }
                    }
                }
                // Print the sorted array
                console.log(arr);
            }
            """.trimIndent()
        ).virtualFile
        totalSize += utilsJs.length
        totalLines += utilsJs.toNioPath().toFile().readLines().size

        projectRule.fixture.addFileToModule(
            testModule,
            "/Helpers/test3Json.json",
            """
                {
                    "AWSTemplateFormatVersion": "2010-09-09",
                    "Description": "This stack creates a SQS queue using KMS encryption\n",
                    "Parameters": {
                        "KmsKey": {
                            "Description": "The KMS key master ID",
                            "Type": "String"
                        }
                    },
                    "Resources": {
                        "Queue": {
                            "DeletionPolicy": "Retain",
                            "UpdateReplacePolicy": "Retain",
                            "Type": "AWS::SQS::Queue",
                            "Properties": {
                                "DelaySeconds": 0,
                                "FifoQueue": false,
                                "KmsDataKeyReusePeriodSeconds": 300,
                                "KmsMasterKeyId": {
                                    "Ref": "KmsKey"
                                },
                                "MaximumMessageSize": 262144,
                                "MessageRetentionPeriod": 345600,
                                "ReceiveMessageWaitTimeSeconds": 0,
                                "VisibilityTimeout": 30
                            }
                        },
                        "FifoQueue": {
                            "DeletionPolicy": "Retain",
                            "UpdateReplacePolicy": "Retain",
                            "Type": "AWS::SQS::Queue",
                            "Properties": {
                                "ContentBasedDeduplication": true,
                                "DelaySeconds": 0,
                                "FifoQueue": true,
                                "KmsDataKeyReusePeriodSeconds": 300,
                                "KmsMasterKeyId": {
                                    "Ref": "KmsKey"
                                },
                                "MaximumMessageSize": 262144,
                                "MessageRetentionPeriod": 345600,
                                "ReceiveMessageWaitTimeSeconds": 0,
                                "VisibilityTimeout": 30
                            }
                        }
                    }
                }
            """.trimIndent()
        ).virtualFile

        helperPy = projectRule.fixture.addFileToModule(
            testModule2,
            "/HelpersInPython/helper.py", // False positive testing
            """
            from helpers import helper as h
            def subtract(num1, num2)
                return num1 - num2

            def fib(num):
                if num == 0: return 0
                if num in [1,2]: return 1
                return h.add(fib(num-1), fib(num-2))

            """.trimIndent()
        ).virtualFile
        totalSize += helperPy.length
        totalLines += helperPy.toNioPath().toFile().readLines().size

        readMeMd = projectRule.fixture.addFileToModule(testModule, "/ReadMe.md", "### Now included").virtualFile
        totalSize += readMeMd.length
        totalLines += readMeMd.toNioPath().toFile().readLines().size

        // Adding gitignore file and gitignore file member for testing.
        // The tests include the markdown file but not these two files.
        projectRule.fixture.addFileToModule(
            testModule,
            "/.gitignore",
            """
                Helpers
                .idea
                .vscode
                .DS_Store
            """.trimIndent()
        ).virtualFile

        projectRule.fixture.addFileToModule(
            testModule2,
            "/.gitignore",
            """
                Helpers
                .idea
                .vscode
                .DS_Store
            """.trimIndent()
        ).virtualFile

        projectRule.fixture.addFileToModule(testModule2, "/.idea/ref", "ref: refs/heads/main") // adding ignored files in second module
    }
}
