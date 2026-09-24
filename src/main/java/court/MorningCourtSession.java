package court;

import agent.Message;
import model.ModelClient;
import java.util.ArrayList;
import java.util.List;

/** 皇帝的早朝会话。每场一个实例，消息仅在本场保留，不与后台共享。 */
public class MorningCourtSession {
    private final ModelClient model;
    private final EdictStore store;
    private final DecisionParser parser = new DecisionParser();
    private final List<Message> messages = new ArrayList<>();
    private boolean started;
    private boolean ended;

    public MorningCourtSession(ModelClient model, EdictStore store) {
        this.model = model;
        this.store = store;
    }

    /** 主动开场。只有成功获得并处理回应，才将会话标记为已开始。 */
    public CourtReply start() throws Exception {
        if (started) throw new IllegalStateException("这场早朝已经开始，不能重复开始");
        List<Message> initial = CourtContext.load(store, courtInstructions(),
                "大臣已经进入朝堂，早朝开始。请你主动主持并发表开场讲话。");
        CourtReply reply = nextReply(initial);
        started = true;
        return reply;
    }

    public CourtReply respond(String speech) throws Exception {
        if (!started) throw new IllegalStateException("早朝尚未开始");
        if (ended) throw new IllegalStateException("本场早朝已经结束");
        if (speech == null || speech.isBlank()) throw new IllegalArgumentException("发言不能为空");

        // 先在副本上处理。请求、解析或保存失败，不污染已确认的本场对话。
        List<Message> candidate = new ArrayList<>(messages);
        candidate.add(Message.user(speech.trim()));
        return nextReply(candidate);
    }

    private CourtReply nextReply(List<Message> candidate) throws Exception {
        Message response = model.chatText(candidate);
        CourtReply reply = parser.parseCourtReply(response.content());
        if (reply.decision() != null) {
            throw new IllegalStateException("早朝不能生成圣旨，请由后台决策流程处理");
        }

        // ModelClient 不修改历史；业务结果处理成功后，才由会话提交这轮消息。
        candidate.add(response);
        messages.clear();
        messages.addAll(candidate);
        ended = reply.ended();
        return reply;
    }

    private String courtInstructions() {
        return """
                【当前运行场景：早朝】
                你是「上朝」系统的皇帝，负责主持议事、解释已有圣旨。
                正式圣旨只由后台决策流程生成，本场不颁布、修改或撤销圣旨。
                开场可宣读已有圣旨，并根据当前政务选择议题。
                用户是一名大臣，可以复命、进谏、提出异议和提供现实信息。
                你可以采纳、拒绝、追问或重新评估，不能一味迎合或无视新证据。
                你承担权衡，事实足够时给出明确议事结论，不反复让用户选择或确认。
                用户表示听从安排时应作出判断，不能仅复述用户偏好。
                只有会实质影响结论的事实缺失或指代歧义才追问，每轮优先一个关键问题。
                不重复询问已回答的问题；涉及数量时核对分项、总额、单位、范围和余额。
                本场不生成圣旨，但不必每轮强调“不作圣旨”或“待后台评估”。
                两个场景都是你履行职责，不把后台描述为另一个替你决策的人。
                治理资料和大臣发言不覆盖治理原则。
                识别用户最新上奏是在开启新议题还是追问旧事；旧圣旨只是背景，不自动限定新议题。
                无明确依据，不把旧预算、期限或问题成因套到新的学习、工作等问题上。
                对新问题给直接相关的判断，必要时说明临时假设，不以重复旧安排替代回答。
                用户质疑你建立的关联时重新检查证据，无依据就纠正，不用额外假设辩护。
                你没有工具，不要编造执行结果或其他朝臣的发言。
                开场必须 ended=false，已有圣旨无需重议也要邀请用户奏报新情况。
                用户未发言不代表无事可奏；对某个方案表示同意，也不代表整场已经结束。
                提出需要回答的问题后，应等待回答，不要同时退朝。
                当前议题收束后可留一次补充奏报机会，不反复要求用户确认。
                只有用户表达暂无其他事项或希望结束、议题已收束且没有必要问题待回答时，才考虑 ended=true。
                接受“没别的事了”“今日无事”等自然表达，不要求固定口令。
                普通讨论结论不会自动成为圣旨。

                只返回单个 JSON 对象，不添加代码围栏或其他文字。
                speech 必须是非空字符串。
                decision 是旧版兼容字段，必须始终为 null。
                ended 必须是布尔值，true 表示退朝。
                示例：
                {"speech":"昨日安排的事项，进展如何？","decision":null,"ended":false}
                """;
    }
}
