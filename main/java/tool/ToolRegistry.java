package tool;

import java.util.HashMap;
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


}
