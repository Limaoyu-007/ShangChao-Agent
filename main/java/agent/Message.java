package agent;

import java.util.List;

public record Message(
        String role,
        String content,
        List<ToolCall> tool_calls,
        String tool_call_id
) {

    // 保留原来的写法
    public Message(String role, String content) {
        this(role, content, null, null);
    }
}