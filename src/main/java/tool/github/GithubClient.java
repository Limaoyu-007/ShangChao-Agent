
package tool.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GithubClient {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String readFile(
            String owner,
            String repo,
            String path
    ) throws Exception {

        String url = "https://api.github.com/repos/"
                + owner + "/"
                + repo + "/contents/"
                + path;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "lmy-Agent")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        // 检查 HTTP 请求是否成功
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "GitHub 请求失败，状态码："
                            + response.statusCode()
                            + "，响应：" + response.body()
            );
        }

        // 解析 GitHub 返回的 JSON
        JsonNode json = mapper.readTree(response.body());

        String content = json.path("content").asText();

        if (content.isBlank()) {
            throw new RuntimeException("GitHub 未返回文件内容");
        }

        // GitHub 返回的文件内容经过 Base64 编码
        byte[] decoded = Base64.getMimeDecoder().decode(content);

        // 转换为正常的源码文本
        return new String(decoded, StandardCharsets.UTF_8);
    }
}