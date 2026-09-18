package store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import agent.Message;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileSessionStore implements SessionStore {

    private final String filePath = "session.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void save(List<Message> messages) throws Exception {

        String json = objectMapper.writeValueAsString(messages);

        Files.writeString(new File(filePath).toPath(), json);

    }

    @Override
    public List<Message> load() throws Exception {

        if (!Files.exists(Path.of(filePath))){
            return new ArrayList<>();
        }

        String json = Files.readString(Path.of(filePath));

        return objectMapper.readValue(json, new TypeReference<List<Message>>() {}
        );

    }
}
