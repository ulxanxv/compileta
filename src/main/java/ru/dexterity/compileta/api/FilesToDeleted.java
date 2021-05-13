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
public class FilesToDeleted {

    private final List<Path> needDeleted = new ArrayList<>();

    public void add(Path path) {
        needDeleted.add(path);
    }

    public void remove(Path path) {
        needDeleted.remove(path);
    }

}
