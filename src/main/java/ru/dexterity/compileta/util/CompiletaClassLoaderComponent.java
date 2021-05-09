package ru.dexterity.compileta.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Component
@RequestScope
public class CompiletaClassLoaderComponent extends ClassLoader {

    @Value("${compile.storage-directory}")
    private String classesDirectory;

    // Компиляция класса вместе с тестовым
    public void compileClasses(CompilationInfo compilationInfo, String directoryName) throws IOException {
        this.createFile(compilationInfo.getClassName(), directoryName, compilationInfo.getCode());
        this.createFile(compilationInfo.getTestClassName(), directoryName, compilationInfo.getTestCode());

        this.compileClasses(compilationInfo.getClassName(), compilationInfo.getTestClassName(), directoryName);
    }

    // Компиляция одного класса
    public Class<?> compileClass(String className, String directoryName, String code) throws IOException {
        this.createFile(className, directoryName, code);
        this.compileClass(className, directoryName);
        return findClass(className, directoryName);
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

    public void deleteFiles(File file) {
        if (!file.exists()) { return; }

        if (file.isDirectory()) {
            Arrays.stream(Objects.requireNonNull(file.listFiles())).iterator().forEachRemaining(this::deleteFiles);
        }

        file.delete();
    }

    private byte[] classToByteArray(String className, String directoryName) throws IOException {
        File compiledClass = new File(
            classesDirectory + directoryName + className + ".class"
        );

        InputStream inputStream = new FileInputStream(compiledClass);

        long length = compiledClass.length();

        if (length > Integer.MAX_VALUE) {
            throw new CompilationErrorException(String.format("Класс %s очень большой", className));
        }

        byte[] bytes = new byte[(int) length];

        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length && (numRead = inputStream.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        if (offset < bytes.length) {
            throw new CompilationErrorException(String.format("В класс %s не все байты прочитаны", className));
        }

        inputStream.close();
        return bytes;
    }

    private void createFile(String className, String directoryName, String code) throws IOException {
        File directory = new File(classesDirectory + directoryName);
        directory.mkdir();
        File file = new File(classesDirectory + directoryName + className + ".java");
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(code);
        fileWriter.close();
    }

    private void compileClasses(String className, String testClassName, String directoryName) throws IOException {
        className       = classesDirectory + directoryName + className + ".java";
        testClassName   = classesDirectory + directoryName + testClassName + ".java";

        Process process = Runtime.getRuntime().exec(
                "javac -cp src/main/resources/module/junit.jar;src/main/resources/module/hamcrest.jar "
                + className + " " + testClassName
        );

        try {
            process.waitFor();

            if (process.exitValue() == 1) {
                throw new CompilationErrorException("Не удалось скомпилировать, проверьте синтаксис или название главного метода/класса");
            }
        } catch (InterruptedException e) { log.info(e.toString()); }

        process.destroy();
    }

    private void compileClass(String className, String directoryName) throws IOException {
        Process process = Runtime.getRuntime().exec(
            "javac -cp src/main/resources/module/junit.jar;src/main/resources/module/hamcrest.jar "
            + classesDirectory + directoryName + className + ".java"
        );

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            log.info(e.toString());
        }

        process.destroy();
    }

}
