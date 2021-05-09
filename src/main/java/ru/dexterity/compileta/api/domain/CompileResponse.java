package ru.dexterity.compileta.api.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompileResponse {

    private String status;
    private String message;

    @JsonInclude(Include.NON_NULL)
    private int brevity;

    @JsonInclude(Include.NON_NULL)
    private double rapidity;

    @JsonInclude(Include.NON_NULL)
    private double totalScore;

}
