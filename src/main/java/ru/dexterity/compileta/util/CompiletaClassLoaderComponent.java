package ru.dexterity.compileta.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import ru.dexterity.compileta.api.CompileComponent;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class CompiletaClassLoaderComponent extends ClassLoader {

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

    private byte[] classToByteArray(String className, String directoryName) throws IOException {
        File compiledClass = new File(
            CompileComponent.CLASSES_DIRECTORY + directoryName + className + ".class"
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
        if (Files.notExists(Paths.get(CompileComponent.CLASSES_DIRECTORY + directoryName))) {
            Files.createDirectory(Paths.get(CompileComponent.CLASSES_DIRECTORY + directoryName));
        }

        Files.writeString(Paths.get(CompileComponent.CLASSES_DIRECTORY + directoryName + className + ".java"), code);
    }

    private void compileClasses(String className, String testClassName, String directoryName) throws IOException {
        className       = CompileComponent.CLASSES_DIRECTORY + directoryName + className + ".java";
        testClassName   = CompileComponent.CLASSES_DIRECTORY + directoryName + testClassName + ".java";

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
            + CompileComponent.CLASSES_DIRECTORY + directoryName + className + ".java"
        );

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            log.info(e.toString());
        }

        process.destroy();
    }

}
