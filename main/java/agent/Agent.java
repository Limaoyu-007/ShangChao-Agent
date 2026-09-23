package agent;

import model.ModelClient;
import tool.ToolRegistry;
import java.util.List;

/** 工具型 Agent 的执行循环。皇帝的纯决策路径不经过本类。 */
public class Agent {
    private static final int MAX_MODEL_ROUNDS = 12;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;

    public Agent(ModelClient modelClient, ToolRegistry toolRegistry) {
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
    }

    /** 会向调用方的历史追加 assistant 和 tool 消息。 */
    public String run(List<Message> messages) throws Exception {
        for (int round = 0; round < MAX_MODEL_ROUNDS; round++) {
            Message reply = modelClient.chat(messages, toolRegistry.getToolDefinitions());
            if (reply == null) throw new IllegalStateException("模型返回空消息");
            messages.add(reply);
            if (reply.tool_calls() == null || reply.tool_calls().isEmpty()) {
                if (reply.content() == null || reply.content().isBlank()) {
                    throw new IllegalStateException("模型没有返回有效文本");
                }
                return reply.content();
            }
            // 每个调用都补齐对应结果，再继续请求模型。
            for (ToolCall call : reply.tool_calls()) {
                String result;
                try {
                    result = toolRegistry.execute(call.function().name(), call.function().arguments());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    result = "工具调用失败: " + e.getMessage();
                }
                messages.add(Message.toolResult(call.id(), result));
            }
        }
        // 不自动重跑：此前可能已执行有副作用的工具。
        throw new IllegalStateException("工具型 Agent 达到 " + MAX_MODEL_ROUNDS + " 轮上限，已停止继续请求");
    }
}
