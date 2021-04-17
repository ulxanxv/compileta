package ru.dexterity.compileta.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dexterity.compileta.api.domain.CompilationInfo;
import ru.dexterity.compileta.exceptions.CompilationErrorException;

@RestController
@RequiredArgsConstructor
public class CompileController {

    private final CompileComponent compileComponent;

    @PostMapping("/compile")
    public ResponseEntity<CompileResponse> compile(@RequestBody CompilationInfo compilationInfo)  {
        try {
            compileComponent.compileAndTest(compilationInfo);
            return ResponseEntity.ok(new CompileResponse("ok", "tests_passed"));
        } catch (CompilationErrorException e) {
            return ResponseEntity.ok(new CompileResponse("error", e.getMessage()));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CompileResponse {

        private final String status;
        private final String message;

    }

}
