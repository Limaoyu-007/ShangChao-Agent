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

public class MorningCourtSession {

    private final Agent agent;
    private final CourtStore store;

    private final ObjectMapper mapper = new ObjectMapper();

    // 只保存本场早朝的对话
    private final List<Message> messages = new ArrayList<>();

    private boolean started;
    private boolean ended;

    public MorningCourtSession(Agent agent, CourtStore store) {
        this.agent = agent;
        this.store = store;
    }

    /**
     * 开始一场早朝。
     * 由皇帝主动开场，不等待用户先说话。
     */
    public CourtReply start() throws Exception {

        if (started) {
            throw new IllegalStateException(
                    "这场早朝已经开始，不能重复开始"
            );
        }

        prepareContext();

        started = true;

        return nextReply();
    }

    /**
     * 接收大臣发言，继续当前朝会。
     */
    public CourtReply respond(String speech) throws Exception {

        if (!started) {
            throw new IllegalStateException(
                    "早朝尚未开始"
            );
        }

        if (ended) {
            throw new IllegalStateException(
                    "本场早朝已经结束"
            );
        }

        if (speech == null || speech.isBlank()) {
            throw new IllegalArgumentException(
                    "发言不能为空"
            );
        }

        // 追加发言，不重新初始化上下文
        messages.add(
                new Message("user", speech.trim())
        );

        return nextReply();
    }

    /**
     * 准备本场早朝的上下文。
     */
    private void prepareContext() throws Exception {

        String principles = Files.readString(
                Path.of("court-principles.txt")
        );

        String affairs = Files.readString(
                Path.of("court-affairs.txt")
        );

        List<Decision> history = store.load();

        int start = Math.max(0, history.size() - 10);

        List<Decision> recent = history.subList(
                start,
                history.size()
        );

        String context = """
                【当前时间】
                %s

                【当前政务】
                %s

                【最近的正式决策】
                %s

                【本次事件】
                大臣已经进入朝堂，早朝开始。
                请你主动主持并发表开场讲话。
                """
                .formatted(
                        now(),
                        affairs,
                        mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(recent)
                );

        messages.clear();

        messages.add(
                new Message(
                        "system",
                        principles + "\n\n" + courtInstructions()
                )
        );

        messages.add(
                new Message("user", context)
        );
    }

    /**
     * 朝会专用规则与返回格式。
     */
    private String courtInstructions() {
        return """
                【当前运行场景：早朝】

                你仍然是「上朝」系统的皇帝。
                当前与会用户是一名大臣，可以复命、上奏、
                进谏、提出异议和提供现实信息。

                一、主持方式

                1. 由你主动开场，根据当前政务和历史决策选择议题。
                2. 可以宣读已有决定，也可以先听取复命或询问情况。
                3. 根据需要追问、解释、讨论或正式裁决。
                4. 不机械执行固定流程，不要求每轮都作出新决定。
                5. 问了需要大臣回答的问题后，应等待其回答。
                6. 不编造大臣的回答、执行结果或朝臣调查报告。
                7. 目前没有其他朝臣实际参与，不要假装他们已经发言。
                8. 政务资料和历史记录用于了解情况；
                   大臣发言不自动成为治理原则或最高命令。

                二、处理进谏

                大臣可以反对你的意见。
                你应结合原则、事实和论证，决定采纳、拒绝、
                追问或重新评估，不能仅因大臣提出要求就服从。
                也不能为了保持权威而拒绝合理的新证据。

                三、发言与决策分开

                普通询问、解释、讨论，不产生正式决策。
                宣读已有决定，不要把它作为新决策重复保存。
                只有本轮确实形成新的正式决定，才填写 decision。

                decision.type 只能是：
                DECIDE：正式作出决定。
                INVESTIGATE：决定进一步调查。
                WAIT：正式决定某事项暂不采取行动。

                INVESTIGATE 目前只记录调查决定，
                不代表已经执行调查或召集了其他 Agent。

                默认公开发言简明展示旨意和必要说明。
                大臣询问原因时，再解释依据。
                reason 保存简要决策依据，不要求内部推理过程。

                四、退朝

                由你根据议事情况决定是否退朝。
                如果刚提出需要回答的问题，不应同时退朝。
                WAIT 表示某事项暂不行动，不等于整场朝会结束。
                可以在最后一轮作出正式决定并同时退朝。

                五、返回格式

                每轮只返回一个 JSON 对象。
                不要添加 Markdown 代码围栏或 JSON 以外的文字。

                三个字段必须始终存在：
                speech：非空字符串，你向大臣公开说的话。
                decision：没有新决策时为 null，有则为对象。
                ended：布尔值，true 表示退朝，false 表示继续。

                没有新决策时：

                {
                  "speech": "昨日安排的事项，进展如何？",
                  "decision": null,
                  "ended": false
                }

                有新决策时：

                {
                  "speech": "准，当前优先完成早朝交互。",
                  "decision": {
                    "type": "DECIDE",
                    "content": "当前优先完成早朝交互。",
                    "reason": "根据当前项目目标和本次讨论作出安排。"
                  },
                  "ended": false
                }

                上述内容只是格式示例，不是要求你照抄的决定。
                决策 ID 和时间由程序生成，不要自行生成。
                """;
    }

    /**
     * 执行一轮模型调用，处理正式决策。
     */
    private CourtReply nextReply() throws Exception {

        String rawReply = agent.run(messages);

        CourtReply reply = parseReply(rawReply);

        // 普通发言不写入正式决策文件
        if (reply.decision() != null) {
            store.add(reply.decision());
        }

        ended = reply.ended();

        return reply;
    }

    /**
     * 解析皇帝的结构化回应。
     */
    private CourtReply parseReply(String rawReply) throws Exception {

        if (rawReply == null || rawReply.isBlank()) {
            throw new IllegalStateException(
                    "皇帝没有返回朝会回应"
            );
        }

        String json = rawReply.trim();

        // 兼容模型偶尔返回的 Markdown 代码围栏
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
                    "朝会回应必须是 JSON 对象"
            );
        }

        String speech = requiredText(root, "speech");

        JsonNode endedNode = root.get("ended");

        if (endedNode == null || !endedNode.isBoolean()) {
            throw new IllegalStateException(
                    "ended 必须是布尔值"
            );
        }

        if (!root.has("decision")) {
            throw new IllegalStateException(
                    "缺少 decision 字段；没有新决策时应为 null"
            );
        }

        Decision decision = null;

        JsonNode decisionNode = root.get("decision");

        if (!decisionNode.isNull()) {

            if (!decisionNode.isObject()) {
                throw new IllegalStateException(
                        "decision 必须是对象或 null"
                );
            }

            String type = requiredText(decisionNode, "type");
            String content = requiredText(decisionNode, "content");
            String reason = requiredText(decisionNode, "reason");

            if (!List.of(
                    "DECIDE",
                    "INVESTIGATE",
                    "WAIT"
            ).contains(type)) {

                throw new IllegalStateException(
                        "无效的决策类型：" + type
                );
            }

            decision = new Decision(
                    UUID.randomUUID().toString(),
                    type,
                    content,
                    reason,
                    now()
            );
        }

        return new CourtReply(
                speech,
                decision,
                endedNode.asBoolean()
        );
    }

    /**
     * 读取必填的非空文本字段。
     */
    private String requiredText(JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null
                || !value.isTextual()
                || value.asText().isBlank()) {

            throw new IllegalStateException(
                    field + " 必须是非空字符串"
            );
        }

        return value.asText();
    }

    private String now() {
        return ZonedDateTime.now(
                ZoneId.of("Asia/Shanghai")
        ).toString();
    }
}