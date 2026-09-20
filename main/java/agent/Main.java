package agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.BailianModelClient;
import model.ModelClient;
import store.FileSessionStore;
import store.SessionStore;
import tool.CurrentTimeTool;
import tool.Tool;
import tool.ToolRegistry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception{

        Tool timeTool = new CurrentTimeTool();
        ToolRegistry toolRegistry = new ToolRegistry();

        toolRegistry.register(timeTool);

        ModelClient modelClient = new BailianModelClient(toolRegistry);

        Scanner scanner = new Scanner(System.in);
        SessionStore store = new FileSessionStore();

        List<Message> messages = store.load();




        while (true) {
            System.out.print("You: ");
            String user_input = scanner.nextLine();
            if(user_input.equals("exit"))
                break;
            messages.add(new Message("user", user_input));



            while (true) {
                //返回后的单个消息
                Message assistantMessage = modelClient.chat(messages);
                messages.add(assistantMessage);

                if (assistantMessage.tool_calls() == null) {
                    System.out.println("Assistant: " + assistantMessage.content());
                    messages.add(assistantMessage);
                    break;
                }


                ToolCall toolCall = assistantMessage.tool_calls().get(0);
                String toolName = toolCall.function().name();
                String arguments = toolCall.function().arguments();

                if(!timeTool.name().equals(toolName)){
                    throw new IllegalArgumentException("不支持工具: " + toolName);
                }

                String toolResult = toolRegistry.execute(toolName, arguments);

                Message toolMessage = new Message("tool", toolResult, null, toolCall.id());
                messages.add(toolMessage);
            }

        }

        store.save(messages);
    }




}