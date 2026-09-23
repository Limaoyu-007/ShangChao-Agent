package court;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CourtStore {

    private final Path filePath =
            Path.of("court-state.json");

    private final ObjectMapper mapper =
            new ObjectMapper();

    // 读取历史决策
    public List<Decision> load() throws Exception {

        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }

        String json = Files.readString(filePath);

        return mapper.readValue(
               json,
                new TypeReference<List<Decision>>() {}
        );
    }

    // 保存全部决策
    public void save(List<Decision> decisions)
            throws Exception {

        String json = mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(decisions);

        Files.writeString(filePath, json);
    }

    // 增加一条新决策
    public void add(Decision decision)
            throws Exception {

        List<Decision> decisions = load();

        decisions.add(decision);

        save(decisions);
    }
}