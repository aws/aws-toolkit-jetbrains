// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererLanguageManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererC
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCpp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCsharp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererDart
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererGo
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJson
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererKotlin
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererLua
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPhp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPlainText
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPowershell
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererR
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRuby
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRust
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererScala
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererShell
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererSql
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererSwift
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererSystemVerilog
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTf
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererVue
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererYaml
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererLanguage
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

class CodeWhispererLanguageManagerTest {
    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    @Rule
    @JvmField
    val projectRule = PythonCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    val manager = CodeWhispererLanguageManager()

    @Test
    fun `test CodeWhispererProgrammingLanguage should be singleton`() {
        val fileTypeMock = mock<FileType> {
            on { name } doReturn "java"
        }
        val vFileMock = mock<VirtualFile> {
            on { fileType } doReturn fileTypeMock
        }

        val lang1 = manager.getLanguage(vFileMock)
        val lang2 = manager.getLanguage(vFileMock)

        assertThat(lang1).isSameAs(lang2)
    }

    @Test
    fun `test getProgrammingLanguage(virtualFile) by fileType`() {
        testGetProgrammingLanguageUtil<CodeWhispererJava>(fileTypeNames = listOf("java", "Java", "JAVA"), fileExtensions = listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererPython>(listOf("python", "Python"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererJavaScript>(listOf("javascript", "JavaScript"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererJsx>(listOf("jsx harmony"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererTsx>(listOf("typescript jsx"), listOf("tsx"))
        testGetProgrammingLanguageUtil<CodeWhispererTypeScript>(listOf("typescript", "TypeScript"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererCsharp>(listOf("c#", "C#"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererGo>(listOf("go", "Go"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererKotlin>(listOf("kotlin", "Kotlin"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererPhp>(listOf("php", "Php"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererRuby>(listOf("ruby", "Ruby"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererScala>(listOf("scala", "Scala"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererSql>(listOf("sql", "Sql"), listOf(""))
//        testGetProgrammingLanguageUtil<CodeWhispererCpp>(listOf("c++"), listOf(""))
//        testGetProgrammingLanguageUtil<CodeWhispererC>(listOf("c"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererShell>(listOf("Shell"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererRust>(listOf("Rust"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererDart>(listOf("Dart"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererLua>(listOf("Lua"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererPowershell>(listOf("Powershell"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererR>(listOf("R"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererSwift>(listOf("Swift"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererSystemVerilog>(listOf("SystemVerilog"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererVue>(listOf("Vue"), listOf(""))
    }

    @Test
    fun `test getProgrammingLanguage(virtualFile) by fileExtensions`() {
        testGetProgrammingLanguageUtil<CodeWhispererJava>(fileTypeNames = listOf("foo"), fileExtensions = listOf("java"))
        testGetProgrammingLanguageUtil<CodeWhispererPython>(listOf("bar"), listOf("py"))
        testGetProgrammingLanguageUtil<CodeWhispererJavaScript>(listOf("baz"), listOf("js"))
        testGetProgrammingLanguageUtil<CodeWhispererJsx>(listOf("foo"), listOf("jsx"))
        testGetProgrammingLanguageUtil<CodeWhispererTsx>(listOf("foo"), listOf("tsx"))
        testGetProgrammingLanguageUtil<CodeWhispererTypeScript>(listOf("foo"), listOf("ts"))
        testGetProgrammingLanguageUtil<CodeWhispererCsharp>(listOf("foo"), listOf("cs"))
        testGetProgrammingLanguageUtil<CodeWhispererGo>(listOf("foo"), listOf("go"))
        testGetProgrammingLanguageUtil<CodeWhispererKotlin>(listOf("foo"), listOf("kt"))
        testGetProgrammingLanguageUtil<CodeWhispererPhp>(listOf("foo"), listOf("php"))
        testGetProgrammingLanguageUtil<CodeWhispererRuby>(listOf("foo"), listOf("rb"))
        testGetProgrammingLanguageUtil<CodeWhispererScala>(listOf("foo"), listOf("scala"))
        testGetProgrammingLanguageUtil<CodeWhispererSql>(listOf("foo"), listOf("sql"))
        testGetProgrammingLanguageUtil<CodeWhispererPlainText>(listOf("foo"), listOf("txt"))
        testGetProgrammingLanguageUtil<CodeWhispererCpp>(listOf("foo"), listOf("cpp", "c++", "cc"))
        testGetProgrammingLanguageUtil<CodeWhispererC>(listOf("foo"), listOf("c", "h"))
        testGetProgrammingLanguageUtil<CodeWhispererShell>(listOf("foo"), listOf("sh"))
        testGetProgrammingLanguageUtil<CodeWhispererRust>(listOf("foo"), listOf("rs"))
        testGetProgrammingLanguageUtil<CodeWhispererDart>(listOf("foo"), listOf("dart"))
        testGetProgrammingLanguageUtil<CodeWhispererLua>(listOf("foo"), listOf("lua", "wlua"))
        testGetProgrammingLanguageUtil<CodeWhispererPowershell>(listOf("foo"), listOf("ps1", "psm1"))
        testGetProgrammingLanguageUtil<CodeWhispererR>(listOf("foo"), listOf("r"))
        testGetProgrammingLanguageUtil<CodeWhispererSwift>(listOf("foo"), listOf("swift"))
        testGetProgrammingLanguageUtil<CodeWhispererSystemVerilog>(listOf("foo"), listOf("sv", "svh", "vh"))
        testGetProgrammingLanguageUtil<CodeWhispererVue>(listOf("foo"), listOf("vue"))
    }

    @Test
    fun `test getProgrammingLanguage(virtualFile) will fallback to unknown if either byFileType or byFileExtension works`() {
        testGetProgrammingLanguageUtil<CodeWhispererUnknownLanguage>(listOf("foo"), listOf("foo"))
        testGetProgrammingLanguageUtil<CodeWhispererUnknownLanguage>(listOf("foo"), listOf(""))
        testGetProgrammingLanguageUtil<CodeWhispererUnknownLanguage>(listOf(""), listOf("foo"))
    }

    @Test
    fun `test getProgrammingLanguage(virtualFile)`() {
        testGetProgrammingLanguageUtil<CodeWhispererJava>(listOf("java", "Java", "JAVA"), listOf("java"))
        testGetProgrammingLanguageUtil<CodeWhispererPython>(listOf("python", "Python"), listOf("py"))
        testGetProgrammingLanguageUtil<CodeWhispererJavaScript>(listOf("javascript", "JavaScript"), listOf("js"))
        testGetProgrammingLanguageUtil<CodeWhispererJsx>(listOf("jsx harmony"), listOf("jsx"))
        testGetProgrammingLanguageUtil<CodeWhispererTsx>(listOf("typescript jsx"), listOf("tsx"))
        testGetProgrammingLanguageUtil<CodeWhispererTypeScript>(listOf("typescript", "TypeScript"), listOf("ts"))
        testGetProgrammingLanguageUtil<CodeWhispererCsharp>(listOf("c#", "C#"), listOf("cs"))
        testGetProgrammingLanguageUtil<CodeWhispererGo>(listOf("go", "Go"), listOf("go"))
        testGetProgrammingLanguageUtil<CodeWhispererKotlin>(listOf("kotlin", "Kotlin"), listOf("kt"))
        testGetProgrammingLanguageUtil<CodeWhispererPhp>(listOf("php", "Php"), listOf("php"))
        testGetProgrammingLanguageUtil<CodeWhispererRuby>(listOf("ruby", "Ruby"), listOf("rb"))
        testGetProgrammingLanguageUtil<CodeWhispererScala>(listOf("scala", "Scala"), listOf("scala"))
        testGetProgrammingLanguageUtil<CodeWhispererSql>(listOf("sql", "Sql"), listOf("sql"))
        testGetProgrammingLanguageUtil<CodeWhispererPlainText>(listOf("plain_text"), listOf("txt"))
        testGetProgrammingLanguageUtil<CodeWhispererCpp>(listOf("c++"), listOf("cpp", "c++", "cc"))
        testGetProgrammingLanguageUtil<CodeWhispererC>(listOf("c"), listOf("c", "h"))
        testGetProgrammingLanguageUtil<CodeWhispererShell>(listOf("Shell Script"), listOf("sh"))
        testGetProgrammingLanguageUtil<CodeWhispererRust>(listOf("Rust"), listOf("rs"))
        testGetProgrammingLanguageUtil<CodeWhispererDart>(listOf("Dart"), listOf("dart"))
        testGetProgrammingLanguageUtil<CodeWhispererLua>(listOf("Lua"), listOf("lua", "wlua"))
        testGetProgrammingLanguageUtil<CodeWhispererPowershell>(listOf("Powershell"), listOf("ps1", "psm1"))
        testGetProgrammingLanguageUtil<CodeWhispererR>(listOf("R"), listOf("r"))
        testGetProgrammingLanguageUtil<CodeWhispererSwift>(listOf("Swift"), listOf("swift"))
        testGetProgrammingLanguageUtil<CodeWhispererSystemVerilog>(listOf("SystemVerilog"), listOf("sv", "svh", "vh"))
        testGetProgrammingLanguageUtil<CodeWhispererVue>(listOf("Vue"), listOf("vue"))
    }

    @Test
    fun `psiFile passed to getProgrammingLanguage(psiFile) returns null`() {
        // psiFile.virtualFile potentially will return null if virtualFile only exist in the memory instead of the disk
        val psiFileMock = mock<PsiFile> {
            on { virtualFile } doReturn null
            on { name } doReturn "my_python_script_1.py"
        }
        assertThat(manager.getLanguage(psiFileMock)).isInstanceOf(CodeWhispererPython::class.java)
    }

    private inline fun <reified T : CodeWhispererProgrammingLanguage> testGetProgrammingLanguageUtil(
        fileTypeNames: List<String>,
        fileExtensions: List<String?>?,
    ) {
        fileExtensions?.forEach { fileExtension ->
            fileTypeNames.forEach { fileTypeName ->
                val fileTypeMock = mock<FileType> {
                    on { name } doReturn fileTypeName
                }
                val vFileMock = mock<VirtualFile> {
                    on { fileType } doReturn fileTypeMock
                    on { extension } doReturn fileExtension
                }
                assertThat(manager.getLanguage(vFileMock)).isInstanceOf(T::class.java)
            }
        }
    }
}

class CodeWhispererProgrammingLanguageTest {
    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    val suts = listOf(
        CodeWhispererC.INSTANCE,
        CodeWhispererCpp.INSTANCE,
        CodeWhispererCsharp.INSTANCE,
        CodeWhispererDart.INSTANCE,
        CodeWhispererGo.INSTANCE,
        CodeWhispererJava.INSTANCE,
        CodeWhispererJavaScript.INSTANCE,
        CodeWhispererJson.INSTANCE,
        CodeWhispererJsx.INSTANCE,
        CodeWhispererKotlin.INSTANCE,
        CodeWhispererLua.INSTANCE,
        CodeWhispererPhp.INSTANCE,
        CodeWhispererPlainText.INSTANCE,
        CodeWhispererPowershell.INSTANCE,
        CodeWhispererPython.INSTANCE,
        CodeWhispererR.INSTANCE,
        CodeWhispererRuby.INSTANCE,
        CodeWhispererRust.INSTANCE,
        CodeWhispererScala.INSTANCE,
        CodeWhispererShell.INSTANCE,
        CodeWhispererSql.INSTANCE,
        CodeWhispererSwift.INSTANCE,
        CodeWhispererSystemVerilog.INSTANCE,
        CodeWhispererTf.INSTANCE,
        CodeWhispererTsx.INSTANCE,
        CodeWhispererTypeScript.INSTANCE,
        CodeWhispererUnknownLanguage.INSTANCE,
        CodeWhispererVue.INSTANCE,
        CodeWhispererYaml.INSTANCE,
    )

    class TestLanguage : CodeWhispererProgrammingLanguage() {
        override val languageId: String = "test-language"
        override fun toTelemetryType(): CodewhispererLanguage = CodewhispererLanguage.Unknown
    }

    @Test
    fun `test language inline completion support`() {
        suts.forEach { sut ->
            val expected = when (sut) {
                // supported
                is CodeWhispererC,
                is CodeWhispererCpp,
                is CodeWhispererCsharp,
                is CodeWhispererGo,
                is CodeWhispererJava,
                is CodeWhispererJavaScript,
                is CodeWhispererJson,
                is CodeWhispererJsx,
                is CodeWhispererKotlin,
                is CodeWhispererPhp,
                is CodeWhispererPython,
                is CodeWhispererRuby,
                is CodeWhispererRust,
                is CodeWhispererScala,
                is CodeWhispererShell,
                is CodeWhispererSql,
                is CodeWhispererTf,
                is CodeWhispererTsx,
                is CodeWhispererTypeScript,
                is CodeWhispererYaml,
                is CodeWhispererDart,
                is CodeWhispererLua,
                is CodeWhispererPowershell,
                is CodeWhispererR,
                is CodeWhispererSwift,
                is CodeWhispererSystemVerilog,
                is CodeWhispererVue,
                -> true

                // not supported
                is CodeWhispererPlainText, is CodeWhispererUnknownLanguage -> false

                else -> false
            }

            assertThat(sut.isCodeCompletionSupported()).isEqualTo(expected)
        }
    }

    @Test
    fun `test language crossfile support`() {
        suts.forEach { sut ->
            val expected = when (sut) {
                is CodeWhispererJava,
                is CodeWhispererJavaScript,
                is CodeWhispererJsx,
                is CodeWhispererPython,
                is CodeWhispererTsx,
                is CodeWhispererTypeScript,
                -> true

                else -> false
            }

            assertThat(sut.isSupplementalContextSupported()).isEqualTo(expected)
        }
    }

    @Test
    fun `test language utg support`() {
        suts.forEach { sut ->
            val expected = when (sut) {
                is CodeWhispererJava,
                is CodeWhispererPython,
                -> true

                else -> false
            }

            assertThat(sut.isUTGSupported()).isEqualTo(expected)
        }
    }

    @Test
    fun `test CodeWhispererProgrammingLanguage isEqual will compare its languageId`() {
        val instance1: CodeWhispererJava = CodeWhispererJava.INSTANCE
        val instance2: CodeWhispererJava
        CodeWhispererJava::class.apply {
            val constructor = primaryConstructor
            constructor?.isAccessible = true
            instance2 = this.createInstance()
        }

        assertThat(instance1).isNotSameAs(instance2)
        assertThat(instance1).isEqualTo(instance2)
    }

    @Test
    fun `test any class extending CodeWhispererProgrammingLanguage isEqual will compare its languageId`() {
        val instance1: TestLanguage
        val instance2: TestLanguage
        TestLanguage::class.apply {
            val constructor = primaryConstructor
            constructor?.isAccessible = true
            instance1 = this.createInstance()
            instance2 = this.createInstance()
        }

        assertThat(instance1).isNotSameAs(instance2)
        assertThat(instance1).isEqualTo(instance2)
    }

    @Test
    fun `test hashCode`() {
        val set = mutableSetOf<CodeWhispererProgrammingLanguage>()
        val instance1 = CodeWhispererJava.INSTANCE
        val instance2: CodeWhispererProgrammingLanguage
        CodeWhispererJava::class.apply {
            val constructor = primaryConstructor
            constructor?.isAccessible = true
            instance2 = this.createInstance()
        }

        set.add(instance1)
        val flag = set.contains(instance2)
        assertThat(flag).isTrue
    }

    companion object {
        val EP_NAME = ExtensionPointName<CodeWhispererProgrammingLanguage>("amazon.q.codewhisperer.programmingLanguage")
    }
}
