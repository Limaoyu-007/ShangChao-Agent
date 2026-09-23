
package court;

import agent.Agent;

import model.BailianModelClient;
import model.ModelClient;

import tool.CurrentTimeTool;
import tool.ToolRegistry;

public class CourtRuntime {

    private final EmperorAgent emperor;

    public CourtRuntime(EmperorAgent emperor) {
        this.emperor = emperor;
    }

    // 主动触发一次皇帝决策
    public void runOnce() throws Exception {

        System.out.println("朝廷运行系统启动。");

        System.out.println("正在唤醒皇帝...");

        Decision decision = emperor.wakeUp();

        System.out.println("\n========== 皇帝决策 ==========");

        System.out.println(
                "决策类型：" + decision.type()
        );

        System.out.println(
                "决策内容：" + decision.content()
        );

        System.out.println(
                "决策依据：" + decision.reason()
        );

        System.out.println(
                "决策编号：" + decision.id()
        );

        System.out.println("============================");

        System.out.println("决策已保存。");
    }

    public static void main(String[] args)
            throws Exception {

        // 1. 为皇帝创建独立的工具注册器
        ToolRegistry emperorTools = new ToolRegistry();

        emperorTools.register(
                new CurrentTimeTool()
        );

        // 2. 创建模型客户端
        ModelClient modelClient =
                new BailianModelClient(emperorTools);

        // 3. 复用现有 Agent
        Agent agent = new Agent(
                modelClient,
                emperorTools
        );

        // 4. 创建政务存储
        CourtStore store = new CourtStore();

        // 5. 创建皇帝 Agent
        EmperorAgent emperor = new EmperorAgent(
                agent,
                store
        );

        // 6. 创建朝廷运行系统
        CourtRuntime runtime =
                new CourtRuntime(emperor);

        // 7. 主动唤醒皇帝
        runtime.runOnce();
    }
}
