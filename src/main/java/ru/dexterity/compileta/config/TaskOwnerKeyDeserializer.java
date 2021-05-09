package ru.dexterity.compileta.config;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import lombok.extern.slf4j.Slf4j;
import ru.dexterity.compileta.api.domain.TaskOwner;

import java.io.IOException;

@Slf4j
public class TaskOwnerKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String s, DeserializationContext deserializationContext) throws IOException {
        return new TaskOwner(s);
    }
}