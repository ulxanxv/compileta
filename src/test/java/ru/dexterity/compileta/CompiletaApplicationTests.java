package ru.dexterity.compileta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.dexterity.compileta.api.CompileComponent;
import ru.dexterity.compileta.api.domain.CompilationInfo;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

@SpringBootTest
class CompiletaApplicationTests {

	@Autowired
	private CompileComponent compileComponent;

	@Test
	void contextLoads() throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		CompilationInfo compilationInfo = new CompilationInfo();
		compilationInfo.setClassName("Kata");
		compilationInfo.setTestClassName("KataTest");
		compilationInfo.setCode(dClass);
		compilationInfo.setTestCode(tClass);

		compileComponent.compileAndTest(compilationInfo);
	}

	String dClass = "import java.util.Arrays;\n" +
		"\n" +
		"public class Kata {\n" +
		"\n" +
		"    public static int[] invert(int[] array) {\n" +
		"        return Arrays.stream(array).map(operand -> operand * -1).toArray();\n" +
		"    }\n" +
		"\n" +
		"}\n";

	String tClass = "import org.junit.Assert;\n" +
		"import org.junit.Test;\n" +
		"\n" +
		"import java.util.Arrays;\n" +
		"\n" +
		"public class KataTest {\n" +
		"\n" +
		"    @Test\n" +
		"    public void test() {\n" +
		"        Assert.assertEquals(\n" +
		"            Arrays.toString(Kata.invert(new int[]{1, 3, -2, 1, 2})),\n" +
		"            Arrays.toString(new int[] {-1, -3, 2, -1})\n" +
		"        );\n" +
		"    }\n" +
		"\n" +
		"}\n";

}
