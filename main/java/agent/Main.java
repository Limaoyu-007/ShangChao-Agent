package agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.BailianModelClient;
import model.ModelClient;
import store.FileSessionStore;
import store.SessionStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception{

        ModelClient modelClient = new BailianModelClient();

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
                System.out.println("请求工具：" + toolCall.function().name());

                String toolResult = null;
                if (toolCall.function().name().equals("get_current_time")) {
                    toolResult = "当前系统时间是：" + LocalDateTime.now();
                }
                System.out.println(toolResult);

                Message toolMessage = new Message("tool", toolResult, null, toolCall.id());

                messages.add(toolMessage);
            }

        }

        store.save(messages);
    }




}