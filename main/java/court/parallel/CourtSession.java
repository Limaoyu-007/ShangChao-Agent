
package court.parallel;

import court.EdictStore;
import court.Edict;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一场朝会的共享状态。
 *
 * 负责：
 * 1. 保存公开会议事件。
 * 2. 管理会议版本号。
 * 3. 生成不可变会议快照。
 * 4. 读取并宣读已有圣旨。
 * 5. 管理会议开始和结束状态。
 *
 * 本类不负责调用模型，也不负责调度线程。
 *
 * 约定：所有修改操作只能由会议协调线程调用。
 * Agent 工作线程只能读取已经生成的 CourtSnapshot。
 */
public class CourtSession {

    // 每场独立编号，避免不同朝会覆盖同一个文件。
    private final UUID sessionId = UUID.randomUUID();

    public UUID sessionId() {
        return sessionId;
    }

    /** 协调线程取得只读副本交给存储，不暴露内部可变列表。 */
    public List<CourtEvent> events() {
        return List.copyOf(events);
    }

    // 本场朝会的公开事件
    private final List<CourtEvent> events = new ArrayList<>();

    // 圣旨记录
    private final List<Edict> edicts;

    // 用户近况、待处理问题和待核实信息；不是已执行结果
    private final String currentAffairs;

    // 当前会议版本
    private long version = 0;

    // 会议是否已经开始
    private boolean started = false;

    // 会议是否已经结束
    private boolean ended = false;


    /**
     * 创建一场新的朝会。
     *
     * 每场朝会拥有独立的事件列表，
     * 但可以共享同一个圣旨存储。
     */
    public CourtSession(EdictStore store) throws Exception {

        Objects.requireNonNull(
                store,
                "EdictStore 不能为空"
        );

        // 读取当前政务
        this.currentAffairs = Files.readString(
                Path.of("court-affairs.txt")
        );

        // 读取已经保存的圣旨
        this.edicts = List.copyOf(
                store.load()
        );
    }


    /**
     * 正式开始本场朝会。
     */
    public CourtEvent start() {

        if (started) {
            throw new IllegalStateException(
                    "本场早朝已经开始"
            );
        }

        started = true;

        return appendEvent(
                CourtEvent.COURT_STARTED,
                "system",
                "早朝开始。",
                null
        );
    }


    /**
     * 提交一条公开发言。
     *
     * speakerId：发言者 ID
     * content：发言内容
     * targetId：主要回应对象，可以为 null
     */
    public CourtEvent speak(
            String speakerId,
            String content,
            String targetId
    ) {

        ensureActive();

        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException(
                    "发言者 ID 不能为空"
            );
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "发言内容不能为空"
            );
        }

        return appendEvent(
                CourtEvent.SPEECH,
                speakerId,
                content,
                targetId
        );
    }


    /**
     * 开场宣读最近一份圣旨，只追加会议事件，不重复写入圣旨存储。
     * 暂不维护跨场次的宣读状态，因此下场朝会可以再次宣读同一份。
     */
    public CourtEvent announceLatestEdict() {
        ensureActive();
        if (edicts.isEmpty()) {
            return appendEvent(CourtEvent.SPEECH, "system",
                    "当前没有可宣读的圣旨，可以直接议事。", null);
        }

        Edict edict = edicts.get(edicts.size() - 1);
        String content = """
                【宣旨】
                圣旨编号：%s
                类型：%s
                内容：%s
                依据：%s
                """.formatted(edict.id(), edict.type(), edict.content(), edict.reason());

        return appendEvent(CourtEvent.EDICT_ANNOUNCED, "system", content, null);
    }


    /**
     * 皇帝正式宣布退朝。
     */
    public CourtEvent endCourt(
            String speakerId,
            String content
    ) {

        ensureActive();

        if (!"emperor".equals(speakerId)) {
            throw new IllegalArgumentException(
                    "只有皇帝可以宣布退朝"
            );
        }

        CourtEvent event = appendEvent(
                CourtEvent.COURT_ENDED,
                speakerId,
                content == null || content.isBlank()
                        ? "今日退朝。"
                        : content,
                null
        );

        ended = true;

        return event;
    }


    /**
     * 创建当前会议的不可变快照。
     *
     * Agent 使用快照进行思考，
     * 不直接访问本场会议的可变状态。
     */
    public CourtSnapshot snapshot(List<String> participantIds) {

        if (!started) {
            throw new IllegalStateException(
                    "早朝尚未开始"
            );
        }

        return new CourtSnapshot(
                version,
                events,
                edicts,
                currentAffairs,
                participantIds
        );
    }


    /**
     * 内部方法：提交一条会议事件。
     *
     * 每提交一条事件，会议版本增加 1。
     */
    private CourtEvent appendEvent(
            String type,
            String speakerId,
            String content,
            String targetId
    ) {

        long sequence = ++version;

        CourtEvent event = new CourtEvent(
                sequence,
                UUID.randomUUID().toString(),
                type,
                speakerId,
                content,
                targetId,
                Instant.now()
        );

        events.add(event);

        return event;
    }


    /**
     * 检查会议是否处于可操作状态。
     */
    private void ensureActive() {

        if (!started) {
            throw new IllegalStateException(
                    "早朝尚未开始"
            );
        }

        if (ended) {
            throw new IllegalStateException(
                    "本场早朝已经结束"
            );
        }
    }


    /**
     * 查询会议是否已经结束。
     */
    public boolean isEnded() {
        return ended;
    }


    /**
     * 获取当前会议版本。
     */
    public long version() {
        return version;
    }

}
