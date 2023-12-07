
import io.mockk.every
import io.mockk.mockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class StaticClass {
    companion object {
        @JvmStatic
        fun testStaticMethod(): Int {
            print("why is this running still??")
            throw Exception("test")
            return 10
        }
    }
}

class Test {
    @Before
    fun setup() {
        // mockkStatic(StaticClass::class)
        // mockkStatic(StaticClass::testStaticMethod)
        // mockkObject(StaticClass)

        // every { StaticClass.testStaticMethod() } returns Unit
        // every { StaticClass.testStaticMethod() } just Runs
    }

    @Test
    fun `processClearQuickAction calls deleteSession and recordTelemetryChatRunCommand`() {
        mockkObject(StaticClass.Companion) {
            every { StaticClass.testStaticMethod() } returns 4
            assertThat(StaticClass.testStaticMethod()).isEqualTo(4)
        }
    }

    /*
    @Test
    fun `processClearQuickAction calls deleteSession and recordTelemetryChatRunCommand`() {
        StaticClass.testStaticMethod()
        Assertions.assertThat(true).isEqualTo(true)
    }
     */
}


