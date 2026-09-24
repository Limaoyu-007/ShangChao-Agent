package model;

import agent.FunctionCall;
import agent.Message;
import agent.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 百炼协议适配：发送消息和工具定义，解析回复，不持有可执行的工具注册器。 */
public class BailianModelClient implements ModelClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Message chat(List<Message> messages, List<Map<String, Object>> tools) throws Exception {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("请在当前运行配置中设置 DASHSCOPE_API_KEY");
        }
        // 沿用原配置；可通过环境变量切换服务，不在源码中保存凭据。
        String url = setting("DASHSCOPE_CHAT_URL",
                "https://llm-qhj2oroek3k6dtet.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions");
        String model = setting("DASHSCOPE_MODEL", "qwen3.8-27b");

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        // 皇帝请求不发送 tools 字段，能力边界不依赖提示词。
        if (!tools.isEmpty()) body.put("tools", tools);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        if (response.statusCode() != 200) {
            // 不输出请求、鉴权头或整段业务响应，只摘取服务端错误字段。
            throw new IllegalStateException("模型请求失败，HTTP " + response.statusCode()
                    + errorSummary(response.body(), key));
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode message = root == null ? null : root.path("choices").path(0).path("message");
        if (message == null || !message.isObject()) {
            throw new IllegalStateException("模型响应缺少 choices[0].message");
        }
        JsonNode contentNode = message.get("content");
        if (contentNode != null && !contentNode.isNull() && !contentNode.isTextual()) {
            throw new IllegalStateException("当前客户端仅支持文本 content");
        }
        String content = contentNode == null || contentNode.isNull() ? null : contentNode.asText();

        List<ToolCall> calls = new ArrayList<>();
        JsonNode callNodes = message.get("tool_calls");
        if (callNodes != null && !callNodes.isNull()) {
            if (!callNodes.isArray()) throw new IllegalStateException("tool_calls 必须是数组");
            // 完整保留一轮中的所有工具调用，由 Agent 逐个执行。
            for (JsonNode call : callNodes) {
                String type = requiredText(call, "type");
                if (!"function".equals(type)) {
                    throw new IllegalStateException("不支持的工具调用类型：" + type);
                }
                JsonNode function = call.path("function");
                calls.add(new ToolCall(requiredText(call, "id"), type,
                        new FunctionCall(requiredText(function, "name"),
                                requiredText(function, "arguments"))));
            }
        }
        if (calls.isEmpty() && (content == null || content.isBlank())) {
            throw new IllegalStateException("模型没有返回文本或工具调用");
        }
        return new Message("assistant", content, calls, null);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("模型响应缺少有效字段：" + field);
        }
        return value.asText();
    }

    private String errorSummary(String body, String key) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null) return "（空响应）";
            JsonNode error = root.path("error");
            String detail = error.path("code").asText("") + " " + error.path("message").asText("");
            detail = detail.replace(key, "[REDACTED]").replaceAll("[\\r\\n]+", " ").trim();
            return detail.isEmpty() ? "" : "：" + detail.substring(0, Math.min(detail.length(), 500));
        } catch (Exception e) {
            return "（错误响应不是有效 JSON）";
        }
    }

    private String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
