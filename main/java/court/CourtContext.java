package court;

import agent.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** 两种皇帝入口共同的数据读取方式；场景规则由各自入口提供。 */
public final class CourtContext {
    private CourtContext() {}

    public static List<Message> load(CourtStore store, String instructions, String event) throws Exception {
        String principles = Files.readString(Path.of("court-principles.txt"));
        String affairs = Files.readString(Path.of("court-affairs.txt"));
        List<Decision> history = store.load();
        // 当前仅提供近期历史，不代表所有仍有效的旨意；后续治理状态应单独建模。
        List<Decision> recent = history.subList(Math.max(0, history.size() - 10), history.size());
        String context = """
                【当前时间】
                %s

                【当前政务】
                %s

                【最近的正式决策】
                %s

                【本次事件】
                %s
                """.formatted(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")), affairs,
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(recent), event);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(principles + "\n\n" + instructions));
        messages.add(Message.user(context));
        return messages;
    }
}
