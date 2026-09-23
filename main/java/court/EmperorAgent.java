
package court;

import agent.Agent;
import agent.Message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EmperorAgent {

    private final Agent agent;
    private final CourtStore store;

    private final ObjectMapper mapper = new ObjectMapper();

    public EmperorAgent(Agent agent, CourtStore store) {
        this.agent = agent;
        this.store = store;
    }

    // 皇帝被唤醒后执行一次自主决策
    public Decision wakeUp() throws Exception {

        // 1. 读取皇帝的治理原则
        String principles = Files.readString(
                Path.of("court-principles.txt")
        );

        // 2. 读取当前政务
        String affairs = Files.readString(
                Path.of("court-affairs.txt")
        );

        // 3. 读取历史决策
        List<Decision> history = store.load();

        // 4. 组装本次运行的上下文
        String context = buildContext(affairs, history);

        List<Message> messages = new ArrayList<>();

        messages.add(
                new Message("system", principles)
        );

        messages.add(
                new Message("user", context)
        );

        // 5. 调用现有 Agent Runtime
        String reply = agent.run(messages);

        // 6. 解析皇帝作出的决定
        Decision decision = parseDecision(reply);

        // 7. 保存决策
        store.add(decision);

        return decision;
    }

    // 组装当前政务和历史决策
    private String buildContext(
            String affairs,
            List<Decision> history
    ) throws Exception {

        StringBuilder context = new StringBuilder();

        context.append("【当前时间】\n");

        context.append(
                ZonedDateTime.now(
                        ZoneId.of("Asia/Shanghai")
                )
        );

        context.append("\n\n【当前政务】\n");
        context.append(affairs);

        context.append("\n\n【最近的决策记录】\n");

        int start = Math.max(0, history.size() - 10);

        List<Decision> recent = history.subList(
                start,
                history.size()
        );

        context.append(
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(recent)
        );

        context.append("""

                【本次运行事件】
                朝廷运行系统已经主动唤醒你。

                请根据当前政务、治理原则和历史决策，
                自主决定现在应当采取什么行动。

                你不需要等待用户提出要求。

                如果已经存在相同的有效决定，
                不要重复颁布。

                请只返回一个 JSON 对象，不要添加解释、
                Markdown 代码块或其他文字。

                返回格式：

                {
                  "type": "DECIDE",
                  "content": "具体决定",
                  "reason": "决策依据"
                }

                type 只能是以下三种：

                DECIDE：正式作出决定。
                INVESTIGATE：决定进一步调查。
                WAIT：当前没有必要产生新行动。

                三种类型都必须提供 content 和 reason。

                重要：本阶段 INVESTIGATE 只记录调查决定，
                尚不具备实际召集其他 Agent 的能力。
                """);

        return context.toString();
    }

    // 将模型回复转化为正式决策
    private Decision parseDecision(String reply)
            throws Exception {

        if (reply == null || reply.isBlank()) {
            throw new IllegalStateException(
                    "皇帝没有返回决策内容"
            );
        }

        // 兼容模型偶尔返回 Markdown 代码块
        String json = reply.trim();

        if (json.startsWith("```")) {
            json = json.replaceFirst(
                    "^```(?:json)?\\s*", ""
            );

            json = json.replaceFirst(
                    "\\s*```$", ""
            );
        }

        JsonNode root = mapper.readTree(json);

        if (root == null || !root.isObject()) {
            throw new IllegalStateException(
                    "皇帝返回的不是 JSON 对象"
            );
        }

        String type = root.path("type").asText("");
        String content = root.path("content").asText("");
        String reason = root.path("reason").asText("");

        // 校验决策类型
        if (!List.of(
                "DECIDE",
                "INVESTIGATE",
                "WAIT"
        ).contains(type)) {

            throw new IllegalStateException(
                    "无效的决策类型: " + type
            );
        }

        if (content.isBlank() || reason.isBlank()) {
            throw new IllegalStateException(
                    "决策内容或理由不能为空"
            );
        }

        return new Decision(
                UUID.randomUUID().toString(),
                type,
                content,
                reason,
                ZonedDateTime.now(
                        ZoneId.of("Asia/Shanghai")
                ).toString()
        );
    }
}
