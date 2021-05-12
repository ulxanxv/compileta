package ru.dexterity.compileta.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.api.domain.CompileResponse;
import ru.dexterity.compileta.api.domain.TaskOwner;
import ru.dexterity.compileta.api.domain.UpdateTableResponse;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompileComponent {

    @Value("${compile.classesDirectory}")
    public String classesDirectory;

    @Value("${compile.modulesDirectory}")
    public String modulesDirectory;

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
        Class<?> testClass = null;

        try {

            URL[] urls = new URL[] {
                new File(modulesDirectory + "junit.jar").toURI().toURL(),
                new File(classesDirectory + directoryName).toURI().toURL()
            };

            URLClassLoader urlClassLoader = URLClassLoader.newInstance(urls);

            try {
                testClass  = urlClassLoader.loadClass(compilationInfo.getTestClassName());
            } catch (ClassNotFoundException e) {
                log.error(e.getMessage());
            }
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
        }

        int userBrevity         = this.countBrevity(compilationInfo.getClassName(), directoryName);
        double userAverageSpeed = this.testSolution(testClass, directoryName);

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
            if (e instanceof TimeoutException) {
                throw new CompilationErrorException("Защита от долгого выполнения (более 10 секунд)");
            }

            throw new CompilationErrorException(e.getCause().getMessage());
        }

        return executionSpeedEachMethod.stream()
            .mapToDouble(Double::valueOf)
            .average()
            .orElse(Double.NaN);
    }

    private void compileClasses(CompilationInfo compilationInfo, String directoryName) {
        try {
            this.createFile(compilationInfo.getClassName(), directoryName, compilationInfo.getCode());
            this.createFile(compilationInfo.getTestClassName(), directoryName, compilationInfo.getTestCode());
        } catch (IOException ioException) {
            throw new CompilationErrorException(ioException.getMessage());
        }

        this.compileClasses(compilationInfo.getClassName(), compilationInfo.getTestClassName(), directoryName);
    }

    private void compileClasses(String className, String testClassName, String directoryName) {
        className       = classesDirectory + directoryName + className + ".java";
        testClassName   = classesDirectory + directoryName + testClassName + ".java";

        try {
            Process process = Runtime.getRuntime().exec(
                "javac -Xmaxerrs 1 -cp " + modulesDirectory + "junit.jar" + File.pathSeparatorChar + modulesDirectory + "hamcrest.jar "
                    + className + " " + testClassName
            );
            process.waitFor();

            if (process.exitValue() == 1) {
                String errorText = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

                throw new CompilationErrorException(errorText);
            }

        } catch (InterruptedException | IOException e) {
            log.error(e.getMessage());
        }
    }

    private void createFile(String className, String directoryName, String code) throws IOException {
        if (Files.notExists(Paths.get(classesDirectory + directoryName))) {
            Files.createDirectory(Paths.get(classesDirectory + directoryName));
        }

        Files.write(Paths.get(classesDirectory + directoryName + className + ".java"), code.getBytes(StandardCharsets.UTF_8));
    }

    @Scheduled(cron = "0/10 * * * * *")
    public void deleteFiles() {
        if (!Files.exists(Paths.get(classesDirectory))) {
            return;
        }

        File[] files = new File(classesDirectory).listFiles();
        for (File file : files) {
            try {
                FileSystemUtils.deleteRecursively(file.toPath());
            } catch (IOException ignored) {}
        }
    }

}
