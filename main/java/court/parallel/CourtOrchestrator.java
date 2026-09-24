
package court.parallel;


import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多 Agent 朝会编排器。
 *
 * 负责：
 * 1. 并行调度多个 Agent。
 * 2. 接收模型调用结果。
 * 3. 统一修改会议状态。
 * 4. 将新发言通知其他参与者。
 * 5. 校验退朝权限。
 *
 * 约定：
 * run() 所在的线程是唯一的会议协调线程。
 * Agent 工作线程只负责调用模型并返回结果。
 */
public class CourtOrchestrator {

    // 单场会议最多发起的模型请求次数
    private static final int MAX_MODEL_CALLS = 80;

    // 每次用户发言后，最多自动产生的 Agent 行动数
    private static final int MAX_AUTO_ACTIONS = 12;

    private final CourtSession session;

    // 同一份参与者清单用于提示模型和校验行动目标。
    private final List<String> participantIds;

    // 根据 ID 查找 Agent
    private final Map<String, CourtParticipant> participants =
            new HashMap<>();

    // 记录每个 Agent 的运行状态
    private final Map<String, AgentState> agentStates =
            new HashMap<>();

    // 所有异步结果和用户输入都进入这个队列
    private final BlockingQueue<RuntimeEvent> eventQueue =
            new LinkedBlockingQueue<>();

    // 每个模型调用使用独立的虚拟线程
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    // 防止重复启动
    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private int modelCalls = 0;

    private int autoActions = 0;

    private boolean actionLimitNotified = false;

    private boolean modelLimitNotified = false;


    /**
     * 保存单个 Agent 的调度状态。
     *
     * 这些字段只允许由会议协调线程修改。
     */
    private static class AgentState {

        // 是否正在执行模型请求
        boolean running = false;

        // 当前请求使用的会议版本
        long launchedVersion = -1;

        // 执行期间收到的最新待处理版本
        long requestedVersion = -1;
    }


    /**
     * 运行时事件。
     *
     * 注意：这里不是公开的 CourtEvent。
     *
     * CourtEvent 表示已经提交的会议事件。
     * RuntimeEvent 表示需要编排器处理的内部消息。
     */
    private record RuntimeEvent(

            String type,

            String participantId,

            String content,

            CourtAction action,

            long snapshotVersion,

            Exception error

    ) {

        static RuntimeEvent userSpeech(String content) {

            return new RuntimeEvent(
                    "USER_SPEECH",
                    "user",
                    content,
                    null,
                    -1,
                    null
            );
        }


        static RuntimeEvent completed(
                String participantId,
                CourtAction action,
                long version
        ) {

            return new RuntimeEvent(
                    "AGENT_COMPLETED",
                    participantId,
                    null,
                    action,
                    version,
                    null
            );
        }


        static RuntimeEvent failed(
                String participantId,
                long version,
                Exception error
        ) {

            return new RuntimeEvent(
                    "AGENT_FAILED",
                    participantId,
                    null,
                    null,
                    version,
                    error
            );
        }


        static RuntimeEvent stop() {

            return new RuntimeEvent(
                    "STOP",
                    "system",
                    null,
                    null,
                    -1,
                    null
            );
        }

    }


    /**
     * 创建会议编排器。
     */
    public CourtOrchestrator(
            CourtSession session,
            List<CourtParticipant> agents
    ) {

        if (session == null) {
            throw new IllegalArgumentException(
                    "CourtSession 不能为空"
            );
        }

        if (agents == null || agents.isEmpty()) {
            throw new IllegalArgumentException(
                    "朝会至少需要一个 Agent"
            );
        }

        this.session = session;

        for (CourtParticipant agent : agents) {

            if (agent == null
                    || agent.id() == null
                    || agent.id().isBlank()) {

                throw new IllegalArgumentException(
                        "Agent 及其 ID 不能为空"
                );
            }

            if (participants.containsKey(agent.id())) {
                throw new IllegalArgumentException(
                        "Agent ID 重复：" + agent.id()
                );
            }

            if ("user".equals(agent.id()) || "system".equals(agent.id())) {
                throw new IllegalArgumentException("Agent ID 不能使用保留身份：" + agent.id());
            }

            participants.put(agent.id(), agent);

            agentStates.put(
                    agent.id(),
                    new AgentState()
            );
        }

        if (!participants.containsKey("emperor")) {
            throw new IllegalArgumentException(
                    "朝会必须注册皇帝 Agent"
            );
        }
        List<String> ids = new ArrayList<>(participants.keySet());
        ids.add("user");
        ids.sort(String::compareTo);
        participantIds = List.copyOf(ids);
    }


    /**
     * 运行一场朝会。
     *
     * 该方法会持续处理事件，
     * 直到用户中止或者皇帝正式退朝。
     */
    public void run() throws Exception {

        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "会议编排器不能重复启动"
            );
        }

        try {

            // 1. 正式开始会议
            session.start();

            System.out.println(
                    "\n========== 多 Agent 早朝 =========="
            );

            System.out.println(
                    "皇帝与大臣开始独立思考。"
            );

            // 先完成宣旨，再触发模型。第一次快照同时包含开场和宣旨事件。
            CourtEvent announcement = session.announceLatestEdict();
            System.out.println(announcement.content());
            broadcast(announcement);

            // 3. 协调线程持续接收和处理运行事件
            while (!session.isEnded()) {

                // 整场额度耗尽后仅收尾在途请求，不再表现为仍可继续议事。
                if (modelCalls >= MAX_MODEL_CALLS) {
                    notifyModelLimit();
                    if (agentStates.values().stream().noneMatch(state -> state.running)) {
                        System.out.println("[系统] 在途请求已处理完，本场运行因调用额度耗尽而中止。");
                        return;
                    }
                }

                RuntimeEvent event = eventQueue.take();

                switch (event.type()) {

                    case "USER_SPEECH" ->
                            onUserSpeech(event.content());

                    case "AGENT_COMPLETED" ->
                            onCompleted(event);

                    case "AGENT_FAILED" ->
                            onFailed(event);

                    case "STOP" -> {

                        System.out.println(
                                "\n本场早朝已由用户中止。"
                        );

                        return;
                    }

                    default ->
                            System.err.println(
                                    "未知运行事件：" + event.type()
                            );
                }

            }

        } finally {

            // 退出时取消仍在执行的模型任务
            executor.shutdownNow();

            System.out.println(
                    "\n========== 朝会运行结束 =========="
            );
        }

    }



    /**
     * 接收用户发言。
     *
     * 可以由控制台输入线程调用。
     * 即使会议协调循环尚未正式启动，
     * 发言也可以先进入事件队列等待处理。
     */
    public void submitUserSpeech(String speech) {

        if (speech == null || speech.isBlank()) {
            return;
        }

        eventQueue.offer(
                RuntimeEvent.userSpeech(speech.trim())
        );
    }


    /**
     * 请求中止本场朝会。
     */
    public void requestStop() {

        eventQueue.offer(
                RuntimeEvent.stop()
        );
    }


    /**
     * 将一条会议事件通知相关 Agent。
     *
     * 注意：广播不代表强制发言。
     * Agent 可以选择 SPEAK 或 SILENT。
     */
    private void broadcast(CourtEvent event) {

        if (session.isEnded()) {
            return;
        }

        for (CourtParticipant participant
                : participants.values()) {

            // 不因 Agent 自己的发言直接唤醒自己
            if (participant.id().equals(event.speakerId())) {
                continue;
            }

            // 指定目标时仅通知该 Agent；targetId=user 时等待用户发言。
            if (event.targetId() != null
                    && !event.targetId().equals(participant.id())) {

                continue;
            }

            requestThinking(participant.id());
        }

    }


    /**
     * 请求指定 Agent 思考。
     *
     * 如果该 Agent 正在执行，
     * 则只记录最新待处理版本，
     * 不会同时启动第二次请求。
     */
    private void requestThinking(String participantId) {

        AgentState state = agentStates.get(participantId);

        if (state == null || session.isEnded()) {
            return;
        }

        long currentVersion = session.version();

        if (state.running) {

            state.requestedVersion = Math.max(
                    state.requestedVersion,
                    currentVersion
            );

            return;
        }

        // 同一个 Agent 不重复处理已经启动过的版本
        if (currentVersion <= state.launchedVersion) {
            return;
        }

        if (!canSchedule()) {
            return;
        }

        schedule(participantId);
    }


    /**
     * 判断是否还允许启动新的模型请求。
     */
    private boolean canSchedule() {

        if (modelCalls >= MAX_MODEL_CALLS) {
            notifyModelLimit();
            return false;
        }

        if (autoActions >= MAX_AUTO_ACTIONS) {

            if (!actionLimitNotified) {

                System.out.println(
                        "\n[系统] 本轮自动讨论已达到上限，"
                                + "等待用户发言。"
                );

                actionLimitNotified = true;
            }

            return false;
        }

        return true;
    }

    private void notifyModelLimit() {
        if (!modelLimitNotified) {
            System.out.println("\n[系统] 本场调用额度已耗尽，不再接收新发言；处理完在途请求后中止运行。");
            modelLimitNotified = true;
        }
    }


    /**
     * 真正启动一次 Agent 模型调用。
     *
     * 这里不会等待模型返回。
     */
    private void schedule(String participantId) {

        CourtParticipant participant =
                participants.get(participantId);

        AgentState state =
                agentStates.get(participantId);

        if (participant == null
                || state == null
                || state.running) {

            return;
        }

        // 创建本次模型请求使用的会议快照
        CourtSnapshot snapshot = session.snapshot(participantIds);

        state.running = true;

        state.launchedVersion = snapshot.version();

        state.requestedVersion = -1;

        modelCalls++;

        System.out.println(
                "[系统] " + participant.name()
                        + " 开始思考，会议版本 V"
                        + snapshot.version()
        );

        // 提交到虚拟线程，不阻塞会议协调线程
        executor.submit(() -> {

            try {

                CourtAction action =
                        participant.react(snapshot);

                eventQueue.offer(
                        RuntimeEvent.completed(
                                participantId,
                                action,
                                snapshot.version()
                        )
                );

            } catch (Exception e) {

                eventQueue.offer(
                        RuntimeEvent.failed(
                                participantId,
                                snapshot.version(),
                                e
                        )
                );

            }

        });

    }


    /**
     * 处理用户发言。
     */
    private void onUserSpeech(String speech) {

        if (modelCalls >= MAX_MODEL_CALLS) {
            notifyModelLimit();
            System.out.println("[系统] 额度已耗尽，这条发言未加入朝会记录。");
            return;
        }

        if (session.isEnded()) {
            return;
        }

        CourtEvent event = session.speak(
                "user",
                speech,
                null
        );

        // 用户发言后，开启新一轮自动讨论
        autoActions = 0;

        actionLimitNotified = false;

        System.out.println(
                "\n你：" + speech
        );

        broadcast(event);
    }


    /**
     * 处理 Agent 已完成的模型调用。
     */
    private void onCompleted(RuntimeEvent event)
            throws Exception {

        String participantId = event.participantId();

        AgentState state =
                agentStates.get(participantId);

        if (state == null || !state.running) {
            return;
        }

        // 防止旧任务结果误认为当前任务的结果
        if (state.launchedVersion
                != event.snapshotVersion()) {

            return;
        }

        state.running = false;

        CourtAction action = event.action();

        if (action == null) {

            System.err.println(
                    "[系统] " + participantId
                            + " 返回了空行动。"
            );

            schedulePending(participantId);

            return;
        }

        try {

            // 无效目标不能先公开再静默丢失路由，也不消耗行动次数。
            if (action.targetId() != null && !participantIds.contains(action.targetId())) {
                System.err.println("[系统] 拒绝 " + participantId + " 的行动：无效 targetId="
                        + action.targetId() + "，可用目标为 " + participantIds + " 或 null。");
                return;
            }

            // 如果模型使用旧快照判断退朝，
            // 不直接执行，而是重新请求最新会议状态
            if (isSensitiveAction(action)
                    && event.snapshotVersion()
                    != session.version()) {

                System.out.println(
                        "[系统] " + participantId
                                + " 的退朝判断基于旧会议版本，"
                                + "需要重新评估。"
                );

                requestThinking(participantId);

                return;
            }

            // 根据行动类型处理结果
            commitAction(participantId, action);

        } finally {

            // 处理执行期间收到的新事件
            schedulePending(participantId);
        }

    }


    /**
     * 退朝必须基于最新会议状态判断。
     */
    private boolean isSensitiveAction(CourtAction action) {

        return CourtAction.END_COURT.equals(action.type());
    }


    /**
     * 处理 Agent 请求失败。
     */
    private void onFailed(RuntimeEvent event) {

        AgentState state =
                agentStates.get(event.participantId());

        if (state == null) {
            return;
        }

        if (state.launchedVersion
                != event.snapshotVersion()) {

            return;
        }

        state.running = false;

        System.err.println(
                "[系统] Agent 调用失败："
                        + event.participantId()
                        + "，原因："
                        + event.error().getMessage()
        );

        // 如果执行期间出现了新事件，
        // 后续仍可以使用新的会议版本再次思考。
        schedulePending(event.participantId());
    }


    /**
     * 检查执行期间是否收到了需要处理的新版本。
     */
    private void schedulePending(String participantId) {

        if (session.isEnded()) {
            return;
        }

        AgentState state =
                agentStates.get(participantId);

        if (state == null || state.running) {
            return;
        }

        if (state.requestedVersion
                > state.launchedVersion) {

            requestThinking(participantId);
        }

    }


    /**
     * 将 Agent 的行动提案正式提交到会议中。
     */
    private void commitAction(
            String participantId,
            CourtAction action
    ) throws Exception {

        CourtParticipant participant =
                participants.get(participantId);

        if (participant == null || session.isEnded()) {
            return;
        }

        // 程序级权限校验
        if (!"emperor".equals(participantId)
                && isSensitiveAction(action)) {

            System.err.println(
                    "[系统] 拒绝 " + participant.name()
                            + " 的越权行动："
                            + action.type()
            );

            return;
        }

        if (CourtAction.SILENT.equals(action.type())) {
            return;
        }

        // 防止已经返回的任务突破本轮行动上限
        if (autoActions >= MAX_AUTO_ACTIONS) {

            canSchedule();

            return;
        }

        autoActions++;

        switch (action.type()) {

            case CourtAction.SPEAK ->
                    commitSpeech(participant, action);

            case CourtAction.END_COURT ->
                    commitEndCourt(participant, action);

            default ->
                    throw new IllegalArgumentException(
                            "不支持的行动：" + action.type()
                    );

        }

    }


    /**
     * 提交普通发言。
     */
    private void commitSpeech(
            CourtParticipant participant,
            CourtAction action
    ) {

        CourtEvent event = session.speak(
                participant.id(),
                action.speech(),
                action.targetId()
        );

        System.out.println(
                "\n" + participant.name()
                        + "：" + action.speech()
        );

        broadcast(event);
    }


    /**
     * 提交皇帝的退朝行动。
     */
    private void commitEndCourt(
            CourtParticipant participant,
            CourtAction action
    ) {

        session.endCourt(
                participant.id(),
                action.speech()
        );

        System.out.println(
                "\n" + participant.name()
                        + "：" + action.speech()
        );

        System.out.println(
                "\n========== 退朝 =========="
        );
    }

}
