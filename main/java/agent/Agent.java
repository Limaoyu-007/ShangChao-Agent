package agent;

import model.ModelClient;
import tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

public class Agent {

    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;

    public Agent(ModelClient modelClient, ToolRegistry toolRegistry) {
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
    }

    public String run(List<Message> messages) throws Exception {

        while (true) {
            //返回后的单个消息
            Message assistantMessage = modelClient.chat(messages);
            messages.add(assistantMessage);

            if (assistantMessage.tool_calls() == null) {

                return assistantMessage.content();
            }


            ToolCall toolCall = assistantMessage.tool_calls().get(0);
            String toolName = toolCall.function().name();
            String arguments = toolCall.function().arguments();


            String toolResult;
            try {

                if (!toolRegistry.contains(toolName)) {
                    throw new IllegalArgumentException("工具未注册: " + toolName);
                }

                toolResult = toolRegistry.execute(toolName, arguments);
            } catch (Exception e) {
                toolResult = "工具调用失败: " + e.getMessage();
            }

            Message toolMessage = new Message("tool", toolResult, null, toolCall.id());

            messages.add(toolMessage);
        }

    }
}
