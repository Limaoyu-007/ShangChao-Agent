package court;


import model.BailianModelClient;
import model.ModelClient;


import java.util.Scanner;

/** 早朝控制台入口，只负责交互；模型调用和本场状态由 Session 管理。 */
public class MorningCourtRuntime {

    private final MorningCourtSession session;

    public MorningCourtRuntime(MorningCourtSession session) {
        this.session = session;
    }

    public void run() throws Exception {

        System.out.println("========== 早朝 ==========");
        System.out.println("正在升朝，请稍候……");
        System.out.println("输入 /exit 可中止本场会话。");

        Scanner scanner = new Scanner(System.in);

        // 皇帝先开场，不等待用户先输入
        CourtReply reply = session.start();

        while (true) {

            showReply(reply);

            if (reply.ended()) {
                System.out.println("\n========== 退朝 ==========");
                break;
            }

            System.out.print("\n你：");

            // 输入流关闭时退出，避免继续读取
            if (!scanner.hasNextLine()) {
                System.out.println("\n输入已结束，本场会话中止。");
                break;
            }

            String speech = scanner.nextLine().trim();

            if ("/exit".equalsIgnoreCase(speech)) {
                System.out.println(
                        "本场会话已由你中止；临时议事记录不保存。"
                );
                break;
            }

            // 空输入不请求模型，也不重复显示上一轮回应
            while (speech.isBlank()) {

                System.out.print("发言不能为空，请重新输入：");

                if (!scanner.hasNextLine()) {
                    System.out.println("\n输入已结束，本场会话中止。");
                    return;
                }

                speech = scanner.nextLine().trim();

                if ("/exit".equalsIgnoreCase(speech)) {
                    System.out.println(
                            "本场会话已由你中止；临时议事记录不保存。"
                    );
                    return;
                }
            }

            reply = session.respond(speech);
        }
    }

    private void showReply(CourtReply reply) {

        System.out.println("\n皇帝：");
        System.out.println(reply.speech());


    }

    public static void main(String[] args) throws Exception {

        // 早朝中的皇帝同样没有工具能力。
        ModelClient modelClient = new BailianModelClient();

        CourtStore store = new CourtStore();

        MorningCourtSession session =
                new MorningCourtSession(
                        modelClient,
                        store
                );

        MorningCourtRuntime runtime =
                new MorningCourtRuntime(session);

        runtime.run();
    }
}
