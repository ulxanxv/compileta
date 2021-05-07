package ru.dexterity.compileta.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.dexterity.compileta.api.CompileController.CompileResponse;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.exceptions.CompilationErrorException;
import ru.dexterity.compileta.util.CompiletaClassLoaderComponent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class CompileComponent {

    private final CompiletaClassLoaderComponent loaderComponent;

    @Autowired
    public CompileComponent(CompiletaClassLoaderComponent loaderComponent) {
        this.loaderComponent = loaderComponent;
    }

    @Value("${compile.storage-directory}")
    private String classesDirectory;


    public CompileResponse run(CompilationInfo compilationInfo) {
        final String directoryName =
            UUID.randomUUID().toString().concat("/");

        // Компиляция основного класса и тестового
        this.compileClasses(compilationInfo, directoryName);

        // Поиск скомпилированного класса и тестирование
        Class<?> mainClass  = this.findClass(compilationInfo.getClassName(), directoryName); // TODO need to count brevity and resourceConsumption
        Class<?> testClass  = this.findClass(compilationInfo.getTestClassName(), directoryName);
        double averageSpeed = this.testSolution(testClass, directoryName);

        loaderComponent.deleteFiles(
            new File(classesDirectory + directoryName)
        );

        return CompileResponse.builder()
            .status("ok")
            .message("tests passed")
            .rapidity(averageSpeed)
            // .brevity()                   // TODO need filling
            //. resourceConsumption()       // TODO need filling
            // .totalScore()                // TODO need filling after count all metrics
            .build();
    }

    /**
     * @return the average execution speed
     */
    private double testSolution(Class<?> testClass, String directoryName) {
        Method[] declaredMethods            = testClass.getDeclaredMethods();
        List<Long> executionSpeedEachMethod = new ArrayList<>();

        try {
            Object testInstance = testClass.getConstructor().newInstance();
            for (Method method : declaredMethods) {
                CompletableFuture.runAsync(() -> {
                    try {
                        long executionSpeed = System.nanoTime();
                        method.invoke(testInstance);
                        executionSpeedEachMethod.add(System.nanoTime() - executionSpeed);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new CompilationErrorException(e.getCause().getMessage());
                    }
                }).get(10, TimeUnit.SECONDS);
            }

            return executionSpeedEachMethod.stream()
                .mapToDouble(Double::valueOf)
                .average()
                .orElse(Double.NaN);

        } catch (Exception e) {
            if (e instanceof TimeoutException)
                throw new CompilationErrorException("защита от долгого выполнения (более 10 секунд)");

            loaderComponent.deleteFiles(new File(classesDirectory + directoryName));
            throw
                new CompilationErrorException(e.getCause().getMessage());
        }
    }

    private void compileClasses(CompilationInfo compilationInfo, String directoryName) {
        try {
            loaderComponent.compileClasses(compilationInfo, directoryName);
        } catch (IOException | CompilationErrorException e) {
            loaderComponent.deleteFiles(new File(classesDirectory + directoryName));
            throw new CompilationErrorException(e.getMessage());
        }
    }

    private Class<?> findClass(String className, String directoryName) {
        return loaderComponent.findClass(className, directoryName);
    }

}
