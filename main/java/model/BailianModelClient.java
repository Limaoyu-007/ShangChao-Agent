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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BailianModelClient implements ModelClient {
    @Override
    public Message chat(List<Message> messages) throws Exception {

        String key = "sk-8b292221d3be4b9683926e4f459e332f";
        String url = "https://llm-qhj2oroek3k6dtet.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "qwen3.8-27b");
        requestBody.put("messages", messages);

        //
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new HashMap<>());
        parameters.put("required", List.of());

        Map<String, Object> function = new HashMap<>();
        function.put("name", "get_current_time");
        function.put("description", "获取当前系统时间");
        function.put("parameters", parameters);

        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");
        tool.put("function", function);

        requestBody.put("tools", List.of(tool));
        //



        ObjectMapper objectMapper = new ObjectMapper();
        String body = objectMapper.writeValueAsString(requestBody);


        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();



        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(response.body());

        //
        JsonNode messageNode = jsonNode.get("choices").get(0).get("message");


        if(messageNode.has("tool_calls")){
            JsonNode toolNode = messageNode.get("tool_calls").get(0);
            String toolCallId = toolNode.get("id").asText();
            String toolName = toolNode.get("function").get("name").asText();
            String arguments = toolNode.get("function").get("arguments").asText();
            String toolType = toolNode.get("type").asText();
//
//            System.out.println("工具 ID：" + toolCallId);
//            System.out.println("工具名称：" + toolName);
//            System.out.println("工具参数：" + arguments);
//

            FunctionCall functionCall = new FunctionCall(toolName, arguments);
            ToolCall toolCall = new ToolCall(toolCallId,toolType,functionCall);




            String content = jsonNode.get("choices").get(0).get("message").get("content").asText();

            return new Message("assistant", content, List.of(toolCall), null);

        }else{


            String content = jsonNode.get("choices").get(0).get("message").get("content").asText();

            return new Message("assistant", content);

        }



    }
}
