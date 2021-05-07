package ru.dexterity.compileta.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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
            CompileResponse run = compileComponent.run(compilationInfo);
            return ResponseEntity.ok(run);
        } catch (CompilationErrorException e) {
            return ResponseEntity.ok(
                CompileResponse.builder()
                    .status("error")
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    @Data
    @Builder
    public static class CompileResponse {

        private String status;
        private String message;

        @JsonInclude(Include.NON_NULL)
        private Double brevity;

        @JsonInclude(Include.NON_NULL)
        private Double rapidity;

        @JsonInclude(Include.NON_NULL)
        private Double resourceConsumption;

        @JsonInclude(Include.NON_NULL)
        private Double totalScore;

    }

}
