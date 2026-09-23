package court;

/**
 * 皇帝在早朝中的一次回应。
 */
public record CourtReply(
        String speech,
        Decision decision,
        boolean ended
) {
}