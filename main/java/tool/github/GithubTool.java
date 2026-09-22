
package tool.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tool.Tool;

import java.util.List;
import java.util.Map;

public class GithubTool implements Tool {

    private final GithubClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GithubTool(GithubClient client) {
        this.client = client;
    }

    // 1. 工具名称：供大模型识别和调用
    @Override
    public String name() {
        return "read_github_file";
    }

    // 2. 工具描述：告诉大模型什么时候使用
    @Override
    public String description() {
        return "读取 GitHub 仓库中的指定文件，返回文件的原始文本内容。"
                + "适用于读取 Java 源码、配置文件和 Markdown 文档。";
    }

    // 3. 参数定义：告诉大模型需要提供哪些参数
    @Override
    public Map<String, Object> parameters() {

        return Map.of(
                "type", "object",

                "properties", Map.of(
                        "owner", Map.of(
                                "type", "string",
                                "description", "GitHub 用户名或组织名"
                        ),

                        "repo", Map.of(
                                "type", "string",
                                "description", "GitHub 仓库名称"
                        ),

                        "path", Map.of(
                                "type", "string",
                                "description", "仓库中的文件路径，例如 main/java/agent/Main.java"
                        )
                ),

                "required", List.of("owner", "repo", "path")
        );
    }

    // 4. 工具执行：解析参数并调用 GithubClient
    @Override
    public String execute(String argument) throws Exception {

        // 把大模型返回的 JSON 字符串解析成对象
        JsonNode node = mapper.readTree(argument);

        String owner = node.path("owner").asText();
        String repo = node.path("repo").asText();
        String path = node.path("path").asText();

        // 检查必要参数
        if (owner.isBlank() || repo.isBlank() || path.isBlank()) {
            throw new IllegalArgumentException(
                    "缺少必要参数：owner、repo 或 path"
            );
        }

        // 调用 GithubClient 获取文件源码
        return client.readFile(owner, repo, path);
    }
}