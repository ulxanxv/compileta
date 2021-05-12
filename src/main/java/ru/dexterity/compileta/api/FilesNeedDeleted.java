package ru.dexterity.compileta.api;

import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
@Component
@Scope("singleton")
public class FilesNeedDeleted {

    private List<Path> needDeleted = new ArrayList<>();

    public synchronized void addFile(Path path) {
        needDeleted.add(path);
    }

    public synchronized void removeFile(Path path) {
        needDeleted.remove(path);
    }

}
