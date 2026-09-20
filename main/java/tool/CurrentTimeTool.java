package tool;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurrentTimeTool implements Tool {


    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String execute(String argument) throws Exception {
        return "当前系统时间：" + LocalDateTime.now();
    }

    @Override
    public String description() {
        return "获取当前系统时间";
    }

    @Override
    public Map<String, Object> parameters() {

        Map<String, Object> parameters = new HashMap<>();

        parameters.put("type", "object");
        parameters.put("properties", new HashMap<>());
        parameters.put("required", List.of());

        return parameters;
    }

}
