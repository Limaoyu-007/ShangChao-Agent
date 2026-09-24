package court;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import court.parallel.CourtEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一场朝会对应一个 JSON 文件，只保存协调线程已提交的公开事件。
 * 同场由一个协调线程写入；原子替换使后台只能读到完整快照。
 * 不生成摘要、不修改政务，也不把发言变成圣旨。
 */
public final class CourtRecordStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public CourtRecordStore() {
        this(Path.of("court-records"));
    }

    public CourtRecordStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public void save(UUID sessionId, List<CourtEvent> events) throws Exception {
        ObjectNode record = mapper.createObjectNode();
        record.put("sessionId", sessionId.toString());
        ArrayNode entries = record.putArray("events");
        for (CourtEvent event : events) {
            ObjectNode entry = entries.addObject();
            entry.put("sequence", event.sequence());
            entry.put("eventId", event.eventId());
            entry.put("type", event.type());
            entry.put("speakerId", event.speakerId());
            entry.put("content", event.content());
            entry.put("targetId", event.targetId());
            // 显式使用 ISO 时间字符串，避免为 Instant 增加 Jackson 模块。
            entry.put("createdAt", event.createdAt().toString());
        }

        Files.createDirectories(directory);
        Path file = directory.resolve(sessionId + ".json");
        Path temporary = Files.createTempFile(directory, "court-record-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(record));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 按最近更新时间选取场次，返回完整 JSON 记录供后台阅读，不恢复运行中的会话。 */
    public String loadRecent(int limit) throws Exception {
        if (limit < 1) throw new IllegalArgumentException("场次数量必须大于零");
        if (!Files.exists(directory)) return "[]";

        List<Path> files = new ArrayList<>();
        Map<Path, FileTime> updatedAt = new HashMap<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile).toList()) {
                files.add(path);
                updatedAt.put(path, Files.getLastModifiedTime(path));
            }
        }
        files.sort(Comparator.comparing((Path p) -> updatedAt.get(p)).reversed()
                .thenComparing(p -> p.getFileName().toString()));

        ArrayNode records = mapper.createArrayNode();
        for (Path path : files.subList(0, Math.min(limit, files.size()))) {
            JsonNode record = mapper.readTree(Files.readString(path));
            if (record == null || !record.isObject()
                    || !record.path("sessionId").isTextual()
                    || !record.path("events").isArray()) {
                throw new IllegalStateException("朝会记录格式无效：" + path);
            }
            records.add(record);
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(records);
    }
}
