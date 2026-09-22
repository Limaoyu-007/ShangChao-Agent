package tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {
    private final Map<String, Tool> tools = new HashMap<>();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public String execute(String name,String arguments) throws Exception {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未注册工具: " + name);
        }
        return tool.execute(arguments);
    }

    public List<Map<String, Object>> getToolDefinitions() {

        List<Map<String,Object>> definitions = new ArrayList<>();

        for(Tool tool : tools.values()){

            Map<String, Object> function = new HashMap<>();

            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());

            Map<String,Object> definition = new HashMap<>();
            definition.put("type", "function");
            definition.put("function", function);

            definitions.add(definition);
        }
        return definitions;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }


}
