package org.example.traveljavserver.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 大模型（兼容 OpenAI 协议）调用工具类
 */
public class LLMutils {
    private static final Logger log = LoggerFactory.getLogger(LLMutils.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最近一次调用失败的中文原因；成功时为 null */
    private String lastError;

    public LLMutils(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.client = new OkHttpClient.Builder()
                // 非流式与 SSE 共用该 client：读取超时要足够长，避免慢响应/流式被默认 10s 切断
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Interceptor.Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("Authorization", "Bearer " + apiKey)
                                .header("Content-Type", "application/json")
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * 是否配置了可用的 API Key。
     * 未配置时调用方不应发起请求，直接走降级逻辑。
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 非流式对话。
     *
     * @return 模型回复内容；调用失败时返回 null，具体原因见 getLastError()
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, false);
    }

    /**
     * 非流式对话。
     *
     * @param jsonMode true 时携带 response_format=json_object，要求模型只输出 JSON
     *                 （DeepSeek、千问等兼容 OpenAI 协议的模型支持；不支持的模型会走异常/解析失败分支）
     * @return 模型回复内容；调用失败时返回 null，具体原因见 getLastError()
     */
    public String chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        lastError = null;
        String jsonBody = buildRequestBody(systemPrompt, userPrompt, false, jsonMode);
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .post(RequestBody.create(JSON_MEDIA_TYPE, jsonBody))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                lastError = translateHttpError(response.code(), errorBody);
                log.warn("大模型调用失败：{}", lastError);
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                lastError = "大模型返回内容为空";
                return null;
            }
            String content = extractContent(body.string());
            if (content.isEmpty()) {
                lastError = "大模型返回的数据中没有有效内容";
                return null;
            }
            return content;
        } catch (Exception e) {
            lastError = "调用大模型服务失败，请稍后重试（" + e.getMessage() + "）";
            log.warn("大模型调用异常：", e);
            return null;
        }
    }

    /** 单参数对话（不指定系统提示词） */
    public String chat(String userPrompt) {
        return chat(null, userPrompt);
    }

    /**
     * 流式对话，每解析出一段内容就回调 consumer。
     *
     * @return 完整拼接内容；失败时返回 null，原因见 getLastError()
     */
    public String chatStream(String systemPrompt, String userPrompt, Consumer<String> consumer) {
        lastError = null;
        String jsonBody = buildRequestBody(systemPrompt, userPrompt, true);
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("accept", "text/event-stream")
                .post(RequestBody.create(JSON_MEDIA_TYPE, jsonBody))
                .build();

        StringBuilder fullContent = new StringBuilder();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                lastError = translateHttpError(response.code(), errorBody);
                log.warn("大模型流式调用失败：{}", lastError);
                return null;
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                lastError = "大模型返回内容为空";
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String content = parseStreamContent(data);
                    if (content != null && !content.isEmpty()) {
                        fullContent.append(content);
                        consumer.accept(content);
                    }
                }
            }
            return fullContent.toString();
        } catch (Exception e) {
            lastError = "调用大模型服务失败，请稍后重试（" + e.getMessage() + "）";
            log.warn("大模型流式调用异常：", e);
            return null;
        }
    }

    /** 从单个 SSE data 片段中解析增量内容 */
    private String parseStreamContent(String data) {
        try {
            JsonNode chunk = objectMapper.readTree(data);
            JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() ? null : delta.asText("");
        } catch (Exception e) {
            // 个别非 JSON 心跳片段直接忽略
            return null;
        }
    }

    /** 非流式响应：从 choices[0].message.content 提取内容 */
    private String extractContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText("");
        }
        return "";
    }

    /** 把大模型的 HTTP 错误码/响应体翻译成用户能看懂的中文提示 */
    private String translateHttpError(int code, String errorBody) {
        switch (code) {
            case 401:
                return "大模型 API Key 无效或已过期，请检查配置";
            case 402:
            case 429:
                // DeepSeek 余额不足返回 402；429 多为额度/限流
                if (errorBody.contains("Insufficient Balance") || errorBody.contains("balance")) {
                    return "大模型账户余额不足，请充值后再试";
                }
                return "大模型调用额度已用完或请求过于频繁，请稍后再试";
            case 404:
                return "大模型接口地址或模型名称不正确，请检查配置";
            default:
                return "大模型服务异常（响应码 " + code + "）：" + errorBody;
        }
    }

    /** 构建 OpenAI 兼容的请求体 JSON */
    public String buildRequestBody(String systemPrompt, String userPrompt, Boolean stream) {
        return buildRequestBody(systemPrompt, userPrompt, stream, false);
    }

    /** 构建 OpenAI 兼容的请求体 JSON；jsonMode=true 时要求模型只输出 JSON 对象 */
    public String buildRequestBody(String systemPrompt, String userPrompt, Boolean stream, Boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        Map<String, String> usr = new LinkedHashMap<>();
        usr.put("role", "user");
        usr.put("content", userPrompt);
        messages.add(usr);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("stream", stream);
        if (Boolean.TRUE.equals(jsonMode)) {
            // OpenAI 兼容的 JSON 输出模式：DeepSeek、千问等支持，模型将只返回 JSON 对象
            body.put("response_format", Map.of("type", "json_object"));
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("构建大模型请求体失败", e);
        }
    }
}
