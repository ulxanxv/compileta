package ru.dexterity.compileta.api.domain;

import lombok.Data;

@Data
public class CompileRequest {

    private String code;
    private String className;
    private String testCode;
    private String testClassName;

    private Double averageSpeed;
    private Double averageBrevity;

}