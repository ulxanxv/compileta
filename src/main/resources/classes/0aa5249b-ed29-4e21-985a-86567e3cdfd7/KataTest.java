import org.junit.Assert; import org.junit.Test; import java.util.Arrays; public class KataTest { @Test public void test() { Assert.assertEquals(Arrays.toString(Kata.invert(new int[]{1, 3, -2, 1})), Arrays.toString(new int[] {-1, -3, 2, -1}));}}