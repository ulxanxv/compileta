package ru.dexterity.compileta.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.exceptions.CompilationErrorException;
import ru.dexterity.compileta.util.CompiletaClassLoaderComponent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

@Slf4j
@Component
public class CompileComponent {

    @Autowired
    private CompiletaClassLoaderComponent loaderComponent;

    @Value("${compile.storage-directory}")
    private String classesDirectory;

    public void compileAndTest(CompilationInfo compilationInfo) {
        String directoryName = UUID.randomUUID().toString().concat("/");

        try {
            loaderComponent.compileClasses(compilationInfo, directoryName);
        } catch (IOException e) {
            loaderComponent.deleteFiles(new File(classesDirectory + directoryName));
            throw new CompilationErrorException("compilation error");
        }

        // Вызов всех методов тест класса
        Class<?> mainClass = loaderComponent.findClass(compilationInfo.getClassName(), directoryName);
        Class<?> testClass = loaderComponent.findClass(compilationInfo.getTestClassName(), directoryName);

        Method[] declaredMethods = testClass.getDeclaredMethods();

        try {
            Object testInstance = testClass.getConstructor().newInstance();
            for (Method method : declaredMethods) {
                method.invoke(testInstance);
            }
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            loaderComponent.deleteFiles(new File(classesDirectory + directoryName));
            throw new CompilationErrorException(e.getCause().getMessage());
        }

        // Удаление файлов после компиляции и тестирования
        loaderComponent.deleteFiles(new File(classesDirectory + directoryName));
    }

    public Class<?> compile(String className, String code, String directoryName) {
        try {
            return loaderComponent.compileClass(className, directoryName, code);
        } catch (IOException e) {
            throw new CompilationErrorException(e.getMessage());
        }
    }

}
