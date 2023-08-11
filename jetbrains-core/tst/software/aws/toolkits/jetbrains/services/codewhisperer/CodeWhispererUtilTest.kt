// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.countSubstringMatches
import software.aws.toolkits.jetbrains.services.codewhisperer.util.toCodeChunk
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class CodeWhispererUtilTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()

    lateinit var fixture: CodeInsightTestFixture

    @Before
    fun setup() {
        fixture = projectRule.fixture
    }

    @Test
    fun getFileDistance() {
        val targetFile = fixture.addFileToProject("service/microService/CodeWhispererFileContextProvider.java", aString())

        val fileWithDistance0 = fixture.addFileToProject("service/microService/CodeWhispererFileCrawler.java", aString())
        val fileWithDistance1 = fixture.addFileToProject("service/CodewhispererRecommendationService.java", aString())
        val fileWithDistance3 = fixture.addFileToProject("util/CodeWhispererConstants.java", aString())
        val fileWithDistance4 = fixture.addFileToProject("ui/popup/CodeWhispererPopupManager.java", aString())
        val fileWithDistance5 = fixture.addFileToProject("ui/popup/components/CodeWhispererPopup.java", aString())
        val fileWithDistance6 = fixture.addFileToProject("ui/popup/components/actions/AcceptRecommendationAction.java", aString())

        assertThat(CodeWhispererUtil.getFileDistance(targetFile.virtualFile, fileWithDistance0.virtualFile))
            .isEqualTo(0)

        assertThat(CodeWhispererUtil.getFileDistance(targetFile.virtualFile, fileWithDistance1.virtualFile))
            .isEqualTo(1)

        assertThat(CodeWhispererUtil.getFileDistance(targetFile.virtualFile, fileWithDistance3.virtualFile))
            .isEqualTo(3)

        assertThat(CodeWhispererUtil.getFileDistance(targetFile.virtualFile, fileWithDistance4.virtualFile))
            .isEqualTo(4)

        assertThat(CodeWhispererUtil.getFileDistance(targetFile.virtualFile, fileWithDistance5.virtualFile))
            .isEqualTo(5)

        assertThat(CodeWhispererUtil.getFileDistance(targetFile.virtualFile, fileWithDistance6.virtualFile))
            .isEqualTo(6)
    }

    @Test
    fun `test util countSubstringMatches`() {
        val elementsToCheck = listOf("apple", "pineapple", "banana", "chocolate", "fries", "laptop", "amazon", "codewhisperer", "aws")
        val targetElements = listOf(
            "an apple a day, keep doctors away",
            "codewhisperer is the best AI code generator",
            "chocolateCake",
            "green apple is sour",
            "pineapple juice",
            "chocolate cake is good"
        )

        val actual = countSubstringMatches(targetElements, elementsToCheck)
        assertThat(actual).isEqualTo(4)
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
        val psiFile = fixture.configureByText("Sample.java", codeSample33Lines)

        val result = runBlocking {
            psiFile.virtualFile.toCodeChunk("fake/path")
        }.toList()

        assertThat(result).hasSize(5)

        // 0th
        assertThat(result[0].content).isEqualTo(
            """public int runBinarySearchRecursively(int[] sortedArray, int key, int low, int high) {
                |    int middle = low  + ((high - low) / 2);
            """.trimMargin()
        )
        assertThat(result[0].path).isEqualTo("fake/path")
        assertThat(result[0].nextChunk).isEqualTo(result[1].content)

        // 1st
        assertThat(result[1].content).isEqualTo(
            """|public int runBinarySearchRecursively(int[] sortedArray, int key, int low, int high) {
                    |    int middle = low  + ((high - low) / 2);
                    |    
                    |    if (high < low) {
                    |        return -1;
                    |    }
                    |
                    |    if (key == sortedArray[middle]) {
                    |        return middle;
                    |    } else if (key < sortedArray[middle]) {
            """.trimMargin()
        )
        assertThat(result[1].path).isEqualTo("fake/path")
        assertThat(result[1].nextChunk).isEqualTo(result[2].content)

        // 2nd
        assertThat(result[2].content).isEqualTo(
            """|        return runBinarySearchRecursively(sortedArray, key, low, middle - 1);
               |    } else {
               |        return runBinarySearchRecursively(sortedArray, key, middle + 1, high);
               |    }
               |}
               |
               |public int runBinarySearchIteratively(int[] sortedArray, int key, int low, int high) {
               |    int index = Integer.MAX_VALUE;
               |    
               |    while (low <= high) {
            """.trimMargin()
        )
        assertThat(result[2].path).isEqualTo("fake/path")
        assertThat(result[2].nextChunk).isEqualTo(result[3].content)

        // 3rd
        assertThat(result[3].content).isEqualTo(
            """|        int mid = low  + ((high - low) / 2);
       |        if (sortedArray[mid] < key) {
       |            low = mid + 1;
       |        } else if (sortedArray[mid] > key) {
       |            high = mid - 1;
       |        } else if (sortedArray[mid] == key) {
       |            index = mid;
       |            break;
       |        }
       |     }
            """.trimMargin()
        )
        assertThat(result[3].path).isEqualTo("fake/path")
        assertThat(result[3].nextChunk).isEqualTo(result[4].content)

        // 4th
        assertThat(result[4].content).isEqualTo(
            """|    
               |    return index;
               |}
            """.trimMargin()
        )
        assertThat(result[4].path).isEqualTo("fake/path")
        assertThat(result[4].nextChunk).isEqualTo(result[4].content)
    }
}

private val codeSample33Lines =
    """public int runBinarySearchRecursively(int[] sortedArray, int key, int low, int high) {
       |    int middle = low  + ((high - low) / 2);
       |    
       |    if (high < low) {
       |        return -1;
       |    }
       |
       |    if (key == sortedArray[middle]) {
       |        return middle;
       |    } else if (key < sortedArray[middle]) {
       |        return runBinarySearchRecursively(sortedArray, key, low, middle - 1);
       |    } else {
       |        return runBinarySearchRecursively(sortedArray, key, middle + 1, high);
       |    }
       |}
       |
       |public int runBinarySearchIteratively(int[] sortedArray, int key, int low, int high) {
       |    int index = Integer.MAX_VALUE;
       |    
       |    while (low <= high) {
       |        int mid = low  + ((high - low) / 2);
       |        if (sortedArray[mid] < key) {
       |            low = mid + 1;
       |        } else if (sortedArray[mid] > key) {
       |            high = mid - 1;
       |        } else if (sortedArray[mid] == key) {
       |            index = mid;
       |            break;
       |        }
       |     }
       |    
       |    return index;
       |}
       |
    """.trimMargin()
