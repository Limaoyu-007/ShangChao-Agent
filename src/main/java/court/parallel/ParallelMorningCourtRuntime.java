
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


        // 3. 创建礼部大臣 Agent
        CourtParticipant minister =
                new MinisterParticipant(modelClient);


        // 户部与礼部使用同一模型客户端，各自构建独立的角色请求。
        CourtParticipant finance =
                new FinanceMinisterParticipant(modelClient);

        // 工部与吏部也只参与议事，复用现有无工具调用流程。
        CourtParticipant works = new WorksMinisterParticipant(modelClient);
        CourtParticipant personnel = new PersonnelMinisterParticipant(modelClient);

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
                                minister,
                                finance,
                                works,
                                personnel
                        )
                );


        System.out.println("""
                
                ========== 上朝 · 多方议事 ==========
                皇帝、礼部、户部、工部、吏部与你共同议事。
                直接输入内容并按 Enter 上奏；输入 /exit 中止本场朝会。
                圣旨是已保存的正式决定，朝会发言会作为反馈记录。
                ====================================
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
