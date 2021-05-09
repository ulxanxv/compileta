package ru.dexterity.compileta.api;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.api.domain.CompileResponse;
import ru.dexterity.compileta.api.domain.TaskOwner;
import ru.dexterity.compileta.api.domain.UpdateTableResponse;
import ru.dexterity.compileta.exceptions.CompilationErrorException;
import ru.dexterity.compileta.util.CompiletaClassLoaderComponent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


    public UpdateTableResponse runAll(Map<TaskOwner, CompilationInfo> compilationList) {
        Map<TaskOwner, CompileResponse> responseMap = new HashMap<>();

        compilationList.forEach((key, value) -> {
            TaskOwner taskOwner = new TaskOwner();
            taskOwner.setCredentialId(key.getCredentialId());
            taskOwner.setTaskId(key.getTaskId());

            responseMap.put(taskOwner, this.run(value));
        });

        return new UpdateTableResponse(responseMap);
    }

    public CompileResponse run(CompilationInfo compilationInfo) {
        final String directoryName =
            UUID.randomUUID().toString().concat("/");

        // Компиляция основного класса и тестового
        this.compileClasses(compilationInfo, directoryName);

        // Загрузка скомпилированных классов
        Class<?> mainClass  = this.findClass(compilationInfo.getClassName(), directoryName);
        Class<?> testClass  = this.findClass(compilationInfo.getTestClassName(), directoryName);

        int userBrevity         = this.countBrevity(compilationInfo.getClassName(), directoryName);
        double userAverageSpeed = this.testSolution(testClass, directoryName);

        loaderComponent.deleteFiles(
            new File(classesDirectory + directoryName)
        );

        return CompileResponse.builder()
            .status("ok")
            .message("Все тесты успешно пройдены")
            .rapidity(userAverageSpeed)
            .brevity(userBrevity)
            .totalScore(
                100 / (userAverageSpeed / compilationInfo.getAverageSpeed() + ((double) userBrevity / compilationInfo.getAverageBrevity()))
            ).build();
    }

    private int countBrevity(String className, String directoryName) {
        ClassReader classReader;

        try {
            classReader = new ClassReader(
                new FileInputStream(classesDirectory + directoryName + className + ".class")
            );
        } catch (IOException ignored) {
            return 0;
        }

        StringWriter classContent       = new StringWriter();
        TraceClassVisitor classVisitor  = new TraceClassVisitor(new PrintWriter(classContent));

        classReader.accept(classVisitor, 0);

        return classContent.toString()
            .replaceAll("//.*|(\\\"(?:\\\\\\\\[^\\\"]|\\\\\\\\\\\"|.)*?\\\")|(?s)/\\\\*.*?\\\\*/", "")
            .replaceAll("\\n", "")
            .replaceAll(" ", "")
            .length();
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
        } catch (Exception e) {
            if (e instanceof TimeoutException)
                throw new CompilationErrorException("Защита от долгого выполнения (более 10 секунд)");

            loaderComponent.deleteFiles(new File(classesDirectory + directoryName));
            throw
                new CompilationErrorException(e.getCause().getMessage());
        }

        return executionSpeedEachMethod.stream()
            .mapToDouble(Double::valueOf)
            .average()
            .orElse(Double.NaN);
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
