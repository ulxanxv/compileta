package ru.dexterity.compileta.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import ru.dexterity.compileta.api.domain.CompileRequest;
import ru.dexterity.compileta.api.domain.CompileResponse;
import ru.dexterity.compileta.api.domain.TaskOwner;
import ru.dexterity.compileta.api.domain.UpdateTableResponse;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    private final DeleteQueue deleteQueue;

    public UpdateTableResponse runAll(Map<TaskOwner, CompileRequest> compileMap) {
        Map<TaskOwner, CompileResponse> responseMap = new HashMap<>();

        compileMap.forEach((key, value) -> {
            TaskOwner taskOwner = new TaskOwner();
            taskOwner.setCredentialId(key.getCredentialId());
            taskOwner.setTaskId(key.getTaskId());

            responseMap.put(taskOwner, this.run(value));
        });

        return new UpdateTableResponse(responseMap);
    }

    public CompileResponse run(CompileRequest compileRequest) {
        final String directoryName =
            UUID.randomUUID().toString().concat("/");

        // Компиляция основного класса и тестового
        this.compileClasses(compileRequest, directoryName);

        // Загрузка скомпилированных классов
        Class<?> testClass = null;

        try {

            URL[] urls = new URL[] {
                new File(modulesDirectory + "junit.jar").toURI().toURL(),
                new File(classesDirectory + directoryName).toURI().toURL()
            };

            URLClassLoader urlClassLoader = URLClassLoader.newInstance(urls);

            try {
                testClass  = urlClassLoader.loadClass(compileRequest.getTestClassName());
            } catch (ClassNotFoundException e) {
                deleteQueue.add(Paths.get(classesDirectory + directoryName));
                log.error(e.getMessage());
            }
        } catch (MalformedURLException e) {
            deleteQueue.add(Paths.get(classesDirectory + directoryName));
            log.error(e.getMessage());
        }

        int userBrevity         = this.countBrevity(compileRequest.getClassName(), directoryName);
        double userAverageSpeed = this.testSolution(testClass, directoryName);

        return CompileResponse.builder()
            .status("ok")
            .message("Все тесты успешно пройдены")
            .rapidity(userAverageSpeed)
            .brevity(userBrevity)
            .totalScore(
                25 / (userAverageSpeed / compileRequest.getAverageSpeed()) + ((double) (25 / (userBrevity / compileRequest.getAverageBrevity())))
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
                if (Modifier.isPrivate(method.getModifiers())) { continue; }
                CompletableFuture.runAsync(() -> {
                    try {
                        long executionSpeed = System.nanoTime();
                        method.invoke(testInstance);
                        executionSpeedEachMethod.add(System.nanoTime() - executionSpeed);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        deleteQueue.add(Paths.get(classesDirectory + directoryName));
                        throw new CompilationErrorException(e.getCause().getMessage());
                    }
                }).get(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                throw new CompilationErrorException("Защита от долгого выполнения (более 10 секунд)");
            }

            throw new CompilationErrorException(e.getCause().getMessage());
        } finally {
            deleteQueue.add(Paths.get(classesDirectory + directoryName));
        }

        return executionSpeedEachMethod.stream()
            .mapToDouble(Double::valueOf)
            .average()
            .orElse(Double.NaN);
    }

    private void compileClasses(CompileRequest compileRequest, String directoryName) {
        try {
            this.createFile(compileRequest.getClassName(), directoryName, compileRequest.getCode());
            this.createFile(compileRequest.getTestClassName(), directoryName, compileRequest.getTestCode());
        } catch (IOException ioException) {
            deleteQueue.add(Paths.get(classesDirectory + directoryName));
            throw new CompilationErrorException(ioException.getMessage());
        }

        this.compileClasses(compileRequest.getClassName(), compileRequest.getTestClassName(), directoryName);
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

                deleteQueue.add(Paths.get(classesDirectory + directoryName));

                throw new CompilationErrorException(errorText);
            }

        } catch (InterruptedException | IOException ignored) {
            deleteQueue.add(Paths.get(classesDirectory + directoryName));
        }
    }

    private void createFile(String className, String directoryName, String code) throws IOException {
        if (Files.notExists(Paths.get(classesDirectory + directoryName))) {
            Files.createDirectory(Paths.get(classesDirectory + directoryName));
        }

        Files.write(Paths.get(classesDirectory + directoryName + className + ".java"), code.getBytes(StandardCharsets.UTF_8));
    }

    @Scheduled(cron = "0/60 * * * * *")
    public void deleteFiles() {
        if (!Files.exists(Paths.get(classesDirectory))) {
            return;
        }

        synchronized (this) {
            deleteQueue.getNeedDeleted().removeIf(each -> {
                try {
                    return FileSystemUtils.deleteRecursively(each);
                } catch (IOException ignored) {
                    return false;
                }
            });
        }
    }

    @PostConstruct
    public void clearClasses() {
        if (!Files.exists(Paths.get(classesDirectory))) {
            return;
        }

        File[] listFiles = new File(classesDirectory).listFiles();
        for (File file : listFiles) {
            deleteQueue.add(file.toPath());
        }

    }

}
