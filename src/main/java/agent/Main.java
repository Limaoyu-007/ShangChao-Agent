package agent;

import model.BailianModelClient;
import model.ModelClient;
import store.FileSessionStore;
import store.SessionStore;
import tool.CurrentTimeTool;
import tool.ToolRegistry;
import tool.github.GithubClient;
import tool.github.GithubTool;
import tool.chaoxing.ChaoxingClient;
import tool.chaoxing.HomeworkTool;

import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception{


        ToolRegistry toolRegistry = new ToolRegistry();

        toolRegistry.register(
                new CurrentTimeTool()
        );
        toolRegistry.register(
                new GithubTool(new GithubClient())
        );
        toolRegistry.register(new HomeworkTool(new ChaoxingClient()));

        ModelClient modelClient = new BailianModelClient();

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
            messages.add(Message.user(user_input));

            String reply = agent.run(messages);

            System.out.println("Assistant: " + reply);

        }

        store.save(messages);
    }




}
