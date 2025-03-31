import org.junit.Test;
import static org.junit.Assert.*;

public class HappyPathTest {

    /**
     * Tests the behavior of the multiply method with extreme large values.
     * This test verifies that the method can handle multiplication of very large numbers
     * without throwing exceptions or producing unexpected results.
     */
    @Test
    public void testMultiplyWithExtremeValues() {
        double result = HappyPath.multiply(Double.MAX_VALUE, 2);
        assertTrue("Multiplication with extreme values should result in Infinity", Double.isInfinite(result));
    }

    /**
     * Tests the multiply method with positive numbers.
     * Verifies that the method correctly multiplies two positive double values.
     */
    @Test
    public void test_multiply_positive_numbers() {
        double result = HappyPath.multiply(2.5, 3.0);
        assertEquals(7.5, result, 0.0001);
    }

}
