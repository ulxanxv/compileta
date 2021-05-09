package ru.dexterity.compileta.api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class UpdateTableResponse {

    private Map<TaskOwner, CompileResponse> updatableList;

}
