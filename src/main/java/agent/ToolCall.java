package agent;

public record ToolCall(
        String id,
        String type,
        FunctionCall function
) {
}
