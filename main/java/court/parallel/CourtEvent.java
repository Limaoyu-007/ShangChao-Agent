
package court.parallel;

import java.time.Instant;

/**
 * 朝会事件。
 *
 * 记录已经正式提交到会议中的公开事件。
 * 皇帝、大臣和用户的发言都使用这个结构。
 */
public record CourtEvent(

        // 本场会议中的事件序号
        long sequence,

        // 事件的唯一标识
        String eventId,

        // 事件类型
        String type,

        // 事件发起者
        String speakerId,

        // 事件内容
        String content,

        // 指定接收者，null 表示面向所有参与者
        String targetId,

        // 事件发生时间
        Instant createdAt

) {

    /**
     * 定义目前支持的会议事件类型。
     */
    public static final String COURT_STARTED = "COURT_STARTED";

    public static final String SPEECH = "SPEECH";

    public static final String EDICT_ANNOUNCED = "EDICT_ANNOUNCED";

    public static final String COURT_ENDED = "COURT_ENDED";

}
