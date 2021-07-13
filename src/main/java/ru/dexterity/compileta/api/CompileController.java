package ru.dexterity.compileta.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dexterity.compileta.api.domain.CompileRequest;
import ru.dexterity.compileta.api.domain.CompileResponse;
import ru.dexterity.compileta.api.domain.TaskOwner;
import ru.dexterity.compileta.api.domain.UpdateTableResponse;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CompileController {

    private final CompileComponent compileComponent;

    @PostMapping("/compile")
    public ResponseEntity<CompileResponse> compile(@RequestBody CompileRequest compileRequest)  {
        try {
            return ResponseEntity.ok(compileComponent.run(compileRequest));
        } catch (CompilationErrorException e) {
            return ResponseEntity.ok(CompileResponse.builder()
                    .status("error")
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/compile_all")
    public ResponseEntity<UpdateTableResponse> compileAll(@RequestBody Map<TaskOwner, CompileRequest> compileMap) {
        return ResponseEntity.ok(compileComponent.runAll(compileMap));
    }

}
