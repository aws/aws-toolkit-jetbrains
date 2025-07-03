// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.model.IdeDiagnostic
import software.amazon.awssdk.services.codewhispererruntime.model.OptOutPreference
import software.amazon.awssdk.services.codewhispererruntime.model.Position
import software.amazon.awssdk.services.codewhispererruntime.model.Range
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.aws.toolkits.core.utils.test.aStringWithLineCount
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCompletionType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getTelemetryOptOutPreference
import software.aws.toolkits.jetbrains.services.codewhisperer.util.convertSeverity
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getDiagnosticDifferences
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getDiagnosticsType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.isWithin
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.util.toCodeChunk
import software.aws.toolkits.jetbrains.services.codewhisperer.util.truncateLineByLine
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererCompletionType

class CodeWhispererUtilTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()

    @JvmField
    @Rule
    val clientManager = MockClientManagerRule()

    @JvmField
    @Rule
    val regionProvider = MockRegionProviderRule()

    lateinit var fixture: CodeInsightTestFixture
    private var isTelemetryEnabledDefault: Boolean = false

    @Before
    fun setup() {
        regionProvider.addRegion(Region.US_EAST_1)
        fixture = projectRule.fixture

        clientManager.create<SsoOidcClient>()
        isTelemetryEnabledDefault = AwsSettings.getInstance().isTelemetryEnabled
    }

    @After
    fun tearDown() {
        AwsSettings.getInstance().isTelemetryEnabled = isTelemetryEnabledDefault
    }

    @Test
    fun `truncateLineByLine should drop the last line if max length is greater than threshold`() {
        val input: String = """
            ${"a".repeat(11)}
            ${"b".repeat(11)}
            ${"c".repeat(11)}
            ${"d".repeat(11)}
            ${"e".repeat(11)}
        """.trimIndent()
        assertThat(input.length).isGreaterThan(50)
        val actual = truncateLineByLine(input, 50)
        assertThat(actual).isEqualTo(
            """
            ${"a".repeat(11)}
            ${"b".repeat(11)}
            ${"c".repeat(11)}
            ${"d".repeat(11)}
            """.trimIndent()
        )

        val input2 = "b\n".repeat(10)
        val actual2 = truncateLineByLine(input2, 8)
        assertThat(actual2.length).isEqualTo(8)
    }

    @Test
    fun `truncateLineByLine should return empty if empty string is provided`() {
        val input = ""
        val actual = truncateLineByLine(input, 50)
        assertThat(actual).isEqualTo("")
    }

    @Test
    fun `truncateLineByLine should return empty if 0 max length is provided`() {
        val input = "aaaaa"
        val actual = truncateLineByLine(input, 0)
        assertThat(actual).isEqualTo("")
    }

    @Test
    fun `truncateLineByLine should return flip the value if negative max length is provided`() {
        val input = "aaaaa\nbbbbb"
        val actual = truncateLineByLine(input, -6)
        val expected1 = truncateLineByLine(input, 6)
        assertThat(actual).isEqualTo(expected1)
        assertThat(actual).isEqualTo("aaaaa")
    }

    @Test
    fun `checkIfIdentityCenterLoginOrTelemetryEnabled will execute callback if the connection is IamIdentityCenter`() {
        val modificationTracker = SimpleModificationTracker()
        val oldCount = modificationTracker.modificationCount

        val ssoConn = LegacyManagedBearerSsoConnection(startUrl = "fake url", region = "us-east-1", scopes = Q_SCOPES)

        runIfIdcConnectionOrTelemetryEnabled(ssoConn) { modificationTracker.incModificationCount() }

        val newCount = modificationTracker.modificationCount
        assertThat(newCount).isEqualTo(oldCount + 1L)
    }

    @Test
    fun `checkIfIdentityCenterLoginOrTelemetryEnabled will return null if the connection is not IamIdentityCenter and telemetry not enabled`() {
        val modificationTracker = SimpleModificationTracker()
        val oldCount = modificationTracker.modificationCount

        val builderIdConn = LegacyManagedBearerSsoConnection(startUrl = SONO_URL, region = SONO_REGION, scopes = Q_SCOPES)
        AwsSettings.getInstance().isTelemetryEnabled = false
        runIfIdcConnectionOrTelemetryEnabled(builderIdConn) { modificationTracker.incModificationCount() }

        val newCount = modificationTracker.modificationCount
        assertThat(newCount).isEqualTo(oldCount)
        fixture = projectRule.fixture
    }

    @Test
    fun `toCodeChunk case_1`() {
        val psiFile = fixture.configureByText(
            "Sample.java",
            """public class Main {
            |    public static void main() {
            |    }
            |}
            """.trimMargin()
        )

        val result = runBlocking {
            psiFile.virtualFile.toCodeChunk("fake/path")
        }.toList()

        assertThat(result).hasSize(2)

        assertThat(result[0].content).isEqualTo(
            """public class Main {
                |    public static void main() {
                |    }
            """.trimMargin()
        )
        assertThat(result[1].content).isEqualTo(
            """public class Main {
            |    public static void main() {
            |    }
            |}
            """.trimMargin()
        )
    }

    @Test
    fun `toCodeChunk case_2`() {
        val fakeCodeWith210Lines = aStringWithLineCount(210)
        val psiFile = fixture.configureByText("Sample.java", fakeCodeWith210Lines)

        val result = runBlocking {
            psiFile.virtualFile.toCodeChunk("fake/path")
        }.toList()

        // 210 / 50 + 2
        assertThat(result).hasSize(6)

        // 0th
        assertThat(result[0].content).isEqualTo(aStringWithLineCount(3))
        assertThat(result[0].path).isEqualTo("fake/path")
        assertThat(result[0].nextChunk).isEqualTo(aStringWithLineCount(50, start = 0))

        // 1st
        assertThat(result[1].content).isEqualTo(aStringWithLineCount(50, start = 0))
        assertThat(result[1].path).isEqualTo("fake/path")
        assertThat(result[1].nextChunk).isEqualTo(aStringWithLineCount(50, start = 50))

        // 2nd
        assertThat(result[2].content).isEqualTo(aStringWithLineCount(50, start = 50))
        assertThat(result[2].path).isEqualTo("fake/path")
        assertThat(result[2].nextChunk).isEqualTo(aStringWithLineCount(50, start = 100))

        // 3rd
        assertThat(result[3].content).isEqualTo(aStringWithLineCount(50, start = 100))
        assertThat(result[3].path).isEqualTo("fake/path")
        assertThat(result[3].nextChunk).isEqualTo(aStringWithLineCount(50, start = 150))

        // 4th
        assertThat(result[4].content).isEqualTo(aStringWithLineCount(50, start = 150))
        assertThat(result[4].path).isEqualTo("fake/path")
        assertThat(result[4].nextChunk).isEqualTo(aStringWithLineCount(10, start = 200))

        // 5th
        assertThat(result[5].content).isEqualTo(aStringWithLineCount(10, start = 200))
        assertThat(result[5].path).isEqualTo("fake/path")
        assertThat(result[5].nextChunk).isEqualTo(aStringWithLineCount(10, start = 200))
    }

    @Test
    fun `test getCompletionType() should give Block completion type to multi-line completions that has at least two non-blank lines`() {
        assertThat(getCompletionType(aCompletion("test\n\n\t\nanother test"))).isEqualTo(CodewhispererCompletionType.Block)
        assertThat(getCompletionType(aCompletion("test\ntest\n"))).isEqualTo(CodewhispererCompletionType.Block)
        assertThat(getCompletionType(aCompletion("\n   \t\r\ntest\ntest"))).isEqualTo(CodewhispererCompletionType.Block)
    }

    @Test
    fun `test getCompletionType() should give Line completion type to line completions`() {
        assertThat(getCompletionType(aCompletion("test"))).isEqualTo(CodewhispererCompletionType.Line)
        assertThat(getCompletionType(aCompletion("test\n\t   "))).isEqualTo(CodewhispererCompletionType.Line)
    }

    @Test
    fun `test getCompletionType() should give Line completion type to multi-line completions that has at most 1 non-blank line`() {
        assertThat(getCompletionType(aCompletion("test\n\t"))).isEqualTo(CodewhispererCompletionType.Line)
        assertThat(getCompletionType(aCompletion("test\n    "))).isEqualTo(CodewhispererCompletionType.Line)
        assertThat(getCompletionType(aCompletion("test\n\r"))).isEqualTo(CodewhispererCompletionType.Line)
        assertThat(getCompletionType(aCompletion("\n\n\n\ntest"))).isEqualTo(CodewhispererCompletionType.Line)
    }

    @Test
    fun `test getTelemetryOptOutPreference() returns correct status based on AwsTelemetry`() {
        AwsSettings.getInstance().isTelemetryEnabled = true
        assertThat(AwsSettings.getInstance().isTelemetryEnabled).isTrue
        assertThat(getTelemetryOptOutPreference()).isEqualTo(OptOutPreference.OPTIN)

        AwsSettings.getInstance().isTelemetryEnabled = false
        assertThat(AwsSettings.getInstance().isTelemetryEnabled).isFalse
        assertThat(getTelemetryOptOutPreference()).isEqualTo(OptOutPreference.OPTOUT)
    }

    @Test
    fun `test isWithin() returns true if file is within the given directory`() {
        val projectRoot = fixture.tempDirFixture.findOrCreateDir("workspace/projectA")
        val file = fixture.addFileToProject("workspace/projectA/src/Sample.java", "").virtualFile
        assertThat(file.isWithin(projectRoot)).isTrue()
    }

    @Test
    fun `test isWithin() returns false if file is not within the given directory`() {
        val projectRoot = fixture.tempDirFixture.findOrCreateDir("workspace/projectA")
        val file = fixture.addFileToProject("workspace/projectB/src/Sample.java", "").virtualFile
        assertThat(file.isWithin(projectRoot)).isFalse()
    }

    @Test
    fun `test isWithin() returns false if file is not within the given directory but has the same prefix`() {
        val projectRoot = fixture.tempDirFixture.findOrCreateDir("workspace/projectA")
        val file = fixture.addFileToProject("workspace/projectA1/src/Sample.java", "").virtualFile
        assertThat(file.isWithin(projectRoot)).isFalse()
    }

    @Test
    fun `getDiagnosticsType correctly identifies syntax errors`() {
        val messages = listOf(
            "Expected semicolon at end of line",
            "Incorrect indent level",
            "Syntax error in expression"
        )

        messages.forEach { message ->
            assertThat(getDiagnosticsType(message)).isEqualTo("SYNTAX_ERROR")
        }
    }

    @Test
    fun `getDiagnosticsType correctly identifies type errors`() {
        val messages = listOf(
            "Cannot cast String to Int",
            "Type mismatch: expected String but got Int"
        )

        messages.forEach { message ->
            assertThat(getDiagnosticsType(message)).isEqualTo("TYPE_ERROR")
        }
    }

    @Test
    fun `getDiagnosticsType returns OTHER for unrecognized patterns`() {
        val message = "Some random message"
        assertThat(getDiagnosticsType(message)).isEqualTo("OTHER")
    }

    @Test
    fun `convertSeverity correctly maps severity levels`() {
        assertThat(convertSeverity(HighlightSeverity.ERROR)).isEqualTo("ERROR")
        assertThat(convertSeverity(HighlightSeverity.WARNING)).isEqualTo("WARNING")
        assertThat(convertSeverity(HighlightSeverity.INFORMATION)).isEqualTo("INFORMATION")
        assertThat(convertSeverity(HighlightSeverity.INFO)).isEqualTo("INFORMATION")
    }

    @Test
    fun `getDiagnosticDifferences correctly identifies added and removed diagnostics`() {
        val diagnostic1 = IdeDiagnostic.builder()
            .ideDiagnosticType("SYNTAX_ERROR")
            .severity("ERROR")
            .source("inspection1")
            .range(
                Range.builder()
                    .start(Position.builder().line(0).character(0).build())
                    .end(Position.builder().line(0).character(10).build())
                    .build()
            )
            .build()

        val diagnostic2 = IdeDiagnostic.builder()
            .ideDiagnosticType("TYPE_ERROR")
            .severity("WARNING")
            .source("inspection2")
            .range(
                Range.builder()
                    .start(Position.builder().line(1).character(0).build())
                    .end(Position.builder().line(1).character(10).build())
                    .build()
            )
            .build()

        val oldList = listOf(diagnostic1)
        val newList = listOf(diagnostic2)

        val differences = getDiagnosticDifferences(oldList, newList)

        assertThat(differences.added).containsExactly(diagnostic2)
        assertThat(differences.removed).containsExactly(diagnostic1)
    }

    @Test
    fun `getDiagnosticDifferences handles empty lists`() {
        val diagnostic = IdeDiagnostic.builder()
            .ideDiagnosticType("SYNTAX_ERROR")
            .severity("ERROR")
            .source("inspection1")
            .range(
                Range.builder()
                    .start(Position.builder().line(0).character(0).build())
                    .end(Position.builder().line(0).character(10).build())
                    .build()
            )
            .build()

        val emptyList = emptyList<IdeDiagnostic>()
        val nonEmptyList = listOf(diagnostic)

        val differencesWithEmptyOld = getDiagnosticDifferences(emptyList, nonEmptyList)
        assertThat(differencesWithEmptyOld.added).containsExactly(diagnostic)
        assertThat(differencesWithEmptyOld.removed).isEmpty()

        val differencesWithEmptyNew = getDiagnosticDifferences(nonEmptyList, emptyList)
        assertThat(differencesWithEmptyNew.added).isEmpty()
        assertThat(differencesWithEmptyNew.removed).containsExactly(diagnostic)
    }

    @Test
    fun `getDiagnosticDifferences handles identical lists`() {
        val diagnostic = IdeDiagnostic.builder()
            .ideDiagnosticType("SYNTAX_ERROR")
            .severity("ERROR")
            .source("inspection1")
            .range(
                Range.builder()
                    .start(Position.builder().line(0).character(0).build())
                    .end(Position.builder().line(0).character(10).build())
                    .build()
            )
            .build()

        val list = listOf(diagnostic)
        val differences = getDiagnosticDifferences(list, list)

        assertThat(differences.added).isEmpty()
        assertThat(differences.removed).isEmpty()
    }
}
