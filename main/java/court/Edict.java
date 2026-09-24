package court;

/** 后台颁布的圣旨记录。朝会只读取和讨论。保留现有字符串字段和 JSON 格式，不承载朝会聊天历史。 */
public record Edict(
        String id,
        String type,
        String content,
        String reason,
        String createdAt
) {
}
