import org.junit.Test;
import static org.junit.Assert.*;

public class HappyPathTest {

    /**
     * Test case for multiply method with positive numbers
     * Verifies that the method correctly multiplies two positive double values
     */
    @Test
    public void test_multiply_positive_numbers() {
        double result = HappyPath.multiply(2.5, 3.0);
        assertEquals(7.5, result, 0.0001);
    }


    /**
     * Tests the multiply method with positive numbers.
     * Verifies that the method correctly multiplies two positive double values.
     */
    @Test
    public void test_multiply_1() {
        double result = HappyPath.multiply(2.5, 3.0);
        assertEquals(7.5, result, 0.0001);
    }

    /**
     * Tests the multiply method with the largest possible double values.
     * This verifies that the method handles extreme input values correctly.
     */
    @Test
    public void testMultiplyWithMaxDoubleValues() {
        double result = HappyPath.multiply(Double.MAX_VALUE, Double.MAX_VALUE);
        assertEquals(Double.POSITIVE_INFINITY, result, 0);
    }

    /**
     * Tests the multiply method with the smallest possible double values.
     * This verifies that the method handles very small input values correctly.
     */
    @Test
    public void testMultiplyWithMinDoubleValues() {
        double result = HappyPath.multiply(Double.MIN_VALUE, Double.MIN_VALUE);
        assertEquals(0.0, result, 0);
    }

    /**
     * Tests the multiply method with NaN as input.
     * This verifies that the method handles NaN correctly.
     */
    @Test
    public void testMultiplyWithNaN() {
        double result = HappyPath.multiply(Double.NaN, 1.0);
        assertTrue(Double.isNaN(result));
    }

    /**
     * Tests the multiply method with negative infinity as input.
     * This verifies that the method handles negative infinity correctly.
     */
    @Test
    public void testMultiplyWithNegativeInfinity() {
        double result = HappyPath.multiply(Double.NEGATIVE_INFINITY, 1.0);
        assertEquals(Double.NEGATIVE_INFINITY, result, 0);
    }

    /**
     * Tests the multiply method with positive infinity as input.
     * This verifies that the method handles infinity correctly.
     */
    @Test
    public void testMultiplyWithPositiveInfinity() {
        double result = HappyPath.multiply(Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Double.POSITIVE_INFINITY, result, 0);
    }

    /**
     * Tests the multiply method with positive numbers.
     * Verifies that the method correctly multiplies two positive doubles.
     */
    @Test
    public void test_multiply_1_2() {
        double result = HappyPath.multiply(2.5, 3.0);
        assertEquals(7.5, result, 0.0001);
    }
}
