
package court;


import model.BailianModelClient;
import model.ModelClient;


/** 后台单次触发入口，不等待用户输入；不是定时调度器。 */
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

        // 皇帝直接请求模型，不创建工具注册器或工具执行循环。
        ModelClient modelClient = new BailianModelClient();
        CourtStore store = new CourtStore();

        // 与早朝共享正式记录，但本次上下文独立。
        EmperorAgent emperor = new EmperorAgent(
                modelClient,
                store
        );

        // 组装入口并触发一次。
        CourtRuntime runtime =
                new CourtRuntime(emperor);

        runtime.runOnce();
    }
}
