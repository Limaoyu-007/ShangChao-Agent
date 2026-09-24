package court;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 圣旨的文件存储，沿用 court-state.json 的数组格式。 */
public class CourtStore {
    private final Path filePath;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public CourtStore() {
        this(Path.of("court-state.json"));
    }

    public CourtStore(Path filePath) {
        this.filePath = filePath.toAbsolutePath().normalize();
    }

    public List<Edict> load() throws Exception {
        if (!Files.exists(filePath)) return new ArrayList<>();
        List<Edict> decisions = mapper.readValue(Files.readString(filePath), new TypeReference<>() {});
        if (decisions == null || decisions.contains(null)) {
            throw new IllegalStateException("决策文件必须是有效数组，不能包含 null；不会自动重置旧数据");
        }
        return decisions;
    }

    public void add(Edict decision) throws Exception {
        Objects.requireNonNull(decision, "不能保存空决策");
        Files.createDirectories(filePath.getParent());
        Path lockPath = filePath.resolveSibling(filePath.getFileName() + ".lock");
        // 同一 JVM 串行追加；跨进程锁覆盖完整的读取—追加—写入过程。
        // 这只保护文件写入，不保证两个皇帝不会基于同一旧快照作出重复决定。
        synchronized (CourtStore.class) {
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.tryLock()) {
                if (lock == null) {
                    throw new IllegalStateException("其他进程正在保存决策，本次未保存，请稍后重试");
                }
                List<Edict> decisions = load();
                decisions.add(decision);
                saveSnapshot(decisions);
            }
        }
    }

    private void saveSnapshot(List<Edict> decisions) throws Exception {
        Path temporary = Files.createTempFile(filePath.getParent(), "court-state-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(decisions));
            // 同目录原子替换，防止直接覆盖时留下半份 JSON。
            // 文件系统不支持原子替换则报错，不悄悄降级为不安全的覆盖。
            Files.move(temporary, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
