package court;

/**
 * 皇帝在早朝中的一次回应。
 */
public record CourtReply(
        String speech,
        // 旧版协议兼容字段；早朝解析器强制要求为 null。
        Edict decision,
        boolean ended
) {
}