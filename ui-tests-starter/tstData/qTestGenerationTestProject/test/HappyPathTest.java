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
}
