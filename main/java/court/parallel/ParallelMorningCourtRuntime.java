
package court.parallel;

import court.EdictStore;
import model.BailianModelClient;
import model.ModelClient;

import java.util.List;
import java.util.Scanner;

/**
 * 多 Agent 并行早朝的控制台运行入口。
 *
 * 负责：
 * 1. 初始化模型客户端。
 * 2. 创建皇帝和大臣 Agent。
 * 3. 创建会议会话和编排器。
 * 4. 启动独立的控制台输入任务。
 * 5. 运行会议协调循环。
 */
public class ParallelMorningCourtRuntime {

    public static void main(String[] args)
            throws Exception {

        // 1. 创建模型客户端
        ModelClient modelClient =
                new BailianModelClient();


        // 2. 创建皇帝 Agent
        CourtParticipant emperor =
                new EmperorParticipant(modelClient);


        // 3. 创建大臣 Agent
        CourtParticipant minister =
                new MinisterParticipant(modelClient);


        // 4. 创建圣旨存储
        EdictStore store = new EdictStore();


        // 5. 创建本场朝会
        CourtSession session =
                new CourtSession(store);


        // 6. 创建多 Agent 会议编排器
        CourtOrchestrator orchestrator =
                new CourtOrchestrator(
                        session,
                        List.of(
                                emperor,
                                minister
                        )
                );


        System.out.println("""
                
                ========== 多 Agent 早朝 ==========
                
                皇帝与大臣将并行思考。
                
                你可以随时输入发言。
                输入 /exit 可以中止朝会。
                
                ================================
                """);


        // 7. 启动独立的控制台输入任务
        Thread inputThread = Thread.ofVirtual()
                .name("court-console-input")
                .start(() -> {

                    Scanner scanner =
                            new Scanner(System.in);

                    while (scanner.hasNextLine()) {

                        String input =
                                scanner.nextLine().trim();

                        // 用户主动退出
                        if ("/exit".equalsIgnoreCase(input)) {

                            orchestrator.requestStop();

                            return;
                        }

                        // 忽略空输入
                        if (input.isBlank()) {
                            continue;
                        }

                        // 将用户发言发送给会议编排器
                        orchestrator.submitUserSpeech(input);
                    }

                    // 标准输入关闭，通知编排器退出
                    orchestrator.requestStop();
                });


        // 8. 在主线程中运行会议协调循环
        try {

            orchestrator.run();

        } finally {

            // 朝会结束后，尝试停止输入任务
            inputThread.interrupt();
        }

    }

}
