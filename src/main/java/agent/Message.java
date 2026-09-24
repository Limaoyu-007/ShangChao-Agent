package agent;

import java.util.List;

/** 模型协议消息。role 是 API 角色，不是皇帝、朝臣等业务身份。 */
public record Message(
        String role,
        String content,
        List<ToolCall> tool_calls,
        String tool_call_id
) {

    // 保留 snake_case 字段，兼容模型 API 和已有 session.json。
    public Message {
        if (tool_calls != null) {
            // record 只保证引用不变，复制列表以免消息被外部继续修改。
            tool_calls = List.copyOf(tool_calls);
            if (tool_calls.isEmpty()) tool_calls = null;
        }
    }

    public Message(String role, String content) {
        this(role, content, null, null);
    }

    public static Message system(String content) { return new Message("system", content); }
    public static Message user(String content) { return new Message("user", content); }
    public static Message assistant(String content) { return new Message("assistant", content); }
    public static Message toolResult(String callId, String content) {
        return new Message("tool", content, null, callId);
    }
}
