package ru.dexterity.compileta.api.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.dexterity.compileta.config.TaskOwnerKeyDeserializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@EqualsAndHashCode
@NoArgsConstructor
@JsonDeserialize(keyUsing = TaskOwnerKeyDeserializer.class)
public class TaskOwner {

    private Long credentialId;
    private Long taskId;

    public TaskOwner(String both) {
        Matcher matcher = Pattern.compile("=+(.*?),+|=+(.*?)\\)+").matcher(both);

        if (matcher.find())
            this.credentialId = Long.valueOf(matcher.group(1));

        if (matcher.find())
            this.taskId = Long.valueOf(matcher.group(2));
    }

    public TaskOwner(Long credentialId, Long taskId) {
        this.credentialId = credentialId;
        this.taskId = taskId;
    }

}
