package court.parallel;

import java.util.List;

/** 朝会行动提案。只允许议事和退朝，正式圣旨由后台生成。 */
public record CourtAction(String type, String speech, String targetId) {
    public static final String SPEAK = "SPEAK";
    public static final String SILENT = "SILENT";
    public static final String END_COURT = "END_COURT";

    public CourtAction {
        if (type == null || !List.of(SPEAK, SILENT, END_COURT).contains(type)) {
            throw new IllegalArgumentException("无效的朝会行动类型：" + type);
        }
        if (!SILENT.equals(type) && (speech == null || speech.isBlank())) {
            throw new IllegalArgumentException("非沉默行动必须包含有效发言");
        }
    }
}
