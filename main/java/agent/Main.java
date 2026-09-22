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


        Agent agent = new Agent(
                modelClient,
                toolRegistry
        );


        while (true) {
            System.out.print("You: ");
            String user_input = scanner.nextLine();
            if(user_input.equals("exit"))
                break;
            messages.add(new Message("user", user_input));

            String reply = agent.run(messages);

            System.out.println("Assistant: " + reply);

        }

        store.save(messages);
    }




}