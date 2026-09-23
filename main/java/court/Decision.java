package court;

/** 正式决策记录。保留现有字符串字段和 JSON 格式，不承载朝会聊天历史。 */
public record Decision(
        String id,
        String type,
        String content,
        String reason,
        String createdAt
) {
}
