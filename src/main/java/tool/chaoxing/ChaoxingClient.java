package tool.chaoxing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChaoxingClient {
    private static final String COURSES = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata";
    private static final String COURSE_PAGE = "https://mooc1.chaoxing.com/visit/stucoursemiddle";
    private static final String WORK_LIST = "https://mooc1.chaoxing.com/mooc2/work/list";
    private static final String LOGIN = "https://passport2.chaoxing.com/fanyalogin";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/118.0.0.0 Safari/537.36";
    private static final byte[] AES_KEY = "u2oh6Vu^HWe4_AES".getBytes(StandardCharsets.UTF_8);
    private static final Pattern COURSE_LINK = Pattern.compile("stucoursemiddle\\?courseid=(\\d+)&clazzid=(\\d+)&cpi=(\\d+)");
    private static final Pattern COURSE_NAME = Pattern.compile("course-name[^>]*title=\"([^\"]+)\"");
    private static final Pattern PROGRESS = Pattern.compile("<span>(\\d+)/(\\d+)</span>\\s*</div>\\s*</div>");
    private static final Pattern HOMEWORK = Pattern.compile("<li onclick=\"goTask\\(this\\);\" data=\"[^\"]+\"[\\s\\S]*?<p class=\"overHidden2 fl\">([^<]+)</p>[\\s\\S]*?<p class=\"status fl\">([^<]*)</p>");

    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private boolean loggedIn;

    public record Course(String name, String courseId, String classId, String cpi) {}
    public record Homework(String title, String status) {}
    public record HomeworkList(Course course, String progress, List<Homework> items) {}

    public HomeworkList listHomework(String keyword) throws Exception {
        ensureLogin();
        try {
            return doListHomework(keyword);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("登录状态已失效")) {
                ensureLogin();
                return doListHomework(keyword);
            }
            throw e;
        }
    }

    private HomeworkList doListHomework(String keyword) throws Exception {
        Course course = findCourse(keyword);
        if (course == null) {
            throw new IllegalArgumentException("未找到包含「" + keyword + "」的课程");
        }
        String page = get(COURSE_PAGE, Map.of("courseid", course.courseId(), "clazzid", course.classId(),
                "cpi", course.cpi(), "ismooc2", "1", "v", "2"));
        String workEnc = hiddenValue(page, "workEnc");
        if (workEnc.isBlank()) {
            throw new IllegalStateException("课程页未生成作业签名 workEnc，可能未开放作业功能或登录已失效");
        }
        String html = get(WORK_LIST, Map.of(
                "courseId", course.courseId(), "classId", course.classId(), "cpi", course.cpi(),
                "ut", "s", "openc", hiddenValue(page, "openc"), "enc", workEnc,
                "stuenc", hiddenValue(page, "enc"), "t", Long.toString(System.currentTimeMillis()),
                "isdisplaytable", "2"));
        if (html.contains("没有此页面访问权限")) {
            throw new IllegalStateException("作业页动态签名校验失败");
        }
        Matcher progressMatch = PROGRESS.matcher(html);
        String progress = progressMatch.find() ? progressMatch.group(1) + "/" + progressMatch.group(2) : "";
        List<Homework> items = new ArrayList<>();
        Matcher itemMatch = HOMEWORK.matcher(html);
        while (itemMatch.find()) {
            items.add(new Homework(decodeHtml(itemMatch.group(1).trim()), decodeHtml(itemMatch.group(2).trim())));
        }
        return new HomeworkList(course, progress, items);
    }

    private Course findCourse(String keyword) throws Exception {
        String fid = cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("fid"))
                .map(cookie -> cookie.getValue())
                .filter(value -> value.matches("\\d+"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("登录成功但未取得学校 fid，请检查学习通登录会话"));
        String html = get(COURSES, Map.of("courseType", "1", "courseFid", fid,
                "courseTeacherid", "0", "courseOrderBy", "0"));
        List<Course> courses = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher link = COURSE_LINK.matcher(html);
        while (link.find()) {
            String key = link.group(1) + "/" + link.group(2) + "/" + link.group(3);
            if (!seen.add(key)) continue;
            Matcher name = COURSE_NAME.matcher(html.substring(link.end(), Math.min(link.end() + 1600, html.length())));
            courses.add(new Course(name.find() ? decodeHtml(name.group(1)) : "?",
                    link.group(1), link.group(2), link.group(3)));
        }
        for (Course course : courses) if (course.name().equals(keyword)) return course;
        for (Course course : courses) if (course.name().contains(keyword)) return course;
        return null;
    }

    private void ensureLogin() throws Exception {
        if (loggedIn) return;
        String phone = System.getenv("CHAOXING_PHONE");
        String password = System.getenv("CHAOXING_PASSWORD");
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("请先设置 CHAOXING_PHONE 和 CHAOXING_PASSWORD 环境变量");
        }
        String form = form(Map.of("fid", "-1", "uname", encrypt(phone), "password", encrypt(password),
                "refer", "https%3A%2F%2Fi.chaoxing.com", "t", "true", "forbidotherlogin", "0",
                "validate", "", "doubleFactorLogin", "0", "independentId", "0"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(LOGIN)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("学习通登录请求失败：HTTP " + response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        if (!body.path("status").asBoolean(false)) {
            throw new IllegalStateException("学习通登录失败：" + body.path("msg2").asText("可能需要验证码或检查账号密码"));
        }
        loggedIn = true;
    }

    private String get(String url, Map<String, String> params) throws Exception {
        String target = params.isEmpty() ? url : url + "?" + form(params);
        HttpRequest request = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            String location = response.headers().firstValue("Location").orElse("");
            if (location.contains("passport2.chaoxing.com/login") || location.contains("passport2.chaoxing.com/fanyalogin")) {
                loggedIn = false;
                throw new IllegalStateException("学习通登录状态已失效，请重试");
            }
            throw new IllegalStateException("学习通请求被重定向：HTTP " + status + " -> " + location);
        }
        if (status != 200) throw new IllegalStateException("学习通请求失败：HTTP " + status);
        if (response.uri().toString().contains("passport2.chaoxing.com/login") || response.body().contains("用户登录")) {
            loggedIn = false;
            throw new IllegalStateException("学习通登录状态已失效，请重试");
        }
        return response.body();
    }

    private static String hiddenValue(String html, String id) {
        Matcher input = Pattern.compile("<input\\b[^>]*\\bid=\"" + Pattern.quote(id) + "\"[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!input.find()) return "";
        Matcher value = Pattern.compile("\\bvalue=\"([^\"]*)\"").matcher(input.group());
        return value.find() ? decodeHtml(value.group(1)) : "";
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new IvParameterSpec(AES_KEY));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String decodeHtml(String value) {
        String decoded = value.replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        Matcher numeric = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(decoded);
        StringBuffer result = new StringBuffer();
        while (numeric.find()) {
            String number = numeric.group(1);
            int codePoint = number.startsWith("x") ? Integer.parseInt(number.substring(1), 16) : Integer.parseInt(number);
            numeric.appendReplacement(result, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        numeric.appendTail(result);
        return result.toString();
    }
}
