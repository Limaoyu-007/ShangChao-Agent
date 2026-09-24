package court;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** 模型输出进入正式治理数据前的统一校验，不调用模型、不负责保存。 */
public final class DecisionParser {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public Edict parseDecision(String reply) throws Exception {
        return decision(readObject(reply));
    }

    public CourtReply parseCourtReply(String reply) throws Exception {
        JsonNode root = readObject(reply);
        String speech = text(root, "speech");
        JsonNode ended = root.get("ended");
        if (ended == null || !ended.isBoolean()) {
            throw new IllegalStateException("ended 必须是布尔值");
        }
        JsonNode node = root.get("decision");
        if (node == null) {
            throw new IllegalStateException("缺少 decision 字段；早朝必须返回 null");
        }
        if (!node.isNull()) {
            throw new IllegalStateException("早朝不能生成圣旨，decision 必须为 null");
        }
        return new CourtReply(speech, null, ended.asBoolean());
    }

    private Edict decision(JsonNode node) {
        if (!node.isObject()) throw new IllegalStateException("决策必须是 JSON 对象");
        String type = text(node, "type");
        if (!List.of("DECIDE", "INVESTIGATE", "WAIT").contains(type)) {
            throw new IllegalStateException("无效的决策类型：" + type);
        }
        // ID 和时间属于正式记录，由 Java 生成，不接受模型替程序编号。
        return new Edict(UUID.randomUUID().toString(), type,
                text(node, "content"), text(node, "reason"),
                ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
    }

    private JsonNode readObject(String reply) throws Exception {
        if (reply == null || reply.isBlank()) throw new IllegalStateException("皇帝没有返回内容");
        String json = reply.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("皇帝回应必须是单个 JSON 对象");
        }
        return root;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException(field + " 必须是非空字符串");
        }
        return value.asText();
    }
}
