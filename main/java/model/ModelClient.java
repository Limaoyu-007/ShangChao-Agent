package model;

import agent.Message;

import java.util.List;
import java.util.Map;

/** 只负责模型请求，不执行工具，也不修改调用方的消息历史。 */
public interface ModelClient {

    Message chat(List<Message> messages, List<Map<String, Object>> tools) throws Exception;

    /** 默认不提供工具能力。 */
    default Message chat(List<Message> messages) throws Exception {
        return chat(messages, List.of());
    }

    /** 皇帝使用纯文本路径，意外返回的工具调用也不能执行。 */
    default Message chatText(List<Message> messages) throws Exception {
        Message reply = chat(messages);
        if (reply == null || !"assistant".equals(reply.role())
                || (reply.tool_calls() != null && !reply.tool_calls().isEmpty())
                || reply.content() == null || reply.content().isBlank()) {
            throw new IllegalStateException("本次需要 assistant 文本回复，不能返回工具调用或空内容");
        }
        return reply;
    }

}
