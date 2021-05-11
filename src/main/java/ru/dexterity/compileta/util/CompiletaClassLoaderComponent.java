package ru.dexterity.compileta.util;

import lombok.extern.slf4j.Slf4j;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

@Slf4j
public class CompiletaClassLoaderComponent extends ClassLoader {

    private final String classesDirectory;
    private String modulesDirectory;

    public CompiletaClassLoaderComponent(String classesDirectory, String modulesDirectory) {
        this.classesDirectory = classesDirectory;
        this.modulesDirectory = modulesDirectory;
    }

    // Компиляция класса вместе с тестовым
    public void compileClasses(CompilationInfo compilationInfo, String directoryName) throws IOException {
        this.createFile(compilationInfo.getClassName(), directoryName, compilationInfo.getCode());
        this.createFile(compilationInfo.getTestClassName(), directoryName, compilationInfo.getTestCode());

        this.compileClasses(compilationInfo.getClassName(), compilationInfo.getTestClassName(), directoryName);
    }

    @Override
    // Поиск уже скомпилированного класса
    public Class<?> findClass(String className, String directoryName) {
        try {
            byte[] classByteCode = this.classToByteArray(className, directoryName);
            return this.defineClass(className, classByteCode, 0, classByteCode.length);
        } catch (IOException | ClassFormatError ignored) {
            throw new CompilationErrorException(String.format("Класс %s не найден", className));
        }
    }

    private byte[] classToByteArray(String className, String directoryName) throws IOException {
        File compiledClass = new File(
            classesDirectory + directoryName + className + ".class"
        );

        byte[] bytes;

        try (InputStream inputStream = new FileInputStream(compiledClass)) {
            long length = compiledClass.length();

            if (length > Integer.MAX_VALUE) {
                throw new CompilationErrorException(String.format("Класс %s очень большой", className));
            }

            bytes = new byte[(int) length];

            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead = inputStream.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }

            if (offset < bytes.length) {
                throw new CompilationErrorException(String.format("В класс %s не все байты прочитаны", className));
            }
        }

        return bytes;
    }

    private void createFile(String className, String directoryName, String code) throws IOException {
        if (Files.notExists(Paths.get(classesDirectory + directoryName))) {
            Files.createDirectory(Paths.get(classesDirectory + directoryName));
        }

        Files.writeString(Paths.get(classesDirectory + directoryName + className + ".java"), code);
    }

    private void compileClasses(String className, String testClassName, String directoryName) throws IOException {
        className       = classesDirectory + directoryName + className + ".java";
        testClassName   = classesDirectory + directoryName + testClassName + ".java";

        Process process = Runtime.getRuntime().exec(
                "javac -cp " + modulesDirectory + "junit.jar" + File.pathSeparatorChar + modulesDirectory + "hamcrest.jar "
                + className + " " + testClassName
        );

        try {
            process.waitFor();

            if (process.exitValue() == 1) {
                String errorText = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

                log.info("Compile error :: {}", errorText);

                throw new CompilationErrorException("Не удалось скомпилировать, проверьте синтаксис или название главного метода/класса");
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        } finally {
            process.destroy();
            process.destroyForcibly();
        }
    }

}
