package tool.chaoxing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tool.Tool;

import java.util.List;
import java.util.Map;

public class HomeworkTool implements Tool {
    private final ChaoxingClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public HomeworkTool(ChaoxingClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "list_homework";
    }

    @Override
    public String description() {
        return "查询学习通指定课程的作业列表，返回作业名称、提交状态和完成进度。course 使用课程名或关键词。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of("course", Map.of(
                "type", "string", "description", "课程名称或关键词")), "required", List.of("course"));
    }

    @Override
    public String execute(String argument) throws Exception {
        JsonNode input = mapper.readTree(argument);
        String keyword = input.path("course").asText().trim();
        if (keyword.isBlank()) throw new IllegalArgumentException("缺少必要参数：course");
        ChaoxingClient.HomeworkList list = client.listHomework(keyword);
        StringBuilder result = new StringBuilder("《").append(list.course().name()).append("》作业列表:");
        if (!list.progress().isBlank()) result.append("\n完成进度: ").append(list.progress());
        if (list.items().isEmpty()) return result.append("\n（本课程暂无作业）").toString();
        for (int i = 0; i < list.items().size(); i++) {
            ChaoxingClient.Homework item = list.items().get(i);
            result.append("\n").append(i + 1).append(". ").append(item.title())
                    .append(" | ").append(item.status().isBlank() ? "状态未知" : item.status());
        }
        return result.toString();
    }
}
